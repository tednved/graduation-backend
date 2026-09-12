package com.graduation.backend.item;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品发布/编辑/上下架/详情/搜索的真实 MySQL 端到端用例。
 *
 * <p>覆盖主链路与边界：认证前置、校区自动绑定、图片 1~9 张与归属校验、二级分类校验、
 * 版本冲突、状态机、管理员强制下架与审计、搜索过滤。
 *
 * <p>编辑语义按契约中更宽容的那一处（{@code openapi.yaml:3068}「字段省略表示保留原值」）实现，
 * 因此这里断言「只带 version 的 PUT 保留全部原值」「显式 null 仅 originalPrice 有意义」。
 */
@EnabledIf("databaseConfigured")
class ItemApiFlowTests extends ItemTestSupport {

    // ------------------------------------------------------------------
    // 主链路
    // ------------------------------------------------------------------

    @Test
    @DisplayName("创建草稿自动绑定 MAIN 校区并按入参顺序生成图片 sortNo，上架后可按关键词搜到")
    void createPublishAndSearchFlow() throws Exception {
        String seller = certifiedToken("item-flow-seller");
        String title = uniqueTitle("九成新自行车");
        String categoryId = enabledSecondLevelCategoryId();
        String firstImage = uploadItemImage(seller);
        String secondImage = uploadItemImage(seller);

        MvcResult created = mockMvc.perform(post("/api/v1/items")
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createItemBody(title, "1234.50", categoryId, List.of(firstImage, secondImage))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.title").value(title))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.condition").value("GOOD"))
                .andExpect(jsonPath("$.data.adminLock").value(false))
                .andExpect(jsonPath("$.data.publishedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.favoriteCount").value(0))
                .andExpect(jsonPath("$.data.viewCount").value(0))
                .andExpect(jsonPath("$.data.seller.id").isNotEmpty())
                // 单校区 MVP：请求里没有 campusId，服务端绑定 code=MAIN。
                .andExpect(jsonPath("$.data.campus.id").value(mainCampusId()))
                .andExpect(jsonPath("$.data.category.id").value(categoryId))
                .andExpect(jsonPath("$.data.images", hasSize(2)))
                .andExpect(jsonPath("$.data.images[0].fileId").value(firstImage))
                .andExpect(jsonPath("$.data.images[0].sortNo").value(1))
                .andExpect(jsonPath("$.data.images[0].url").value("/media/" + firstImage))
                .andExpect(jsonPath("$.data.images[1].sortNo").value(2))
                .andExpect(jsonPath("$.data.allowedActions", contains("EDIT", "PUBLISH", "DELETE")))
                .andExpect(jsonPath("$.data.isOwner").value(true))
                .andExpect(jsonPath("$.data.favorited").value(false))
                .andExpect(jsonPath("$.data.canBuy").value(false))
                .andReturn();
        String itemId = readString(created.getResponse().getContentAsString(), "$.data.id");

        // 草稿不进搜索。
        mockMvc.perform(get("/api/v1/items").param("keyword", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.items", hasSize(0)));

        MvcResult published = mockMvc.perform(post("/api/v1/items/{id}/publish", itemId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.allowedActions", contains("OFF_SHELF")))
                .andReturn();
        assertThat(readInt(published.getResponse().getContentAsString(), "$.data.version"))
                .isGreaterThanOrEqualTo(0);

        // 匿名搜索命中，卡片只带首图与校区/分类名。
        mockMvc.perform(get("/api/v1/items").param("keyword", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].id").value(itemId))
                .andExpect(jsonPath("$.data.items[0].status").value("ON_SALE"))
                .andExpect(jsonPath("$.data.items[0].price").value("1234.50"))
                .andExpect(jsonPath("$.data.items[0].coverImageUrl").value("/media/" + firstImage))
                .andExpect(jsonPath("$.data.items[0].campus.id").value(mainCampusId()))
                .andExpect(jsonPath("$.data.items[0].campus.name").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].category.name").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].publishedAt").isNotEmpty());
    }

    @Test
    @DisplayName("详情的个性化字段按查看者身份计算，匿名拿不到 isOwner/canBuy 且 favorited 为 null")
    void detailIsPersonalizedPerViewer() throws Exception {
        String seller = certifiedToken("item-detail-seller");
        String buyer = certifiedToken("item-detail-buyer");
        String stranger = accessTokenOf(login("item-detail-stranger"));
        String itemId = publishDraft(seller, "全新机械键盘", "399.00");

        mockMvc.perform(get("/api/v1/items/{id}", itemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isOwner").value(false))
                .andExpect(jsonPath("$.data.canBuy").value(false))
                .andExpect(jsonPath("$.data.favorited").value(nullValue()))
                .andExpect(jsonPath("$.data.allowedActions", hasSize(0)));

        // 未认证用户即使登录也不能下单，但可以收藏。
        mockMvc.perform(get("/api/v1/items/{id}", itemId).header(AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canBuy").value(false))
                .andExpect(jsonPath("$.data.allowedActions", contains("FAVORITE")))
                .andExpect(jsonPath("$.data.favorited").value(false));

        mockMvc.perform(get("/api/v1/items/{id}", itemId).header(AUTHORIZATION, bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isOwner").value(false))
                .andExpect(jsonPath("$.data.canBuy").value(true))
                .andExpect(jsonPath("$.data.allowedActions", contains("FAVORITE", "BUY")));

        // 卖家看自己的在售商品：不能买自己的，只能下架。
        mockMvc.perform(get("/api/v1/items/{id}", itemId).header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isOwner").value(true))
                .andExpect(jsonPath("$.data.canBuy").value(false))
                .andExpect(jsonPath("$.data.allowedActions", contains("OFF_SHELF")));
    }

    @Test
    @DisplayName("每次读详情自增浏览量，且详情本身不受自增事务影响")
    void detailIncrementsViewCount() throws Exception {
        String seller = certifiedToken("item-view-seller");
        String itemId = publishDraft(seller, "浏览量测试商品", "10.00");

        int first = readInt(mockMvc.perform(get("/api/v1/items/{id}", itemId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.viewCount");
        int second = readInt(mockMvc.perform(get("/api/v1/items/{id}", itemId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data.viewCount");

        assertThat(second).isGreaterThan(first);
        // 第 2 次详情读到的值不含本次自增，落库值应为它 +1，证明自增真的提交了。
        assertThat(jdbcTemplate.queryForObject(
                "SELECT view_count FROM items WHERE id = ?", Integer.class, Long.valueOf(itemId)))
                .isEqualTo(second + 1);
    }

    // ------------------------------------------------------------------
    // 创建前置与参数
    // ------------------------------------------------------------------

    @Test
    @DisplayName("创建商品需要登录且认证通过：匿名 401、未认证 403、被禁用 403")
    void createRequiresCertifiedActiveUser() throws Exception {
        String categoryId = enabledSecondLevelCategoryId();
        String body = createItemBody(uniqueTitle("前置校验"), "20.00", categoryId, List.of("1"));

        mockMvc.perform(post("/api/v1/items")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

        String loginJson = login("item-flow-uncertified");
        String token = accessTokenOf(loginJson);
        String userId = userIdOf(loginJson);
        mockMvc.perform(post("/api/v1/items")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_CERTIFICATION_REQUIRED"));

        jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", Long.valueOf(userId));
        mockMvc.perform(post("/api/v1/items")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_DISABLED"));
    }

    @Test
    @DisplayName("图片必须是 1~9 张本人上传且未被占用的 ITEM_IMAGE，失败时不落任何数据")
    void createValidatesImages() throws Exception {
        String seller = certifiedToken("item-image-seller");
        String other = certifiedToken("item-image-other");
        String categoryId = enabledSecondLevelCategoryId();
        String ownImage = uploadItemImage(seller);
        String foreignImage = uploadItemImage(other);
        String avatar = uploadAvatar(seller);
        Long maxItemIdBefore = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(id), 0) FROM items", Long.class);

        // 0 张。
        mockMvc.perform(createItemRequest(seller, uniqueTitle("无图"), "30.00", categoryId, List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 10 张。
        List<String> tenImages = IntStream.range(0, 10)
                .mapToObj(index -> String.valueOf(100000L + index)).toList();
        mockMvc.perform(createItemRequest(seller, uniqueTitle("十图"), "30.00", categoryId, tenImages))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 同一张图重复引用会撞 (item_id, file_object_id) 唯一键，必须提前拦成 400。
        mockMvc.perform(createItemRequest(seller, uniqueTitle("重复图"), "30.00", categoryId,
                        List.of(ownImage, ownImage)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 他人上传的文件。
        mockMvc.perform(createItemRequest(seller, uniqueTitle("他人图"), "30.00", categoryId,
                        List.of(foreignImage)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_NOT_OWNED"));

        // 类型不符（头像不是商品图片）。
        mockMvc.perform(createItemRequest(seller, uniqueTitle("错类型"), "30.00", categoryId, List.of(avatar)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_INVALID_TYPE"));

        // 文件不存在。
        mockMvc.perform(createItemRequest(seller, uniqueTitle("文件不存在"), "30.00", categoryId,
                        List.of("999999999")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 契约外的字段被 fail-on-unknown-properties 拒绝：请求里不能出现 campusId。
        mockMvc.perform(post("/api/v1/items")
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"带校区的请求\",\"description\":\"" + VALID_DESCRIPTION
                                + "\",\"price\":\"30.00\",\"condition\":\"GOOD\",\"categoryId\":" + categoryId
                                + ",\"imageFileIds\":[" + ownImage + "],\"campusId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 校验全部发生在写库之前：一条商品都没落库，文件也仍然是 UPLOADED。
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM items WHERE id > ?", Integer.class, maxItemIdBefore)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM file_objects WHERE id = ?",
                String.class, Long.valueOf(ownImage))).isEqualTo("UPLOADED");

        // 已被第一个商品绑定的文件不能再被第二个商品引用。
        createDraft(seller, uniqueTitle("图片占用"), "30.00", categoryId, List.of(ownImage));
        mockMvc.perform(createItemRequest(seller, uniqueTitle("二次占用"), "30.00", categoryId,
                        List.of(ownImage)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM file_objects WHERE id = ?",
                String.class, Long.valueOf(ownImage))).isEqualTo("BOUND");
    }

    @Test
    @DisplayName("分类必须是启用的二级分类：不存在 404，一级或已停用 400")
    void createValidatesCategory() throws Exception {
        String seller = certifiedToken("item-category-seller");
        String adminToken = adminToken();

        mockMvc.perform(createItemRequest(seller, uniqueTitle("分类不存在"), "30.00", "999999999",
                        List.of(uploadItemImage(seller))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 一级分类不能直接挂商品。
        mockMvc.perform(createItemRequest(seller, uniqueTitle("一级分类"), "30.00", levelOneCategoryId(),
                        List.of(uploadItemImage(seller))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 新建并停用一个二级分类，用它创建商品必须是 400。
        // 这里只用测试自建的分类，避免改动种子分类影响其它用例。
        String disabledChildId = disableSecondLevelCategory(adminToken);
        mockMvc.perform(createItemRequest(seller, uniqueTitle("停用分类"), "30.00", disabledChildId,
                        List.of(uploadItemImage(seller))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 已绑定启用分类的草稿，在分类被停用后不能上架。
        String childId = createSecondLevelCategory(adminToken);
        String itemId = createDraft(seller, uniqueTitle("分类停用后上架"), "30.00", childId,
                List.of(uploadItemImage(seller)));
        mockMvc.perform(post("/api/v1/admin/categories/{id}/disable", childId)
                        .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/items/{id}/publish", itemId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_EDITABLE"));
    }

    @Test
    @DisplayName("金额与文本按契约校验：格式、范围、原价不得低于售价")
    void createValidatesMoneyAndText() throws Exception {
        String seller = certifiedToken("item-money-seller");
        String categoryId = enabledSecondLevelCategoryId();
        String image = uploadItemImage(seller);

        // 一位小数（不是两位）与整数都不合法。
        for (String price : List.of("30.0", "30", "30.000", "-30.00", "abc")) {
            mockMvc.perform(createItemRequest(seller, uniqueTitle("价格格式"), price, categoryId,
                            List.of(image)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        // 超出 DECIMAL(10,2) 上限。
        mockMvc.perform(createItemRequest(seller, uniqueTitle("价格超限"), "100000000.00", categoryId,
                        List.of(image)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 原价低于售价。
        mockMvc.perform(post("/api/v1/items")
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + uniqueTitle("原价倒挂")
                                + "\",\"description\":\"" + VALID_DESCRIPTION
                                + "\",\"price\":\"100.00\",\"originalPrice\":\"99.99\",\"condition\":\"GOOD\""
                                + ",\"categoryId\":" + categoryId + ",\"imageFileIds\":[" + image + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 标题过短。
        mockMvc.perform(post("/api/v1/items")
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"短\",\"description\":\"" + VALID_DESCRIPTION
                                + "\",\"price\":\"30.00\",\"condition\":\"GOOD\""
                                + ",\"categoryId\":" + categoryId + ",\"imageFileIds\":[" + image + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 成色必须是契约枚举。
        mockMvc.perform(post("/api/v1/items")
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + uniqueTitle("成色非法")
                                + "\",\"description\":\"" + VALID_DESCRIPTION
                                + "\",\"price\":\"30.00\",\"condition\":\"ALMOST_NEW\""
                                + ",\"categoryId\":" + categoryId + ",\"imageFileIds\":[" + image + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ------------------------------------------------------------------
    // 编辑
    // ------------------------------------------------------------------

    @Test
    @DisplayName("省略字段保留原值、显式 null 只对原价有效、version 不匹配 409")
    void updateFollowsOmittedFieldsSemantics() throws Exception {
        String seller = certifiedToken("item-edit-seller");
        String categoryId = enabledSecondLevelCategoryId();
        String image = uploadItemImage(seller);
        String created = createDraftWithOriginalPrice(seller, "可编辑商品", "200.00", "300.00", categoryId, image);
        String itemId = readString(created, "$.data.id");
        int version = readInt(created, "$.data.version");

        // 只带 version：其余字段全部保持原值。
        MvcResult untouched = mockMvc.perform(put("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("可编辑商品"))
                .andExpect(jsonPath("$.data.price").value("200.00"))
                .andExpect(jsonPath("$.data.originalPrice").value("300.00"))
                .andExpect(jsonPath("$.data.condition").value("GOOD"))
                .andExpect(jsonPath("$.data.category.id").value(categoryId))
                .andExpect(jsonPath("$.data.images", hasSize(1)))
                .andExpect(jsonPath("$.data.images[0].fileId").value(image))
                .andReturn();
        int afterNoop = readInt(untouched.getResponse().getContentAsString(), "$.data.version");
        assertThat(afterNoop).isGreaterThan(version);

        // 部分字段更新：只改标题，价格与原价不动。
        MvcResult partiallyUpdated = mockMvc.perform(put("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + afterNoop + ",\"title\":\"改过的标题\",\"price\":\"188.00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("改过的标题"))
                .andExpect(jsonPath("$.data.price").value("188.00"))
                .andExpect(jsonPath("$.data.originalPrice").value("300.00"))
                .andReturn();
        int afterUpdate = readInt(partiallyUpdated.getResponse().getContentAsString(), "$.data.version");

        // 显式 null 的 originalPrice 表示清除。
        MvcResult cleared = mockMvc.perform(put("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + afterUpdate + ",\"originalPrice\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalPrice").value(nullValue()))
                .andExpect(jsonPath("$.data.price").value("188.00"))
                .andExpect(jsonPath("$.data.title").value("改过的标题"))
                .andReturn();
        int afterClear = readInt(cleared.getResponse().getContentAsString(), "$.data.version");

        // 其余字段显式 null 一律 400（不能靠 null 绕过必填）。
        mockMvc.perform(put("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + afterClear + ",\"title\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 缺 version。
        mockMvc.perform(put("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"另一个标题\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 版本落后于库中值：409 ITEM_NOT_EDITABLE，而不是乐观锁 500。
        mockMvc.perform(put("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":99999,\"title\":\"过期版本\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_EDITABLE"));

        // 图片替换：新图绑定、sortNo 从 1 重新开始、被移除的图解绑。
        String replacement = uploadItemImage(seller);
        MvcResult replaced = mockMvc.perform(put("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + afterClear + ",\"imageFileIds\":[" + replacement + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images", hasSize(1)))
                .andExpect(jsonPath("$.data.images[0].fileId").value(replacement))
                .andExpect(jsonPath("$.data.images[0].sortNo").value(1))
                .andReturn();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM file_objects WHERE id = ?",
                String.class, Long.valueOf(replacement))).isEqualTo("BOUND");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM file_objects WHERE id = ?",
                String.class, Long.valueOf(image))).isNotEqualTo("BOUND");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM item_images WHERE item_id = ?",
                Integer.class, Long.valueOf(itemId))).isEqualTo(1);

        // 只传图片列表（省略其它字段）同样保留原值。
        int afterReplace = readInt(replaced.getResponse().getContentAsString(), "$.data.version");
        mockMvc.perform(put("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + afterReplace + ",\"imageFileIds\":["
                                + uploadItemImage(seller) + "," + uploadItemImage(seller) + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("改过的标题"))
                .andExpect(jsonPath("$.data.price").value("188.00"))
                .andExpect(jsonPath("$.data.originalPrice").value(nullValue()))
                .andExpect(jsonPath("$.data.images", hasSize(2)))
                .andExpect(jsonPath("$.data.images[0].sortNo").value(1))
                .andExpect(jsonPath("$.data.images[1].sortNo").value(2));
    }

    @Test
    @DisplayName("只有草稿/已下架可编辑删除，在售商品返回 409 ITEM_NOT_EDITABLE")
    void editAndDeleteAreLimitedToEditableStatuses() throws Exception {
        String seller = certifiedToken("item-status-seller");
        String itemId = publishDraft(seller, "状态机商品", "88.00");

        mockMvc.perform(put("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"title\":\"在售改标题\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_EDITABLE"));

        mockMvc.perform(delete("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_EDITABLE"));

        mockMvc.perform(post("/api/v1/items/{id}/publish", itemId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_EDITABLE"));

        // 下架后可以编辑、可以重新上架（publishedAt 保留首次上架时间）。
        MvcResult offShelf = mockMvc.perform(post("/api/v1/items/{id}/off-shelf", itemId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFF_SHELF"))
                .andExpect(jsonPath("$.data.allowedActions", contains("EDIT", "PUBLISH", "DELETE")))
                .andReturn();
        int version = readInt(offShelf.getResponse().getContentAsString(), "$.data.version");

        mockMvc.perform(put("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + ",\"title\":\"下架后可改\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("下架后可改"))
                .andExpect(jsonPath("$.data.status").value("OFF_SHELF"));

        mockMvc.perform(post("/api/v1/items/{id}/publish", itemId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));

        // 草稿不能下架。
        String categoryId = enabledSecondLevelCategoryId();
        String draftId = createDraft(seller, uniqueTitle("草稿下架"), "12.00", categoryId,
                List.of(uploadItemImage(seller)));
        mockMvc.perform(post("/api/v1/items/{id}/off-shelf", draftId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_EDITABLE"));
    }

    @Test
    @DisplayName("删除是状态变更：删除后仅本人可见、不进搜索、不可再编辑")
    void deleteChangesStatusOnly() throws Exception {
        String seller = certifiedToken("item-delete-seller");
        String categoryId = enabledSecondLevelCategoryId();
        String title = uniqueTitle("待删除商品");
        String itemId = createDraft(seller, title, "66.00", categoryId, List.of(uploadItemImage(seller)));

        mockMvc.perform(delete("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isNoContent());

        // 状态变更而非物理删除。
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM items WHERE id = ?", String.class,
                Long.valueOf(itemId))).isEqualTo("DELETED");

        mockMvc.perform(get("/api/v1/items/{id}", itemId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/items/{id}", itemId).header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELETED"))
                .andExpect(jsonPath("$.data.allowedActions", hasSize(0)));

        mockMvc.perform(get("/api/v1/items").param("keyword", title))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // 删除后不能再次编辑。
        mockMvc.perform(put("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"title\":\"删除后编辑\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_EDITABLE"));

        // 删除后不能再次删除。
        mockMvc.perform(delete("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_EDITABLE"));
    }

    // ------------------------------------------------------------------
    // 可见性与归属
    // ------------------------------------------------------------------

    @Test
    @DisplayName("草稿对他人不可见：他人 404，本人与管理员 200")
    void draftIsVisibleOnlyToOwnerAndAdmin() throws Exception {
        String seller = certifiedToken("item-draft-seller");
        String other = certifiedToken("item-draft-other");
        String adminToken = adminToken();
        String categoryId = enabledSecondLevelCategoryId();
        String itemId = createDraft(seller, uniqueTitle("草稿可见性"), "45.00", categoryId,
                List.of(uploadItemImage(seller)));

        mockMvc.perform(get("/api/v1/items/{id}", itemId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/items/{id}", itemId).header(AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/items/{id}", itemId).header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        mockMvc.perform(get("/api/v1/items/{id}", itemId).header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("非卖家写入一律 403：可见商品 403，不可见商品按 404 处理")
    void writeOperationsRequireOwnership() throws Exception {
        String seller = certifiedToken("item-owner-seller");
        String intruder = certifiedToken("item-owner-intruder");
        String itemId = publishDraft(seller, "归属校验商品", "150.00");

        mockMvc.perform(put("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"title\":\"越权修改\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        mockMvc.perform(delete("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        mockMvc.perform(post("/api/v1/items/{id}/off-shelf", itemId)
                        .header(AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        mockMvc.perform(post("/api/v1/items/{id}/publish", itemId)
                        .header(AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        // 不可见资源不暴露存在性：他人对自己的草稿写入是 404。
        String categoryId = enabledSecondLevelCategoryId();
        String draftId = createDraft(seller, uniqueTitle("他人草稿"), "20.00", categoryId,
                List.of(uploadItemImage(seller)));
        mockMvc.perform(delete("/api/v1/items/{id}", draftId)
                        .header(AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 商品不存在同样 404。
        mockMvc.perform(delete("/api/v1/items/{id}", "999999999")
                        .header(AUTHORIZATION, bearer(intruder)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // ------------------------------------------------------------------
    // 管理员强制下架
    // ------------------------------------------------------------------

    @Test
    @DisplayName("管理员强制下架置 OFF_SHELF 并锁定，卖家不能改也不能重上架，同时写入审计")
    void adminOffShelfLocksItemAndWritesAudit() throws Exception {
        String seller = certifiedToken("item-admin-seller");
        String adminToken = adminToken();
        String itemId = publishDraft(seller, "强制下架商品", "520.00");

        // 原因长度 2~200。
        mockMvc.perform(post("/api/v1/admin/items/{id}/off-shelf", itemId)
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"违\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 非管理员不能强制下架。
        mockMvc.perform(post("/api/v1/admin/items/{id}/off-shelf", itemId)
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"违规下架处理\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        MvcResult result = mockMvc.perform(post("/api/v1/admin/items/{id}/off-shelf", itemId)
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"违规下架处理\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFF_SHELF"))
                .andExpect(jsonPath("$.data.adminLock").value(true))
                .andExpect(jsonPath("$.data.offShelfReason").value("违规下架处理"))
                // 管理端响应按匿名视图组装，不返回与操作者身份相关的动作。
                .andExpect(jsonPath("$.data.allowedActions", hasSize(0)))
                .andExpect(jsonPath("$.data.favorited").value(nullValue()))
                .andReturn();
        int version = readInt(result.getResponse().getContentAsString(), "$.data.version");

        // 审计落库，且带上原因。
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = ? AND target_id = ?",
                Integer.class, "ITEM_ADMIN_OFF_SHELF", Long.valueOf(itemId))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT detail_json FROM audit_logs WHERE action = ? AND target_id = ?",
                String.class, "ITEM_ADMIN_OFF_SHELF", Long.valueOf(itemId))).contains("违规下架处理");

        // 锁定后卖家不能修改、不能重上架；但契约只禁止「修改或重上架」，删除仍允许。
        mockMvc.perform(put("/api/v1/items/{id}", itemId)
                        .header(AUTHORIZATION, bearer(seller))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + ",\"title\":\"锁定后修改\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_EDITABLE"));

        mockMvc.perform(post("/api/v1/items/{id}/publish", itemId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_EDITABLE"));

        mockMvc.perform(get("/api/v1/items/{id}", itemId).header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFF_SHELF"))
                .andExpect(jsonPath("$.data.adminLock").value(true));

        // 已删除商品没有可下架的状态，返回 404。
        String categoryId = enabledSecondLevelCategoryId();
        String draftId = createDraft(seller, uniqueTitle("已删除商品"), "10.00", categoryId,
                List.of(uploadItemImage(seller)));
        mockMvc.perform(delete("/api/v1/items/{id}", draftId).header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/admin/items/{id}/off-shelf", draftId)
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"已删除商品下架\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // ------------------------------------------------------------------
    // 列表与搜索
    // ------------------------------------------------------------------

    @Test
    @DisplayName("我的发布包含全部状态并可按状态筛选，需要登录")
    void myItemsListsAllStatuses() throws Exception {
        String seller = certifiedToken("item-mine-seller");
        String categoryId = enabledSecondLevelCategoryId();
        String draftId = createDraft(seller, uniqueTitle("我的草稿"), "11.00", categoryId,
                List.of(uploadItemImage(seller)));
        String onSaleId = publishDraft(seller, uniqueTitle("我的在售"), "22.00");

        mockMvc.perform(get("/api/v1/users/me/items"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/users/me/items").param("size", "100")
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + draftId + "')]", hasSize(1)))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + onSaleId + "')].status")
                        .value(contains("ON_SALE")))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + draftId + "')].version").exists());

        mockMvc.perform(get("/api/v1/users/me/items").param("status", "DRAFT").param("size", "100")
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + draftId + "')].status")
                        .value(contains("DRAFT")))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + onSaleId + "')]", hasSize(0)));

        // 契约外的状态值按 400 拒绝。
        mockMvc.perform(get("/api/v1/users/me/items").param("status", "NOT_A_STATUS")
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("搜索支持关键词/分类/成色/价格/排序，非法参数 400")
    void searchSupportsFiltersAndValidatesInput() throws Exception {
        String seller = certifiedToken("item-search-seller");
        String categoryId = enabledSecondLevelCategoryId();
        String parentCategoryId = jdbcTemplate.queryForObject(
                "SELECT parent_id FROM categories WHERE id = ?", String.class, Long.valueOf(categoryId));
        String title = uniqueTitle("搜索过滤商品");
        String itemId = publishDraftWithPrice(seller, title, "1234.50", categoryId);

        // 关键词命中 + 分页元数据。
        mockMvc.perform(get("/api/v1/items").param("keyword", title).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        // 一级分类会展开到其二级子分类。
        mockMvc.perform(get("/api/v1/items").param("keyword", title).param("categoryId", parentCategoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // 成色与价格区间。
        mockMvc.perform(get("/api/v1/items").param("keyword", title).param("condition", "GOOD")
                        .param("minPrice", "1000.00").param("maxPrice", "2000.00"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/v1/items").param("keyword", title).param("condition", "NEW"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/items").param("keyword", title).param("minPrice", "2000.00"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/v1/items").param("keyword", title).param("maxPrice", "1000.00"))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // 排序值走白名单枚举。
        mockMvc.perform(get("/api/v1/items").param("keyword", title).param("sort", "PRICE_ASC"))
                .andExpect(jsonPath("$.data.items[0].id").value(itemId));
        mockMvc.perform(get("/api/v1/items").param("keyword", title).param("sort", "NOT_A_SORT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 未知分类返回空页而不是 404（该接口只允许 400/500）。
        mockMvc.perform(get("/api/v1/items").param("keyword", title).param("categoryId", "999999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.items", hasSize(0)));

        // % 是用户输入而不是通配符：转义后不会命中任何商品。
        mockMvc.perform(get("/api/v1/items").param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // 关键词超长、价格区间倒挂、金额格式不符都是 400。
        mockMvc.perform(get("/api/v1/items").param("keyword", "长".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/items").param("minPrice", "100.00").param("maxPrice", "10.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/items").param("minPrice", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private MockHttpServletRequestBuilder createItemRequest(
            String token, String title, String price, String categoryId, List<String> fileIds) {
        return post("/api/v1/items")
                .header(AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createItemBody(title, price, categoryId, fileIds));
    }

    private String createDraft(String token, String title, String price, String categoryId,
                               List<String> fileIds) throws Exception {
        MvcResult result = mockMvc.perform(createItemRequest(token, title, price, categoryId, fileIds))
                .andExpect(status().isCreated())
                .andReturn();
        return readString(result.getResponse().getContentAsString(), "$.data.id");
    }

    private String createDraftWithOriginalPrice(String token, String title, String price, String originalPrice,
                                               String categoryId, String fileId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/items")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"description\":\"" + VALID_DESCRIPTION
                                + "\",\"price\":\"" + price + "\",\"originalPrice\":\"" + originalPrice
                                + "\",\"condition\":\"GOOD\",\"categoryId\":" + categoryId
                                + ",\"imageFileIds\":[" + fileId + "]}"))
                .andExpect(status().isCreated())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    /** 创建一张商品图片并上架，返回商品 ID。 */
    private String publishDraft(String seller, String title, String price) throws Exception {
        return publishDraftWithPrice(seller, title, price, enabledSecondLevelCategoryId());
    }

    private String publishDraftWithPrice(String seller, String title, String price, String categoryId)
            throws Exception {
        String itemId = createDraft(seller, title, price, categoryId, List.of(uploadItemImage(seller)));
        mockMvc.perform(post("/api/v1/items/{id}/publish", itemId)
                        .header(AUTHORIZATION, bearer(seller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));
        return itemId;
    }

    /** 新建一个一级分类 + 启用的二级子分类，返回子分类 ID。 */
    private String createSecondLevelCategory(String adminToken) throws Exception {
        String name = "测试分类" + UUID.randomUUID().toString().substring(0, 6);
        MvcResult root = mockMvc.perform(post("/api/v1/admin/categories")
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"parentId\":null,\"sortNo\":98}"))
                .andExpect(status().isCreated())
                .andReturn();
        String rootId = readString(root.getResponse().getContentAsString(), "$.data.id");

        MvcResult child = mockMvc.perform(post("/api/v1/admin/categories")
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "-子\",\"parentId\":" + rootId + ",\"sortNo\":1}"))
                .andExpect(status().isCreated())
                .andReturn();
        return readString(child.getResponse().getContentAsString(), "$.data.id");
    }

    /** 新建一个二级分类并停用，返回该分类 ID。 */
    private String disableSecondLevelCategory(String adminToken) throws Exception {
        String childId = createSecondLevelCategory(adminToken);
        mockMvc.perform(post("/api/v1/admin/categories/{id}/disable", childId)
                        .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
        return childId;
    }
}
