package com.graduation.backend.admin.api;

import com.graduation.backend.admin.api.dto.AdminOrderSummaryResponse;
import com.graduation.backend.admin.application.AdminOrderQueryService;
import com.graduation.backend.common.security.CurrentUserService;
import com.graduation.backend.common.web.ApiResponse;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.order.api.dto.OrderDetailResponse;
import com.graduation.backend.order.domain.OrderStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 管理员专属订单列表与只读详情，不复用个人订单权限。 */
@RestController
@RequestMapping("/api/v1/admin/orders")
@Validated
public class AdminOrderController {
    private final AdminOrderQueryService service;
    private final CurrentUserService currentUserService;

    public AdminOrderController(AdminOrderQueryService service, CurrentUserService currentUserService) {
        this.service = service;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminOrderSummaryResponse>> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) Long buyerId,
            @RequestParam(required = false) Long sellerId,
            @RequestParam(required = false) @Size(min = 1, max = 64) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(service.list(currentUserService.requireCurrentUserId(), status,
                buyerId, sellerId, keyword, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(service.detail(currentUserService.requireCurrentUserId(), id));
    }
}
