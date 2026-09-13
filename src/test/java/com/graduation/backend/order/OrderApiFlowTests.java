package com.graduation.backend.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单主链路与关键失败分支的真实 MySQL 端到端用例。
 *
 * <p>覆盖：下单到收货的完整状态机（订单状态、商品状态、事件时间线三者同步）、
 * 幂等重放与幂等键内容冲突、自购/未认证/商品不可售/并发抢单、参与方可见性、
 * 非法状态转换、拒单与取消释放商品、我的订单的买卖视角与状态筛选。
 */
@EnabledIf("databaseConfigured")
class OrderApiFlowTests extends OrderTestSupport {

    @Test
    @DisplayName("下单到收货：订单状态、商品状态与事件时间线同步推进，允许动作按身份与状态计算")
    void fullTradeChain() throws Exception {
        String seller = certifiedToken("order-chain-seller");
        String buyer = certifiedToken("order-chain-buyer");
        PublishedItem item = publishItem(seller, "88.00");

        MvcResult created = createOrder(buyer, item.itemId(), newRequestId());
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String body = created.getResponse().getContentAsString();
        String orderId = readString(body, "$.data.id");
        String buyerId = readString(body, "$.data.buyer.id");

        assertThat(readString(body, "$.data.orderNo")).startsWith("OD");
        assertThat(readString(body, "$.data.status")).isEqualTo("PENDING_CONFIRMATION");
        assertThat(readString(body, "$.data.tradeMode")).isEqualTo("OFFLINE");
        // 金额与商品快照取下单那一刻的值，商品后续改价不影响已生成的订单。
        assertThat(readString(body, "$.data.amount")).isEqualTo("88.00");
        assertThat(readString(body, "$.data.item.title")).isEqualTo(item.title());
        assertThat(readString(body, "$.data.item.itemId")).isEqualTo(item.itemId());
        assertThat(readString(body, "$.data.item.price")).isEqualTo("88.00");
        assertThat(readString(body, "$.data.item.imageUrl")).startsWith("/media/");
        assertThat(readString(body, "$.data.cancelReason")).isNull();
        assertThat("RESERVED").isEqualTo(itemStatus(item.itemId()));

        // 买家看到的可执行动作只有取消，卖家才看到接单/拒单——同一订单按查看者身份分别计算。
        mockMvc.perform(get("/api/v1/orders/{id}", orderId).header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.allowedActions", contains("CANCEL")))
                .andExpect(jsonPath("$.data.events", hasSize(1)))
                .andExpect(jsonPath("$.data.events[0].action").value("CREATE"))
                .andExpect(jsonPath("$.data.events[0].fromStatus").value(nullValue()))
                .andExpect(jsonPath("$.data.events[0].toStatus").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.events[0].operator.id").value(buyerId));

        mockMvc.perform(post("/api/v1/orders/{id}/confirm", orderId).header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.allowedActions", contains("CANCEL", "DELIVER")));
        assertThat(itemStatus(item.itemId())).isEqualTo("RESERVED");

        mockMvc.perform(post("/api/v1/orders/{id}/deliver", orderId).header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_RECEIPT"))
                .andExpect(jsonPath("$.data.deliveredAt").isNotEmpty())
                .andExpect(jsonPath("$.data.allowedActions", hasSize(0)));
        assertThat(itemStatus(item.itemId())).isEqualTo("RESERVED");

        mockMvc.perform(post("/api/v1/orders/{id}/receive", orderId).header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.allowedActions", hasSize(0)))
                .andExpect(jsonPath("$.data.events", hasSize(4)))
                .andExpect(jsonPath("$.data.events[3].action").value("RECEIVE"))
                .andExpect(jsonPath("$.data.events[3].fromStatus").value("PENDING_RECEIPT"))
                .andExpect(jsonPath("$.data.events[3].toStatus").value("COMPLETED"));
        // 收货必须与商品转已售出同事务：不能出现订单已完成但商品还能被下单的中间态。
        assertThat(itemStatus(item.itemId())).isEqualTo("SOLD");
    }

    @Test
    @DisplayName("同键同内容重放返回原订单且不产生第二笔；同键换商品判为幂等键冲突")
    void idempotentCreateReplaysSameOrder() throws Exception {
        String seller = certifiedToken("order-idem-seller");
        String buyer = certifiedToken("order-idem-buyer");
        PublishedItem item = publishItem(seller, "50.00");
        String requestId = newRequestId();

        MvcResult first = createOrder(buyer, item.itemId(), requestId);
        MvcResult replay = createOrder(buyer, item.itemId(), requestId);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);

        String firstBody = first.getResponse().getContentAsString();
        String replayBody = replay.getResponse().getContentAsString();
        assertThat(readString(replayBody, "$.data.id")).isEqualTo(readString(firstBody, "$.data.id"));
        assertThat(readString(replayBody, "$.data.orderNo")).isEqualTo(readString(firstBody, "$.data.orderNo"));
        // 重放不产生新的订单事件，也不会把商品重新预留一次。
        assertThat(readInt(replayBody, "$.data.events.length()"))
                .isEqualTo(readInt(firstBody, "$.data.events.length()"));
        assertThat(readString(replayBody, "$.data.status")).isEqualTo("PENDING_CONFIRMATION");

        String buyerId = readString(firstBody, "$.data.buyer.id");
        Integer orders = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE buyer_id = ?", Integer.class, Long.valueOf(buyerId));
        assertThat(orders).isEqualTo(1);

        // 同一个幂等键指向另一件商品：说明客户端复用了标识发起新交易，必须显式冲突。
        PublishedItem other = publishItem(seller, "30.00");
        mockMvc.perform(post("/api/v1/orders")
                        .header(AUTHORIZATION, bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(other.itemId(), requestId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_DUPLICATE_REQUEST"));
    }

    @Test
    @DisplayName("不能购买自己的商品；未认证买家下单被拒绝")
    void selfPurchaseAndCertificationRejected() throws Exception {
        String seller = certifiedToken("order-guard-seller");
        PublishedItem item = publishItem(seller, "66.00");

        mockMvc.perform(post("/api/v1/orders")
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(item.itemId(), newRequestId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_SELF_PURCHASE"));

        String uncertified = accessTokenOf(login("order-uncertified-buyer"));
        mockMvc.perform(post("/api/v1/orders")
                        .header(AUTHORIZATION, bearer(uncertified))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(item.itemId(), newRequestId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_CERTIFICATION_REQUIRED"));
    }

    @Test
    @DisplayName("已预留的商品判为并发抢单，其余不可售状态与不存在的商品分别拒绝")
    void unavailableItemRejected() throws Exception {
        String seller = certifiedToken("order-unavail-seller");
        String firstBuyer = certifiedToken("order-unavail-buyer-a");
        String secondBuyer = certifiedToken("order-unavail-buyer-b");
        PublishedItem item = publishItem(seller, "40.00");

        createOrder(firstBuyer, item.itemId(), newRequestId());
        mockMvc.perform(post("/api/v1/orders")
                        .header(AUTHORIZATION, bearer(secondBuyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(item.itemId(), newRequestId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_CONCURRENTLY_RESERVED"));

        // 下架后重新可售性由状态判定，与「被别人抢先预留」用不同错误码区分。
        PublishedItem offline = publishItem(seller, "41.00");
        mockMvc.perform(post("/api/v1/items/{id}/off-shelf", offline.itemId())
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders")
                        .header(AUTHORIZATION, bearer(firstBuyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(offline.itemId(), newRequestId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_AVAILABLE"));

        mockMvc.perform(post("/api/v1/orders")
                        .header(AUTHORIZATION, bearer(firstBuyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody("999999999", newRequestId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("未支持的交易方式按下单参数错误处理，不落库")
    void unsupportedTradeModeRejected() throws Exception {
        String seller = certifiedToken("order-mode-seller");
        String buyer = certifiedToken("order-mode-buyer");
        PublishedItem item = publishItem(seller, "25.00");

        mockMvc.perform(post("/api/v1/orders")
                        .header(AUTHORIZATION, bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":" + item.itemId() + ",\"tradeMode\":\"SIMULATED_PAYMENT\","
                                + "\"clientRequestId\":\"" + newRequestId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(itemStatus(item.itemId())).isEqualTo("ON_SALE");
    }

    @Test
    @DisplayName("非参与方看不到订单详情，也不能执行任何订单命令；管理员可以查看")
    void onlyParticipantsCanAccessOrder() throws Exception {
        String seller = certifiedToken("order-access-seller");
        String buyer = certifiedToken("order-access-buyer");
        String stranger = certifiedToken("order-access-stranger");
        PublishedItem item = publishItem(seller, "70.00");
        String body = createOrder(buyer, item.itemId(), newRequestId()).getResponse().getContentAsString();
        String orderId = readString(body, "$.data.id");

        mockMvc.perform(get("/api/v1/orders/{id}", orderId).header(AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_OPERATION_FORBIDDEN"));
        mockMvc.perform(post("/api/v1/orders/{id}/confirm", orderId).header(AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_OPERATION_FORBIDDEN"));

        mockMvc.perform(get("/api/v1/orders/{id}", orderId).header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId));

        mockMvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("身份不符返回 403，状态不符返回 409；待收货阶段不允许普通用户取消")
    void illegalTransitionsRejected() throws Exception {
        String seller = certifiedToken("order-illegal-seller");
        String buyer = certifiedToken("order-illegal-buyer");
        PublishedItem item = publishItem(seller, "35.00");
        String body = createOrder(buyer, item.itemId(), newRequestId()).getResponse().getContentAsString();
        String orderId = readString(body, "$.data.id");

        // 接单只有卖家能做；待确认阶段卖家也不能取消（卖家走拒单）。
        mockMvc.perform(post("/api/v1/orders/{id}/confirm", orderId).header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_OPERATION_FORBIDDEN"));
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonBody("卖家不想卖了")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_OPERATION_FORBIDDEN"));

        // 未接单就交付：状态转换冲突。
        mockMvc.perform(post("/api/v1/orders/{id}/deliver", orderId).header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ILLEGAL_STATUS_TRANSITION"));

        mockMvc.perform(post("/api/v1/orders/{id}/confirm", orderId).header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{id}/deliver", orderId).header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk());

        // 待收货阶段买家可以收货，但双方都不得再取消，商品也必须还留在预留状态。
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header(AUTHORIZATION, bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonBody("买错了")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ILLEGAL_STATUS_TRANSITION"));
        mockMvc.perform(post("/api/v1/orders/{id}/receive", orderId).header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_OPERATION_FORBIDDEN"));
        assertThat(orderStatus(orderId)).isEqualTo("PENDING_RECEIPT");
        assertThat(itemStatus(item.itemId())).isEqualTo("RESERVED");
    }

    @Test
    @DisplayName("卖家拒单与买家取消都把商品释放回在售，并记录原因")
    void rejectAndCancelReleaseItem() throws Exception {
        String seller = certifiedToken("order-release-seller");
        String buyer = certifiedToken("order-release-buyer");

        PublishedItem rejected = publishItem(seller, "45.00");
        String rejectBody = createOrder(buyer, rejected.itemId(), newRequestId())
                .getResponse().getContentAsString();
        String rejectOrderId = readString(rejectBody, "$.data.id");
        mockMvc.perform(post("/api/v1/orders/{id}/reject", rejectOrderId)
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonBody("商品已线下售出")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.cancelReason").value("商品已线下售出"))
                .andExpect(jsonPath("$.data.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.data.allowedActions", hasSize(0)))
                .andExpect(jsonPath("$.data.events[1].action").value("REJECT"))
                .andExpect(jsonPath("$.data.events[1].remark").value("商品已线下售出"));
        assertThat(itemStatus(rejected.itemId())).isEqualTo("ON_SALE");

        // 接单后由卖家取消：商品同样释放，另一方可继续下单。
        PublishedItem cancelled = publishItem(seller, "46.00");
        String cancelOrderId = readString(createOrder(buyer, cancelled.itemId(), newRequestId())
                .getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(post("/api/v1/orders/{id}/confirm", cancelOrderId).header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", cancelOrderId)
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonBody("临时有事不方便交易")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.events[2].action").value("CANCEL"))
                .andExpect(jsonPath("$.data.events[2].fromStatus").value("CONFIRMED"));
        assertThat(itemStatus(cancelled.itemId())).isEqualTo("ON_SALE");

        String thirdBuyer = certifiedToken("order-release-buyer-b");
        MvcResult retry = createOrder(thirdBuyer, cancelled.itemId(), newRequestId());
        assertThat(retry.getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    @DisplayName("我的订单按买卖视角分开返回：BUY 的对端是卖家，SELL 的对端是买家，可按状态筛选")
    void myOrdersSeparatesSides() throws Exception {
        String seller = certifiedToken("order-list-seller");
        String buyer = certifiedToken("order-list-buyer");
        PublishedItem item = publishItem(seller, "52.00");
        String body = createOrder(buyer, item.itemId(), newRequestId()).getResponse().getContentAsString();
        String orderId = readString(body, "$.data.id");
        String sellerId = readString(body, "$.data.seller.id");

        mockMvc.perform(get("/api/v1/users/me/orders")
                        .param("side", "BUY").param("status", "PENDING_CONFIRMATION")
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(orderId))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.items[0].amount").value("52.00"))
                .andExpect(jsonPath("$.data.items[0].item.title").value(item.title()))
                .andExpect(jsonPath("$.data.items[0].counterpart.id").value(sellerId));

        String buyerId = readString(body, "$.data.buyer.id");
        mockMvc.perform(get("/api/v1/users/me/orders")
                        .param("side", "SELL")
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].counterpart.id").value(buyerId));

        mockMvc.perform(get("/api/v1/users/me/orders")
                        .param("side", "BUY").param("status", "COMPLETED")
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));

        // 同一用户没有卖出的商品时 SELL 列表为空，而不是回落到 BUY 的数据。
        mockMvc.perform(get("/api/v1/users/me/orders")
                        .param("side", "SELL")
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    @DisplayName("接单先判身份再判商品状态：非参与方一律 403，不靠状态错误码泄漏订单进度")
    void confirmChecksIdentityBeforeItemState() throws Exception {
        String seller = certifiedToken("order-confirm-order-seller");
        String buyer = certifiedToken("order-confirm-order-buyer");
        String stranger = certifiedToken("order-confirm-order-stranger");
        PublishedItem item = publishItem(seller, "33.00");
        // 走到终态后商品已是 SOLD，不再是 RESERVED。
        String orderId = completeOrder(seller, buyer, item.itemId());
        assertThat(itemStatus(item.itemId())).isEqualTo("SOLD");

        // 关键回归：陌生人本不该看到「商品不处于预留」这条状态信息。
        // 若先判商品状态，这里会得到 409 ORDER_ILLEGAL_STATUS_TRANSITION，
        // 于是 403 与 409 的差别就成了一条可用来探测订单进度的侧信道。
        mockMvc.perform(get("/api/v1/orders/{id}", orderId).header(AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_OPERATION_FORBIDDEN"));
        mockMvc.perform(post("/api/v1/orders/{id}/confirm", orderId)
                        .header(AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORDER_OPERATION_FORBIDDEN"));

        // 卖家本人对已终结订单接单，才是状态冲突——同一个终态下两种身份得到两种错误码。
        mockMvc.perform(post("/api/v1/orders/{id}/confirm", orderId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ILLEGAL_STATUS_TRANSITION"));
    }
}
