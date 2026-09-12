package com.graduation.backend.favorite;

import com.graduation.backend.item.ItemTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 收藏接口的真实 MySQL 端到端用例。
 *
 * <p>测试 profile 的 {@code app.wechat.mock-openid} 为空，openid 由 {@code code} 派生，
 * 因此可以在一台机器上造出多个真实用户：跨用户场景（不能收藏自己商品、收藏他人商品
 * 出现在自己的收藏列表、取消收藏）都是真实数据，不靠单用户近似。
 *
 * <p>{@code /items/{itemId}/favorite-status} 是两段路径，安全配置里只放行了单段的
 * {@code /api/v1/items/*}，所以它必须保持「需要登录」——这里的 401 断言就是那条安全边界的回归。
 */
@EnabledIf("databaseConfigured")
class FavoriteFlowTests extends ItemTestSupport {

    @Test
    @DisplayName("跨用户收藏闭环：不能收藏自己的商品，收藏他人商品出现在收藏列表，取消后消失")
    void favoriteLifecycleAcrossUsers() throws Exception {
        String seller = certifiedToken("fav-seller");
        String buyer = certifiedToken("fav-buyer");
        String itemId = publish(seller, uniqueTitle("被收藏商品"), "260.00");

        // 收藏自己的商品按契约是 409 ITEM_SELF_OPERATION（不是 403）。
        mockMvc.perform(put("/api/v1/items/{itemId}/favorite", itemId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_SELF_OPERATION"));

        // 收藏他人商品。
        mockMvc.perform(put("/api/v1/items/{itemId}/favorite", itemId)
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/items/{itemId}/favorite-status", itemId)
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(true));

        mockMvc.perform(get("/api/v1/items/{id}", itemId).header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorited").value(true))
                .andExpect(jsonPath("$.data.favoriteCount").value(1));

        // 出现在买家收藏列表，不出现在卖家收藏列表。
        mockMvc.perform(get("/api/v1/users/me/favorites").header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + itemId + "')].status")
                        .value(List.of("ON_SALE")));
        mockMvc.perform(get("/api/v1/users/me/favorites").header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + itemId + "')]", hasSize(0)));

        // 重复收藏幂等：不重复加计数。
        mockMvc.perform(put("/api/v1/items/{itemId}/favorite", itemId)
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT favorite_count FROM items WHERE id = ?",
                Integer.class, Long.valueOf(itemId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM favorites WHERE item_id = ?",
                Integer.class, Long.valueOf(itemId))).isEqualTo(1);

        // 取消收藏。
        mockMvc.perform(delete("/api/v1/items/{itemId}/favorite", itemId)
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/items/{itemId}/favorite-status", itemId)
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(jsonPath("$.data.favorited").value(false));
        mockMvc.perform(get("/api/v1/users/me/favorites").header(AUTHORIZATION, bearer(buyer)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        assertThat(jdbcTemplate.queryForObject("SELECT favorite_count FROM items WHERE id = ?",
                Integer.class, Long.valueOf(itemId))).isZero();

        // 重复取消同样幂等，计数不会变负。
        mockMvc.perform(delete("/api/v1/items/{itemId}/favorite", itemId)
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isNoContent());
        assertThat(jdbcTemplate.queryForObject("SELECT favorite_count FROM items WHERE id = ?",
                Integer.class, Long.valueOf(itemId))).isZero();
    }

    @Test
    @DisplayName("收藏列表按收藏时间倒序，支持分页")
    void favoritesAreOrderedByMostRecent() throws Exception {
        String seller = certifiedToken("fav-order-seller");
        String buyer = certifiedToken("fav-order-buyer");
        String first = publish(seller, uniqueTitle("先收藏的商品"), "10.00");
        String second = publish(seller, uniqueTitle("后收藏的商品"), "20.00");

        mockMvc.perform(put("/api/v1/items/{itemId}/favorite", first)
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/items/{itemId}/favorite", second)
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isNoContent());

        MvcResult all = mockMvc.perform(get("/api/v1/users/me/favorites").param("size", "100")
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andReturn();
        List<String> ids = JsonPath.read(all.getResponse().getContentAsString(), "$.data.items[*].id");
        assertThat(ids).containsExactly(second, first);

        // 分页：每页 1 条，第一页是最新收藏的那件。
        mockMvc.perform(get("/api/v1/users/me/favorites").param("page", "0").param("size", "1")
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.items[0].id").value(second));
        mockMvc.perform(get("/api/v1/users/me/favorites").param("page", "1").param("size", "1")
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(first))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    @DisplayName("不可收藏的商品一律 404：草稿、已删除、不存在的商品")
    void favoriteRequiresPubliclyVisibleItem() throws Exception {
        String seller = certifiedToken("fav-visibility-seller");
        String buyer = certifiedToken("fav-visibility-buyer");
        String categoryId = enabledSecondLevelCategoryId();

        String draftId = createItem(seller, uniqueTitle("草稿商品"), "30.00", categoryId);
        mockMvc.perform(put("/api/v1/items/{itemId}/favorite", draftId)
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/items/{itemId}/favorite-status", draftId)
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/items/{itemId}/favorite", draftId)
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 已删除商品同理（删除后不再对任何人可收藏）。
        String deletedId = createItem(seller, uniqueTitle("将删除商品"), "30.00", categoryId);
        mockMvc.perform(delete("/api/v1/items/{id}", deletedId).header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/items/{itemId}/favorite", deletedId)
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 不存在的商品。
        mockMvc.perform(put("/api/v1/items/{itemId}/favorite", "999999999")
                        .header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM favorites WHERE user_id = ?",
                Integer.class, Long.valueOf(userIdOf(login("fav-visibility-buyer"))))).isZero();
    }

    @Test
    @DisplayName("收藏接口都需要登录：favorite-status 是两段路径，不能被匿名放行")
    void favoriteEndpointsRequireAuthentication() throws Exception {
        String seller = certifiedToken("fav-auth-seller");
        String itemId = publish(seller, uniqueTitle("鉴权边界商品"), "40.00");

        // 详情是契约里标注 security: [] 的公开接口。
        mockMvc.perform(get("/api/v1/items/{id}", itemId))
                .andExpect(status().isOk());

        // 搜索公开，但收藏相关的一律 401。
        mockMvc.perform(get("/api/v1/items").param("keyword", "鉴权边界"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/items/{itemId}/favorite-status", itemId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        mockMvc.perform(put("/api/v1/items/{itemId}/favorite", itemId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        mockMvc.perform(delete("/api/v1/items/{itemId}/favorite", itemId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/users/me/favorites"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("账号被禁用后收藏读写立即 403 USER_DISABLED")
    void disabledUserCannotFavorite() throws Exception {
        String seller = certifiedToken("fav-disabled-seller");
        String itemId = publish(seller, uniqueTitle("禁用账号收藏"), "50.00");

        String loginJson = login("fav-disabled-buyer");
        String token = accessTokenOf(loginJson);
        String userId = userIdOf(loginJson);

        mockMvc.perform(put("/api/v1/items/{itemId}/favorite", itemId)
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", Long.valueOf(userId));

        mockMvc.perform(put("/api/v1/items/{itemId}/favorite", itemId)
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_DISABLED"));
        mockMvc.perform(get("/api/v1/users/me/favorites").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_DISABLED"));
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    /** 建草稿并上架，返回商品 ID。 */
    private String publish(String seller, String title, String price) throws Exception {
        String itemId = createItem(seller, title, price, enabledSecondLevelCategoryId());
        mockMvc.perform(post("/api/v1/items/{id}/publish", itemId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
        return itemId;
    }

    /** 建草稿，返回商品 ID。 */
    private String createItem(String seller, String title, String price, String categoryId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/items")
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createItemBody(title, price, categoryId, List.of(uploadItemImage(seller)))))
                .andExpect(status().isCreated())
                .andReturn();
        return readString(result.getResponse().getContentAsString(), "$.data.id");
    }
}
