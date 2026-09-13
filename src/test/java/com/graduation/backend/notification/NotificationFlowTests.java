package com.graduation.backend.notification;

import com.graduation.backend.order.OrderTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 站内消息与未读红点的真实 MySQL 端到端用例。
 *
 * <p>覆盖：订单状态变化给对方生成消息、未读数随已读变化、标记已读幂等、
 * 读不到别人的消息、全部已读只统计本次变更、管理员下架通知卖家、被禁用账号的旧令牌失效。
 */
@EnabledIf("databaseConfigured")
class NotificationFlowTests extends OrderTestSupport {

    @Test
    @DisplayName("下单通知卖家、接单通知买家，未读数随标记已读变化且标记操作幂等")
    void orderLifecycleGeneratesNotifications() throws Exception {
        String seller = certifiedToken("notif-flow-seller");
        String buyer = certifiedToken("notif-flow-buyer");
        PublishedItem item = publishItem(seller, "99.00");
        String body = createOrder(buyer, item.itemId(), newRequestId()).getResponse().getContentAsString();
        String orderId = readString(body, "$.data.id");

        // 消息在事务提交后才写库，因此下单接口返回时卖家已经能读到未读数。
        assertThat(unreadCount(seller)).isEqualTo(1);
        assertThat(unreadCount(buyer)).isZero();

        MvcResult listed = mockMvc.perform(get("/api/v1/notifications")
                        .param("read", "false").param("type", "ORDER_CREATED")
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.items[0].type").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.data.items[0].read").value(false))
                .andExpect(jsonPath("$.data.items[0].readAt").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].bizType").value("ORDER"))
                .andExpect(jsonPath("$.data.items[0].bizId").value(orderId))
                .andExpect(jsonPath("$.data.items[0].content", containsString(item.title())))
                .andReturn();
        String notificationId = readString(listed.getResponse().getContentAsString(), "$.data.items[0].id");

        // 别人的消息：既读不到内容，也不能标记已读——两种情况都按不存在处理。
        mockMvc.perform(put("/api/v1/notifications/{id}/read", notificationId)
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/notifications").header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(put("/api/v1/notifications/{id}/read", notificationId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isNoContent());
        assertThat(unreadCount(seller)).isZero();
        // 幂等：已读再标记一次仍然是成功。
        mockMvc.perform(put("/api/v1/notifications/{id}/read", notificationId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isNoContent());

        // 接单通知走的是买家维度，且与卖家的消息互不干扰。
        mockMvc.perform(post("/api/v1/orders/{id}/confirm", orderId).header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/notifications")
                        .param("type", "ORDER_CONFIRMED")
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].bizId").value(orderId));
        assertThat(unreadCount(buyer)).isEqualTo(1);
        assertThat(unreadCount(seller)).isZero();

        // 消息接口没有匿名场景：不带凭证一律 401。
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("全部标记已读只统计本次由未读转为已读的条数，重复调用返回 0")
    void markAllReadCountsOnlyChanges() throws Exception {
        String seller = certifiedToken("notif-all-seller");
        String buyer = certifiedToken("notif-all-buyer");
        PublishedItem item = publishItem(seller, "18.00");
        String body = createOrder(buyer, item.itemId(), newRequestId()).getResponse().getContentAsString();
        String orderId = readString(body, "$.data.id");
        // 接单给买家一条，随后买家取消又给卖家一条：两边各攒下一条以上的未读。
        mockMvc.perform(post("/api/v1/orders/{id}/confirm", orderId).header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header(AUTHORIZATION, bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonBody("不想要了")))
                .andExpect(status().isOk());

        assertThat(unreadCount(seller)).isEqualTo(2);
        assertThat(unreadCount(buyer)).isEqualTo(1);

        mockMvc.perform(put("/api/v1/notifications/read-all").header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(2));
        mockMvc.perform(put("/api/v1/notifications/read-all").header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(0));
        assertThat(unreadCount(seller)).isZero();

        // 全部已读只作用于本人：买家的未读不受影响。
        assertThat(unreadCount(buyer)).isEqualTo(1);
    }

    @Test
    @DisplayName("管理员强制下架给卖家发 SYSTEM 消息，内容带上架原因")
    void adminOffShelfNotifiesSeller() throws Exception {
        String seller = certifiedToken("notif-offshelf-seller");
        PublishedItem item = publishItem(seller, "15.00");
        String reason = "涉嫌违规内容，请修改后重新上架";

        mockMvc.perform(post("/api/v1/admin/items/{id}/off-shelf", item.itemId())
                        .header(AUTHORIZATION, bearer(adminToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonBody(reason)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications")
                        .param("type", "SYSTEM")
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].bizType").value("ITEM"))
                .andExpect(jsonPath("$.data.items[0].bizId").value(item.itemId()))
                .andExpect(jsonPath("$.data.items[0].title").value("商品被管理员下架"))
                .andExpect(jsonPath("$.data.items[0].content", containsString(reason)))
                .andExpect(jsonPath("$.data.items[0].content", containsString(item.title())));
        assertThat(itemStatus(item.itemId())).isEqualTo("OFF_SHELF");
    }

    @Test
    @DisplayName("被禁用账号的旧 Access Token 访问消息接口按 403 处理，刷新令牌全部撤销且不能重新登录")
    void disabledUserLosesAccess() throws Exception {
        String code = "notif-disabled-user";
        String loginJson = login(code);
        String token = accessTokenOf(loginJson);
        String userId = userIdOf(loginJson);

        mockMvc.perform(get("/api/v1/notifications").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/users/{id}/disable", userId)
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(get("/api/v1/notifications").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_DISABLED"));
        mockMvc.perform(get("/api/v1/notifications/unread-count").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_DISABLED"));
        // 未读数走的是另一条查询路径，同样要按事务内读到的状态拒绝。
        mockMvc.perform(put("/api/v1/notifications/read-all").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_DISABLED"));

        // 禁用同时撤销该用户全部刷新令牌；否则旧 Refresh Token 还能换出可用的 Access Token。
        Integer activeTokens = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                Integer.class, Long.valueOf(userId));
        assertThat(activeTokens).isZero();
        mockMvc.perform(post("/api/v1/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "-" + runId() + "\","
                                + "\"deviceId\":\"device-disabled\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_DISABLED"));

        // 恢复后状态回到 ACTIVE，重新登录即可正常访问。
        mockMvc.perform(post("/api/v1/admin/users/{id}/enable", userId)
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(get("/api/v1/notifications")
                        .header(AUTHORIZATION, bearer(accessTokenOf(login(code)))))
                .andExpect(status().isOk());
    }
}
