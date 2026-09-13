package com.graduation.backend.admin;

import com.graduation.backend.order.OrderTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理端用户/商品/审计的真实 MySQL 端到端用例。
 *
 * <p>覆盖：用户分页筛选与 openid 不外泄、禁用自己与越权访问被拒、禁用保留认证状态、
 * 商品分页不过滤状态（草稿也能查到）、审计记录写入与筛选。
 */
@EnabledIf("databaseConfigured")
class AdminApiFlowTests extends OrderTestSupport {

    @Test
    @DisplayName("用户分页按关键字与状态筛选，响应里不含 openid，非管理员访问被拒")
    void userListHidesOpenidAndRequiresAdmin() throws Exception {
        String token = certifiedToken("admin-list-user");
        String userId = userIdOf(login("admin-list-user"));
        String nickname = uniqueTitle("待查用户");
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(nickname));

        MvcResult listed = mockMvc.perform(get("/api/v1/admin/users")
                        .param("keyword", nickname)
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(userId))
                .andExpect(jsonPath("$.data.items[0].nickname").value(nickname))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[0].certificationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.items[0].campus.id").value(mainCampusId()))
                .andExpect(jsonPath("$.data.items[0].openid").doesNotExist())
                .andReturn();
        // 契约明文要求「即使对管理员也不返回 openid」：整段响应里都不该出现这个词。
        assertThat(listed.getResponse().getContentAsString()).doesNotContain("openid");

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("keyword", nickname).param("status", "DISABLED")
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("certificationStatus", "APPROVED")
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(1)));

        // 普通用户带合法 Token 也拿不到管理端数据；匿名请求在鉴权层就被挡下。
        mockMvc.perform(get("/api/v1/admin/users").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/admin/users/{id}/disable", userId)
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("管理员不能禁用自己；禁用保留认证状态，恢复后账号可用")
    void disableRulesAndCertificationKept() throws Exception {
        String adminToken = adminToken();
        String adminId = userIdOf(login("item-admin"));
        mockMvc.perform(post("/api/v1/admin/users/{id}/disable", adminId)
                        .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        String target = certifiedToken("admin-target-user");
        String targetId = userIdOf(login("admin-target-user"));

        mockMvc.perform(post("/api/v1/admin/users/{id}/disable", targetId)
                        .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(targetId))
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                // 禁用不改认证状态：恢复后不必重新提交审核。
                .andExpect(jsonPath("$.data.certificationStatus").value("APPROVED"));

        mockMvc.perform(get("/api/v1/users/me").header(AUTHORIZATION, bearer(target)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_DISABLED"));

        mockMvc.perform(post("/api/v1/admin/users/{id}/enable", targetId)
                        .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.certificationStatus").value("APPROVED"));
        mockMvc.perform(get("/api/v1/users/me").header(AUTHORIZATION, bearer(target)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("管理端商品分页不受在售状态限制，草稿也能查到，并按卖家与状态筛选")
    void itemListIncludesNonOnSaleItems() throws Exception {
        String seller = certifiedToken("admin-item-seller");
        String sellerId = userIdOf(login("admin-item-seller"));
        String title = uniqueTitle("待审草稿商品");
        MvcResult created = mockMvc.perform(post("/api/v1/items")
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createItemBody(title, "9.90", enabledSecondLevelCategoryId(),
                                List.of(uploadItemImage(seller)))))
                .andExpect(status().isCreated())
                .andReturn();
        String itemId = readString(created.getResponse().getContentAsString(), "$.data.id");

        mockMvc.perform(get("/api/v1/admin/items")
                        .param("keyword", title).param("sellerId", sellerId)
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(itemId))
                .andExpect(jsonPath("$.data.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.data.items[0].price").value("9.90"))
                .andExpect(jsonPath("$.data.items[0].seller.id").value(sellerId))
                .andExpect(jsonPath("$.data.items[0].adminLock").value(false))
                .andExpect(jsonPath("$.data.items[0].offShelfReason").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].category.id").isNotEmpty());

        // 公开搜索只看在售商品，管理端多状态筛选与它互不影响。
        mockMvc.perform(get("/api/v1/items").param("keyword", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/admin/items")
                        .param("keyword", title).param("status", "ON_SALE")
                        .header(AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));

        mockMvc.perform(get("/api/v1/admin/items").header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("强制下架与禁用用户都写审计，可按动作与目标筛选")
    void auditLogsRecordAdminWrites() throws Exception {
        String adminToken = adminToken();
        String adminId = userIdOf(login("item-admin"));
        String seller = certifiedToken("admin-audit-seller");
        PublishedItem item = publishItem(seller, "12.00");
        String reason = "图片与描述不符，需重新提交";

        mockMvc.perform(post("/api/v1/admin/items/{id}/off-shelf", item.itemId())
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reasonBody(reason)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFF_SHELF"))
                .andExpect(jsonPath("$.data.adminLock").value(true))
                .andExpect(jsonPath("$.data.offShelfReason").value(reason));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("action", "ITEM_ADMIN_OFF_SHELF").param("targetId", item.itemId())
                        .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].operator.id").value(adminId))
                .andExpect(jsonPath("$.data.items[0].targetType").value("ITEM"))
                .andExpect(jsonPath("$.data.items[0].targetId").value(item.itemId()))
                .andExpect(jsonPath("$.data.items[0].requestId").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].detail.reason").value(reason))
                .andExpect(jsonPath("$.data.items[0].detail.status").value("OFF_SHELF"));

        String targetId = userIdOf(login("admin-audit-target"));
        mockMvc.perform(post("/api/v1/admin/users/{id}/disable", targetId)
                        .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("operatorId", adminId).param("action", "USER_DISABLE")
                        .param("targetType", "USER").param("targetId", targetId)
                        .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].targetId").value(targetId))
                .andExpect(jsonPath("$.data.items[0].detail.status").value("DISABLED"));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                        .param("targetType", "USER").param("targetId", targetId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }
}
