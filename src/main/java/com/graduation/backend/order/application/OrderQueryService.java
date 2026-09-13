package com.graduation.backend.order.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.common.web.dto.UserSummaryResponse;
import com.graduation.backend.order.api.dto.OrderDetailResponse;
import com.graduation.backend.order.api.dto.OrderEventResponse;
import com.graduation.backend.order.api.dto.OrderItemSnapshotResponse;
import com.graduation.backend.order.api.dto.OrderSummaryResponse;
import com.graduation.backend.order.domain.Order;
import com.graduation.backend.order.domain.OrderEvent;
import com.graduation.backend.order.domain.OrderEventRepository;
import com.graduation.backend.order.domain.OrderSide;
import com.graduation.backend.order.domain.OrderSnapshot;
import com.graduation.backend.order.domain.OrderSnapshotRepository;
import com.graduation.backend.order.domain.OrderStatus;
import com.graduation.backend.order.query.OrderQueryMapper;
import com.graduation.backend.order.query.OrderSummaryRow;
import com.graduation.backend.order.domain.OrderRepository;
import com.graduation.backend.user.application.UserService;
import com.graduation.backend.user.domain.User;
import com.graduation.backend.user.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单读取：详情组装与买卖列表。
 *
 * <p>列表走 MyBatis-Plus 投影 + 物理分页（{@code PaginationInnerInterceptor} 追加 LIMIT），
 * 详情走 JPA：可见性判定与 {@code allowedActions} 计算都需要订单领域对象。
 *
 * <p>{@link #assemble} 同时服务于写路径回显：刚写入的订单已在持久化上下文里，不必再查一次。
 */
@Service
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderSnapshotRepository orderSnapshotRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderQueryMapper orderQueryMapper;
    private final UserRepository userRepository;
    private final UserService userService;

    public OrderQueryService(OrderRepository orderRepository,
                             OrderSnapshotRepository orderSnapshotRepository,
                             OrderEventRepository orderEventRepository,
                             OrderQueryMapper orderQueryMapper,
                             UserRepository userRepository,
                             UserService userService) {
        this.orderRepository = orderRepository;
        this.orderSnapshotRepository = orderSnapshotRepository;
        this.orderEventRepository = orderEventRepository;
        this.orderQueryMapper = orderQueryMapper;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    /** 我的订单：{@code BUY} 取买家视角、{@code SELL} 取卖家视角，下单时间倒序。 */
    @Transactional(readOnly = true)
    public PageResult<OrderSummaryResponse> myOrders(Long userId, OrderSide side, OrderStatus status,
                                                     int page, int size) {
        // 事务内重新读库：被禁用账号带旧 Token 访问时必须按 403 USER_DISABLED 处理。
        userService.requireActiveUser(userId);
        IPage<OrderSummaryRow> result = orderQueryMapper.selectMyOrders(
                new Page<>(page + 1L, size), userId, side.name(), status == null ? null : status.name());
        return PageResult.of(page, size, result.getTotal(),
                result.getRecords().stream().map(OrderQueryService::toSummary).toList());
    }

    /**
     * 订单详情。
     *
     * <p>仅买家、卖家或管理员可见。契约把「不可见」与「无权」统一成
     * {@code ORDER_OPERATION_FORBIDDEN}（403），因此非参与方拿到的是 403 而不是 404——
     * 这里不对无关用户伪装成「不存在」。
     */
    @Transactional(readOnly = true)
    public OrderDetailResponse detail(Long viewerId, Long orderId) {
        User viewer = userService.requireActiveUser(viewerId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在"));
        if (!order.isParticipant(viewer.getId()) && !viewer.isAdmin()) {
            throw new BusinessException(ErrorCode.ORDER_OPERATION_FORBIDDEN, "无权查看该订单");
        }
        return assemble(order, viewer.getId());
    }

    /**
     * 组装订单详情，不校验可见性。
     *
     * <p>调用方必须先完成权限判定：写路径的调用方是刚刚通过状态机的参与方。
     */
    public OrderDetailResponse assemble(Order order, Long viewerId) {
        OrderSnapshot snapshot = orderSnapshotRepository.findById(order.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单快照缺失"));
        List<OrderEvent> events = orderEventRepository.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId());
        Map<Long, UserSummaryResponse> operators = loadSummaries(operatorIds(events));

        UserSummaryResponse buyer = summaryOf(order.getBuyerId());
        UserSummaryResponse seller = summaryOf(order.getSellerId());

        List<OrderEventResponse> eventResponses = new ArrayList<>(events.size());
        for (OrderEvent event : events) {
            eventResponses.add(OrderEventResponse.from(event, operators.get(event.getOperatorId())));
        }

        return new OrderDetailResponse(
                order.getId(),
                order.getOrderNo(),
                order.getStatus(),
                order.getTradeMode(),
                order.getAmount(),
                order.getCancelReason(),
                OrderItemSnapshotResponse.from(order.getItemId(), snapshot),
                buyer,
                seller,
                order.allowedActionsFor(viewerId),
                eventResponses,
                order.getCreatedAt(),
                order.getConfirmedAt(),
                order.getDeliveredAt(),
                order.getCompletedAt(),
                order.getCancelledAt(),
                order.getUpdatedAt());
    }

    /** 供评价模块复用：订单的参与者用户摘要。 */
    public UserSummaryResponse summaryOf(Long userId) {
        return userRepository.findById(userId)
                .map(UserSummaryResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
    }

    private List<Long> operatorIds(List<OrderEvent> events) {
        return events.stream().map(OrderEvent::getOperatorId).distinct().toList();
    }

    private Map<Long, UserSummaryResponse> loadSummaries(List<Long> userIds) {
        Map<Long, UserSummaryResponse> summaries = new HashMap<>();
        userRepository.findAllById(userIds).forEach(user -> summaries.put(user.getId(), UserSummaryResponse.from(user)));
        return summaries;
    }

    private static OrderSummaryResponse toSummary(OrderSummaryRow row) {
        return new OrderSummaryResponse(
                row.getOrderId(),
                row.getOrderNo(),
                row.getStatus(),
                row.getTradeMode(),
                row.getAmount(),
                OrderItemSnapshotResponse.of(row.getItemId(), row.getItemTitle(), row.getItemImageUrl(),
                        row.getItemPrice()),
                new UserSummaryResponse(row.getCounterpartId(), row.getCounterpartNickname(),
                        row.getCounterpartAvatarUrl()),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }
}
