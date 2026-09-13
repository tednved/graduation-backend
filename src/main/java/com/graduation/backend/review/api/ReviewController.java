package com.graduation.backend.review.api;

import com.graduation.backend.common.security.CurrentUserService;
import com.graduation.backend.common.web.ApiResponse;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.review.api.dto.CreateReviewRequest;
import com.graduation.backend.review.api.dto.ReviewEligibilityResponse;
import com.graduation.backend.review.api.dto.ReviewResponse;
import com.graduation.backend.review.api.dto.UserCreditResponse;
import com.graduation.backend.review.application.ReviewApplicationService;
import com.graduation.backend.review.application.ReviewQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评价接口。
 *
 * <p>类级路径只到 {@code /api/v1}：本控制器同时覆盖 {@code /reviews}、
 * {@code /orders/{id}/review-eligibility} 与 {@code /users/{id}/reviews|credit}。
 *
 * <p>评价列表与信用摘要是公开接口（契约 {@code security: []}），因此不解析登录态；
 * 只有创建评价与查询本人评价资格需要登录。
 */
@RestController
@RequestMapping("/api/v1")
@Validated
public class ReviewController {

    private final ReviewApplicationService reviewApplicationService;
    private final ReviewQueryService reviewQueryService;
    private final CurrentUserService currentUserService;

    public ReviewController(ReviewApplicationService reviewApplicationService,
                            ReviewQueryService reviewQueryService,
                            CurrentUserService currentUserService) {
        this.reviewApplicationService = reviewApplicationService;
        this.reviewQueryService = reviewQueryService;
        this.currentUserService = currentUserService;
    }

    /** 创建评价：订单须已完成，被评价人由服务端按对端推导。 */
    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReviewResponse> create(@Valid @RequestBody CreateReviewRequest request) {
        return ApiResponse.ok(reviewApplicationService.create(currentUserId(), request));
    }

    /** 订单的双方评价状态：仅订单双方可见。 */
    @GetMapping("/orders/{id}/review-eligibility")
    public ApiResponse<ReviewEligibilityResponse> eligibility(@PathVariable("id") Long id) {
        return ApiResponse.ok(reviewQueryService.eligibility(currentUserId(), id));
    }

    /** 用户收到的评价：公开，可按分数筛选。 */
    @GetMapping("/users/{id}/reviews")
    public ApiResponse<PageResult<ReviewResponse>> userReviews(
            @PathVariable("id") Long id,
            @RequestParam(required = false) @Min(1) @Max(5) Integer rating,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(reviewQueryService.userReviews(id, rating, page, size));
    }

    /** 用户信用摘要：公开。 */
    @GetMapping("/users/{id}/credit")
    public ApiResponse<UserCreditResponse> credit(@PathVariable("id") Long id) {
        return ApiResponse.ok(reviewQueryService.credit(id));
    }

    private Long currentUserId() {
        return currentUserService.requireCurrentUserId();
    }
}
