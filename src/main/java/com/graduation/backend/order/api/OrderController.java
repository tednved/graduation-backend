package com.graduation.backend.order.api;

import com.graduation.backend.common.security.CurrentUserService;
import com.graduation.backend.common.web.ApiResponse;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.order.api.dto.CreateOrderRequest;
import com.graduation.backend.order.api.dto.OrderDetailResponse;
import com.graduation.backend.order.api.dto.OrderReasonRequest;
import com.graduation.backend.order.api.dto.OrderSummaryResponse;
import com.graduation.backend.order.application.OrderApplicationService;
import com.graduation.backend.order.application.OrderCreationResult;
import com.graduation.backend.order.application.OrderQueryService;
import com.graduation.backend.order.domain.OrderSide;
import com.graduation.backend.order.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口。
 *
 * <p>类级路径只到 {@code /api/v1}：本控制器同时覆盖 {@code /orders/**} 与
 * {@code /users/me/orders}。
 *
 * <p>创建订单用 {@link ResponseEntity} 而不是 {@code @ResponseStatus}：首次创建与幂等重放
 * 共用同一段业务逻辑，却要分别返回 201 与 200，状态码由服务层返回的
 * {@link OrderCreationResult#created()} 决定。
 *
 * <p>控制器不推断权限：{@code allowedActions} 与每个命令的校验都在服务层事务内完成。
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class OrderController {

    private final OrderApplicationService orderApplicationService;
    private final OrderQueryService orderQueryService;
    private final CurrentUserService currentUserService;

    public OrderController(OrderApplicationService orderApplicationService,
                           OrderQueryService orderQueryService,
                           CurrentUserService currentUserService) {
        this.orderApplicationService = orderApplicationService;
        this.orderQueryService = orderQueryService;
        this.currentUserService = currentUserService;
    }

    /** 创建订单：首次 201，同键同内容重放 200。 */
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<OrderDetailResponse>> create(
            @Valid @RequestBody CreateOrderRequest request) {
        OrderCreationResult result = orderApplicationService.create(currentUserId(), request);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(result.detail()));
    }

    /** 订单详情：仅买卖双方或管理员可见。 */
    @GetMapping("/orders/{id}")
    public ApiResponse<OrderDetailResponse> detail(@PathVariable("id") Long id) {
        return ApiResponse.ok(orderQueryService.detail(currentUserId(), id));
    }

    /** 卖家接单。 */
    @PostMapping("/orders/{id}/confirm")
    public ApiResponse<OrderDetailResponse> confirm(@PathVariable("id") Long id) {
        return ApiResponse.ok(orderApplicationService.confirm(currentUserId(), id));
    }

    /** 卖家拒单。 */
    @PostMapping("/orders/{id}/reject")
    public ApiResponse<OrderDetailResponse> reject(@PathVariable("id") Long id,
                                                   @Valid @RequestBody OrderReasonRequest request) {
        return ApiResponse.ok(orderApplicationService.reject(currentUserId(), id, request.reason()));
    }

    /** 取消订单。 */
    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<OrderDetailResponse> cancel(@PathVariable("id") Long id,
                                                   @Valid @RequestBody OrderReasonRequest request) {
        return ApiResponse.ok(orderApplicationService.cancel(currentUserId(), id, request.reason()));
    }

    /** 卖家确认交付。 */
    @PostMapping("/orders/{id}/deliver")
    public ApiResponse<OrderDetailResponse> deliver(@PathVariable("id") Long id) {
        return ApiResponse.ok(orderApplicationService.deliver(currentUserId(), id));
    }

    /** 买家确认收货。 */
    @PostMapping("/orders/{id}/receive")
    public ApiResponse<OrderDetailResponse> receive(@PathVariable("id") Long id) {
        return ApiResponse.ok(orderApplicationService.receive(currentUserId(), id));
    }

    /** 我的订单：{@code side} 必填，BUY 为买家视角、SELL 为卖家视角。 */
    @GetMapping("/users/me/orders")
    public ApiResponse<PageResult<OrderSummaryResponse>> myOrders(
            @RequestParam OrderSide side,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(orderQueryService.myOrders(currentUserId(), side, status, page, size));
    }

    private Long currentUserId() {
        return currentUserService.requireCurrentUserId();
    }
}
