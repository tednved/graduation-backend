package com.graduation.backend.admin.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.backend.admin.api.dto.AdminOrderSummaryResponse;
import com.graduation.backend.admin.query.AdminOrderQueryMapper;
import com.graduation.backend.admin.query.AdminOrderRow;
import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import com.graduation.backend.common.web.LikePatterns;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.order.api.dto.OrderDetailResponse;
import com.graduation.backend.order.application.OrderQueryService;
import com.graduation.backend.order.domain.Order;
import com.graduation.backend.order.domain.OrderRepository;
import com.graduation.backend.order.domain.OrderStatus;
import com.graduation.backend.user.application.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理员专属订单读取；与管理员自己的买卖订单完全分离，且不提供状态命令。 */
@Service
public class AdminOrderQueryService {
    private static final int KEYWORD_MAX = 64;

    private final AdminOrderQueryMapper mapper;
    private final OrderRepository orderRepository;
    private final OrderQueryService orderQueryService;
    private final UserService userService;

    public AdminOrderQueryService(AdminOrderQueryMapper mapper, OrderRepository orderRepository,
                                  OrderQueryService orderQueryService, UserService userService) {
        this.mapper = mapper;
        this.orderRepository = orderRepository;
        this.orderQueryService = orderQueryService;
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public PageResult<AdminOrderSummaryResponse> list(Long adminId, OrderStatus status, Long buyerId,
                                                       Long sellerId, String keyword, int page, int size) {
        userService.requireAdmin(adminId);
        IPage<AdminOrderRow> result = mapper.selectOrders(new Page<>(page + 1L, size),
                status == null ? null : status.name(), buyerId, sellerId,
                LikePatterns.contains(keyword, KEYWORD_MAX));
        return PageResult.of(page, size, result.getTotal(),
                result.getRecords().stream().map(AdminOrderSummaryResponse::fromRow).toList());
    }

    @Transactional(readOnly = true)
    public OrderDetailResponse detail(Long adminId, Long orderId) {
        userService.requireAdmin(adminId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在"));
        // 管理视图只读，即使管理员恰好是订单参与方，也不能从管理入口获得业务动作。
        return orderQueryService.assemble(order, null);
    }
}
