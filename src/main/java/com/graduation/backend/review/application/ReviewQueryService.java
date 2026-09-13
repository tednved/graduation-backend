package com.graduation.backend.review.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.common.web.dto.UserSummaryResponse;
import com.graduation.backend.order.domain.Order;
import com.graduation.backend.order.domain.OrderRepository;
import com.graduation.backend.review.api.dto.ReviewEligibilityResponse;
import com.graduation.backend.review.api.dto.ReviewResponse;
import com.graduation.backend.review.api.dto.UserCreditResponse;
import com.graduation.backend.review.domain.Review;
import com.graduation.backend.review.domain.ReviewRepository;
import com.graduation.backend.review.query.RatingCountRow;
import com.graduation.backend.review.query.ReviewQueryMapper;
import com.graduation.backend.review.query.ReviewRow;
import com.graduation.backend.user.application.UserService;
import com.graduation.backend.user.domain.User;
import com.graduation.backend.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 评价读取：用户收到的评价、信用摘要、订单的双方评价状态。
 *
 * <p>评价列表与信用摘要是公开接口：被禁用账号的历史评价仍要可查，
 * 因此这里只校验「用户存在」而不校验「账号 ACTIVE」。
 */
@Service
public class ReviewQueryService {

    private static final int RATING_MIN = 1;
    private static final int RATING_MAX = 5;

    private final ReviewRepository reviewRepository;
    private final ReviewQueryMapper reviewQueryMapper;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public ReviewQueryService(ReviewRepository reviewRepository,
                              ReviewQueryMapper reviewQueryMapper,
                              OrderRepository orderRepository,
                              UserRepository userRepository,
                              UserService userService) {
        this.reviewRepository = reviewRepository;
        this.reviewQueryMapper = reviewQueryMapper;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    /** 用户收到的评价，按创建时间倒序，可按分数筛选。 */
    @Transactional(readOnly = true)
    public PageResult<ReviewResponse> userReviews(Long userId, Integer rating, int page, int size) {
        User reviewee = requireUser(userId);
        UserSummaryResponse revieweeSummary = UserSummaryResponse.from(reviewee);
        IPage<ReviewRow> result = reviewQueryMapper.selectUserReviews(
                new Page<>(page + 1L, size), reviewee.getId(), rating);
        List<ReviewResponse> items = result.getRecords().stream()
                .map(row -> toResponse(row, revieweeSummary))
                .toList();
        return PageResult.of(page, size, result.getTotal(), items);
    }

    /**
     * 信用摘要：平均分与总数取用户表的聚合列（由评价写入时重算），分布实时统计。
     *
     * <p>分布补齐 1~5 每一档，没有评价的分数为 0，客户端不用判空键。
     */
    @Transactional(readOnly = true)
    public UserCreditResponse credit(Long userId) {
        User user = requireUser(userId);
        Map<Integer, Integer> distribution = new LinkedHashMap<>();
        for (int rating = RATING_MIN; rating <= RATING_MAX; rating++) {
            distribution.put(rating, 0);
        }
        for (RatingCountRow row : reviewQueryMapper.countByRating(user.getId())) {
            distribution.put(row.getRating(), row.getTotal());
        }
        return new UserCreditResponse(
                user.getId(), user.getAverageRating(), user.getReviewCount(), distribution);
    }

    /** 订单的双方评价状态：仅订单双方可见。 */
    @Transactional(readOnly = true)
    public ReviewEligibilityResponse eligibility(Long viewerId, Long orderId) {
        User viewer = userService.requireActiveUser(viewerId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在"));
        if (!order.isParticipant(viewer.getId())) {
            throw new BusinessException(ErrorCode.ORDER_OPERATION_FORBIDDEN, "不是该订单的参与方");
        }
        Long counterpartId = order.isBuyer(viewer.getId()) ? order.getSellerId() : order.getBuyerId();
        Optional<Review> mine = reviewRepository.findByOrderIdAndReviewerId(orderId, viewer.getId());
        Optional<Review> counterpart = reviewRepository.findByOrderIdAndReviewerId(orderId, counterpartId);
        return new ReviewEligibilityResponse(
                orderId,
                order.isCompleted() && mine.isEmpty(),
                mine.map(Review::getId).orElse(null),
                counterpart.isPresent(),
                counterpart.map(Review::getId).orElse(null));
    }

    private ReviewResponse toResponse(ReviewRow row, UserSummaryResponse reviewee) {
        return ReviewResponse.of(
                row.getReviewId(),
                row.getOrderId(),
                new UserSummaryResponse(row.getReviewerId(), row.getReviewerNickname(),
                        row.getReviewerAvatarUrl()),
                reviewee,
                row.getRating(),
                row.getContent(),
                row.getStatus(),
                row.getCreatedAt());
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
    }
}
