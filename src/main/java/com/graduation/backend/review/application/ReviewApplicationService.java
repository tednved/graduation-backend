package com.graduation.backend.review.application;

import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import com.graduation.backend.common.web.dto.UserSummaryResponse;
import com.graduation.backend.notification.application.NotificationMessage;
import com.graduation.backend.notification.domain.NotificationType;
import com.graduation.backend.order.domain.Order;
import com.graduation.backend.order.domain.OrderRepository;
import com.graduation.backend.review.api.dto.CreateReviewRequest;
import com.graduation.backend.review.api.dto.ReviewResponse;
import com.graduation.backend.review.domain.Review;
import com.graduation.backend.review.domain.ReviewRepository;
import com.graduation.backend.review.domain.ReviewStatus;
import com.graduation.backend.user.application.UserService;
import com.graduation.backend.user.domain.User;
import com.graduation.backend.user.domain.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * 评价写路径。
 *
 * <p>被评价人按订单对端推导，绝不取客户端传参——否则任何人都能给任意用户刷分。
 *
 * <p>并发控制靠「先锁订单行」：同一订单的双方评价必须串行，否则两个人同时评价会各自读到
 * 「还没人评价」的聚合值，后提交的一方把平均分覆盖成只算了一条的旧值。
 * 订单行是这两笔评价唯一共享的锁对象，不存在加锁顺序不同导致的死锁。
 *
 * <p>聚合不在应用层累加，而是让数据库按 {@code VISIBLE} 评价重算，天然幂等、可重放。
 */
@Service
public class ReviewApplicationService {

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ReviewApplicationService(ReviewRepository reviewRepository,
                                    OrderRepository orderRepository,
                                    UserRepository userRepository,
                                    UserService userService,
                                    ApplicationEventPublisher eventPublisher,
                                    Clock clock) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** 创建评价：仅已完成订单的双方，同一订单每人只能评价一次。 */
    @Transactional
    public ReviewResponse create(Long userId, CreateReviewRequest request) {
        User reviewer = userService.requireActiveUser(userId);
        Order order = orderRepository.findByIdForUpdate(request.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在"));
        if (!order.isParticipant(reviewer.getId())) {
            throw new BusinessException(ErrorCode.ORDER_OPERATION_FORBIDDEN, "不是该订单的参与方");
        }
        if (!order.isCompleted()) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED, "订单完成后才能评价");
        }
        requireVersion(order, request.version());
        if (reviewRepository.findByOrderIdAndReviewerId(order.getId(), reviewer.getId()).isPresent()) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS, "你已经评价过该订单");
        }

        Long revieweeId = order.isBuyer(reviewer.getId()) ? order.getSellerId() : order.getBuyerId();
        Instant now = clock.instant();
        Review review = reviewRepository.save(Review.of(
                order.getId(), reviewer.getId(), revieweeId, request.rating(),
                normalizeContent(request.content()), now));

        User reviewee = requireUser(revieweeId);
        reaggregate(reviewee, now);

        eventPublisher.publishEvent(new NotificationMessage(
                revieweeId, NotificationType.REVIEW_RECEIVED, "收到新的评价",
                reviewer.getNickname() + " 给了你 " + request.rating() + " 星评价",
                NotificationMessage.BIZ_TYPE_ORDER, order.getId()));

        return ReviewResponse.of(review, UserSummaryResponse.from(reviewer), UserSummaryResponse.from(reviewee));
    }

    /**
     * 重算被评价人的信用汇总并写回。
     *
     * <p>没有可见评价时 {@code avg} 返回 null，由 {@code applyRating} 落成 0.00，
     * 与列的非空约束一致。
     */
    private void reaggregate(User reviewee, Instant now) {
        long count = reviewRepository.countByRevieweeIdAndStatus(reviewee.getId(), ReviewStatus.VISIBLE);
        Double average = reviewRepository.averageRating(reviewee.getId(), ReviewStatus.VISIBLE);
        reviewee.applyRating(average == null ? null : BigDecimal.valueOf(average), count, now);
    }

    /** 空白内容按「未填写」处理，避免存进一串空格。 */
    private String normalizeContent(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 可选版本校验：客户端看到的是旧订单时直接冲突，而不是把评价写到已经变化的订单上。 */
    private void requireVersion(Order order, Integer requested) {
        if (requested == null) {
            return;
        }
        long current = order.getVersion() == null ? 0L : order.getVersion();
        if (requested.longValue() != current) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED, "订单已更新，请刷新后重试");
        }
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
    }
}
