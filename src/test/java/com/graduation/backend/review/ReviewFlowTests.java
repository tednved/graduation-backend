package com.graduation.backend.review;

import com.graduation.backend.order.OrderTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 评价与信用摘要的真实 MySQL 端到端用例。
 *
 * <p>覆盖：完成后双方互评、被评价人由服务端按订单对端推导、重复评价冲突、
 * 未完成订单不可评价、非参与方不可评价、可选版本校验、评分筛选与信用分布补齐。
 */
@EnabledIf("databaseConfigured")
class ReviewFlowTests extends OrderTestSupport {

    @Test
    @DisplayName("交易完成后双方互评，被评价人由订单对端推导，信用汇总与分布按可见评价重算")
    void bothSidesReviewAndCreditAggregated() throws Exception {
        String seller = certifiedToken("review-main-seller");
        String buyer = certifiedToken("review-main-buyer");
        PublishedItem item = publishItem(seller, "120.00");
        String orderBody = createOrder(buyer, item.itemId(), newRequestId()).getResponse().getContentAsString();
        String orderId = readString(orderBody, "$.data.id");
        String buyerId = readString(orderBody, "$.data.buyer.id");
        String sellerId = readString(orderBody, "$.data.seller.id");
        advanceToCompleted(seller, buyer, orderId);

        // 双方都还没评价：canReview 为真，两个评价 ID 都为空。
        mockMvc.perform(get("/api/v1/orders/{id}/review-eligibility", orderId)
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canReview").value(true))
                .andExpect(jsonPath("$.data.myReviewId").value(nullValue()))
                .andExpect(jsonPath("$.data.counterpartReviewed").value(false))
                .andExpect(jsonPath("$.data.counterpartReviewId").value(nullValue()));

        MvcResult buyerReview = mockMvc.perform(post("/api/v1/reviews")
                        .header(AUTHORIZATION, bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(orderId, 5, "描述一致，交易顺利")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.status").value("VISIBLE"))
                .andExpect(jsonPath("$.data.reviewer.id").value(buyerId))
                // 被评价人取订单对端：请求体里没有 revieweeId，客户端也无法指定。
                .andExpect(jsonPath("$.data.reviewee.id").value(sellerId))
                .andReturn();
        String buyerReviewId = readString(buyerReview.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(post("/api/v1/reviews")
                        .header(AUTHORIZATION, bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(orderId, 5, "再评一次")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));

        mockMvc.perform(get("/api/v1/orders/{id}/review-eligibility", orderId)
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canReview").value(false))
                .andExpect(jsonPath("$.data.myReviewId").value(buyerReviewId))
                .andExpect(jsonPath("$.data.counterpartReviewed").value(false));

        mockMvc.perform(post("/api/v1/reviews")
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(orderId, 4, "买家爽快")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reviewer.id").value(sellerId))
                .andExpect(jsonPath("$.data.reviewee.id").value(buyerId));

        mockMvc.perform(get("/api/v1/orders/{id}/review-eligibility", orderId)
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counterpartReviewed").value(true))
                .andExpect(jsonPath("$.data.counterpartReviewId").isNotEmpty());

        // 信用汇总取用户表的重算值：卖方只收到 5 星，买方只收到 4 星。
        mockMvc.perform(get("/api/v1/users/{id}/credit", sellerId).header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(sellerId))
                .andExpect(jsonPath("$.data.averageRating").value("5.00"))
                .andExpect(jsonPath("$.data.reviewCount").value(1))
                .andExpect(jsonPath("$.data.distribution['5']").value(1))
                .andExpect(jsonPath("$.data.distribution['4']").value(0))
                .andExpect(jsonPath("$.data.distribution['1']").value(0));

        mockMvc.perform(get("/api/v1/users/{id}/credit", buyerId).header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.averageRating").value("4.00"))
                .andExpect(jsonPath("$.data.reviewCount").value(1))
                .andExpect(jsonPath("$.data.distribution['4']").value(1));

        // 评价列表与信用摘要是公开读接口：匿名也能拿到，且只给双方公开摘要。
        mockMvc.perform(get("/api/v1/users/{id}/reviews", sellerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(buyerReviewId))
                .andExpect(jsonPath("$.data.items[0].rating").value(5))
                .andExpect(jsonPath("$.data.items[0].content").value("描述一致，交易顺利"))
                .andExpect(jsonPath("$.data.items[0].reviewer.id").value(buyerId))
                .andExpect(jsonPath("$.data.items[0].reviewee.id").value(sellerId));

        mockMvc.perform(get("/api/v1/users/{id}/reviews", sellerId).param("rating", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/users/{id}/credit", sellerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewCount").value(1));
    }

    @Test
    @DisplayName("订单未完成不能评价，非参与方既不能评价也拿不到评价状态")
    void reviewRequiresCompletedOrderAndParticipation() throws Exception {
        String seller = certifiedToken("review-guard-seller");
        String buyer = certifiedToken("review-guard-buyer");
        String stranger = certifiedToken("review-guard-stranger");
        PublishedItem item = publishItem(seller, "60.00");
        String body = createOrder(buyer, item.itemId(), newRequestId()).getResponse().getContentAsString();
        String orderId = readString(body, "$.data.id");

        mockMvc.perform(post("/api/v1/reviews")
                        .header(AUTHORIZATION, bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(orderId, 5, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));

        mockMvc.perform(post("/api/v1/reviews")
                        .header(AUTHORIZATION, bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(orderId, 5, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_OPERATION_FORBIDDEN"));
        mockMvc.perform(get("/api/v1/orders/{id}/review-eligibility", orderId)
                        .header(AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_OPERATION_FORBIDDEN"));

        // 订单不存在同样按资源不存在处理，不泄露订单是否存在。
        mockMvc.perform(post("/api/v1/reviews")
                        .header(AUTHORIZATION, bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody("999999999", 5, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/reviews")
                        .header(AUTHORIZATION, bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewBody(orderId, 6, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("带版本的评价请求在订单已变化时冲突，版本一致时正常落库")
    void reviewVersionConflict() throws Exception {
        String seller = certifiedToken("review-version-seller");
        String buyer = certifiedToken("review-version-buyer");
        PublishedItem item = publishItem(seller, "77.00");
        String orderId = completeOrder(seller, buyer, item.itemId());
        String version = orderVersion(orderId);

        mockMvc.perform(post("/api/v1/reviews")
                        .header(AUTHORIZATION, bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"rating\":5,"
                                + "\"version\":" + (Integer.parseInt(version) + 1) + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));

        mockMvc.perform(post("/api/v1/reviews")
                        .header(AUTHORIZATION, bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"rating\":5,"
                                + "\"version\":" + version + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value(nullValue()));

        // 不传 content 就是不填内容：库里存 null，列表里也是 null，而不是一条空字符串。
        mockMvc.perform(get("/api/v1/users/{id}/reviews", sellerIdOf(buyer, orderId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].content").value(nullValue()));
    }
}
