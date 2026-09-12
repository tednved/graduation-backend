package com.graduation.backend;

import com.graduation.backend.support.RealMySqlTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 「登录 → 资料 → 认证 → 分类」闭环的真实 MySQL 端到端用例。
 *
 * <p>走真实的安全过滤链与真实 MySQL：登录用 Mock 微信客户端，鉴权用真实签发的 JWT，
 * 表结构由 Flyway 迁移而来、由 {@code ddl-auto=validate} 校验。
 *
 * <p>每个用例使用独立的 {@code deviceId}（同一设备标识下的登录会复用同一用户），
 * 因此用例之间互不干扰；管理端用例另建一个管理员账号（登录后把 {@code role} 改成 {@code ADMIN}），
 * 用来验证「权限每次请求重新读库、提权立即生效」。
 */
@EnabledIf("databaseConfigured")
class CoreApiFlowTests extends RealMySqlTestBase {

    private static final String AUTHORIZATION = "Authorization";
    private static final byte[] PNG_HEADER = {
            (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0D,
            (byte) 'I', (byte) 'H', (byte) 'D', (byte) 'R'
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 单校区下唯一种子校区（{@code code=MAIN}）的 ID。
     *
     * <p>契约的 {@code CampusRef} 只暴露 {@code id} 与 {@code name}，不含 {@code code}，
     * 因此「绑定到 MAIN」只能用库里 MAIN 的 ID 来证明。
     */
    private String mainCampusId() {
        return jdbcTemplate.queryForObject("SELECT id FROM campuses WHERE code = 'MAIN'", String.class);
    }

    // ------------------------------------------------------------------
    // 登录与资料
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Mock 登录签发令牌对，并在库内创建绑定 MAIN 校区的 ACTIVE/USER 用户")
    void loginCreatesActiveUserOnMainCampus() throws Exception {
        String loginJson = login("flow-login");

        assertThat(readString(loginJson, "$.data.tokenType")).isEqualTo("Bearer");
        assertThat(readInt(loginJson, "$.data.expiresIn")).isEqualTo(900);
        assertThat(readInt(loginJson, "$.data.refreshExpiresIn")).isEqualTo(2592000);
        assertThat(readString(loginJson, "$.data.certificationStatus")).isEqualTo("NOT_SUBMITTED");
        assertThat(readString(loginJson, "$.data.user.id")).isNotBlank();

        mockMvc.perform(get("/api/v1/users/me").header(AUTHORIZATION, bearer(accessTokenOf(loginJson))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.certificationStatus").value("NOT_SUBMITTED"))
                .andExpect(jsonPath("$.data.campus.id").value(mainCampusId()))
                .andExpect(jsonPath("$.data.averageRating").value("0.00"))
                .andExpect(jsonPath("$.data.reviewCount").value(0))
                // version 是资料乐观锁版本，不是业务字段；登录写 lastLoginAt 会把新用户推到 1。
                .andExpect(jsonPath("$.data.version").value(greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.nickname").isNotEmpty());
    }

    @Test
    @DisplayName("修改资料只影响昵称、头像与手机号，契约外的字段被拒绝")
    void profileUpdateIsLimitedToAllowedFields() throws Exception {
        String loginJson = login("flow-profile");
        String token = accessTokenOf(loginJson);
        String userId = readString(loginJson, "$.data.user.id");

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"  小明  \",\"phone\":\"13800001111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("小明"))
                .andExpect(jsonPath("$.data.phone").value("13800001111"))
                .andExpect(jsonPath("$.data.campus.id").value(mainCampusId()));

        // 未开放的身份字段由 fail-on-unknown-properties 拦成 400，而不是被静默忽略。
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 提权尝试失败后角色保持 USER。
        mockMvc.perform(get("/api/v1/users/me").header(AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.data.role").value("USER"));

        // 公开资料匿名可读，且不含手机号。
        mockMvc.perform(get("/api/v1/users/{id}/public", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("小明"))
                .andExpect(jsonPath("$.data.phone").doesNotExist());
    }

    // ------------------------------------------------------------------
    // 认证主链路
    // ------------------------------------------------------------------

    @Test
    @DisplayName("提交 MANUAL 认证返回脱敏值，管理员通过后用户认证状态同步为 APPROVED")
    void certificationApproveFlowSyncsUserStatus() throws Exception {
        String loginJson = login("flow-cert-approve");
        String token = accessTokenOf(loginJson);
        String userId = readString(loginJson, "$.data.user.id");

        mockMvc.perform(post("/api/v1/certifications")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("MANUAL"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.realNameMasked").value("张*"))
                .andExpect(jsonPath("$.data.studentNoMasked").value("20******34"))
                .andExpect(jsonPath("$.data.campus.id").value(mainCampusId()))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.reviewedAt").value(nullValue()));

        mockMvc.perform(get("/api/v1/certifications/me/latest").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.application.status").value("PENDING"));

        // 同一用户只能有一个 PENDING。
        mockMvc.perform(post("/api/v1/certifications")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERTIFICATION_PENDING_EXISTS"));

        String adminToken = adminToken();
        String certificationId = pendingCertificationIdOf(adminToken, userId);

        // 非管理员不能审核。
        mockMvc.perform(post("/api/v1/admin/certifications/{id}/approve", certificationId)
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        mockMvc.perform(post("/api/v1/admin/certifications/{id}/approve", certificationId)
                        .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/users/me").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificationStatus").value("APPROVED"));

        // 重复审核被拒绝。
        mockMvc.perform(post("/api/v1/admin/certifications/{id}/approve", certificationId)
                        .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CERTIFICATION_ALREADY_REVIEWED"));

        // 已完成的申请不再计入 PENDING 列表。
        assertThat(pendingCertificationIds(adminToken)).doesNotContain(certificationId);
    }

    @Test
    @DisplayName("驳回必须带 2~200 字原因，并同步用户认证状态为 REJECTED")
    void certificationRejectFlowRecordsReason() throws Exception {
        String loginJson = login("flow-cert-reject");
        String token = accessTokenOf(loginJson);
        String userId = readString(loginJson, "$.data.user.id");
        String adminToken = adminToken();

        mockMvc.perform(post("/api/v1/certifications")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody()))
                .andExpect(status().isCreated());

        String certificationId = pendingCertificationIdOf(adminToken, userId);

        mockMvc.perform(post("/api/v1/admin/certifications/{id}/reject", certificationId)
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"学号信息无法核实\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectReason").value("学号信息无法核实"));

        mockMvc.perform(get("/api/v1/users/me").header(AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.data.certificationStatus").value("REJECTED"));

        // 驳回后可以重新提交，latest 反映最新一条。
        mockMvc.perform(post("/api/v1/certifications")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(get("/api/v1/certifications/me/latest").header(AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    @DisplayName("从未提交认证时返回 200 与 NOT_SUBMITTED，而不是 404")
    void latestCertificationIsNotSubmittedWhenAbsent() throws Exception {
        String token = accessTokenOf(login("flow-cert-none"));

        mockMvc.perform(get("/api/v1/certifications/me/latest").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_SUBMITTED"))
                .andExpect(jsonPath("$.data.application").value(nullValue()));
    }

    @Test
    @DisplayName("请求体不合法时按 400 拒绝，且不落库")
    void certificationRequestIsValidated() throws Exception {
        String loginJson = login("flow-cert-invalid");
        String token = accessTokenOf(loginJson);
        String userId = readString(loginJson, "$.data.user.id");

        // realName 只有 1 个字符，不满足 2~30。
        mockMvc.perform(post("/api/v1/certifications")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MANUAL\",\"realName\":\"张\",\"studentNo\":\"2021001234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 契约外的认证类型。
        mockMvc.perform(post("/api/v1/certifications")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"AUTO\",\"realName\":\"张三\",\"studentNo\":\"2021001234\"}"))
                .andExpect(status().isBadRequest());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM certifications WHERE user_id = ?", Integer.class, Long.valueOf(userId)))
                .isZero();
    }

    // ------------------------------------------------------------------
    // 失败的边界
    // ------------------------------------------------------------------

    @Test
    @DisplayName("无令牌或令牌无效时返回 401 AUTH_UNAUTHORIZED")
    void protectedEndpointRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

        mockMvc.perform(get("/api/v1/users/me").header(AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("账号被禁用后，既有令牌立即失效并返回 403 USER_DISABLED")
    void disabledUserIsRejectedImmediately() throws Exception {
        String loginJson = login("flow-disabled");
        String token = accessTokenOf(loginJson);
        Long userId = Long.valueOf(readString(loginJson, "$.data.user.id"));

        mockMvc.perform(get("/api/v1/users/me").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        jdbcTemplate.update("UPDATE users SET status = 'DISABLED' WHERE id = ?", userId);

        mockMvc.perform(get("/api/v1/users/me").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_DISABLED"));

        mockMvc.perform(get("/api/v1/certifications/me/latest").header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_DISABLED"));
    }

    @Test
    @DisplayName("刷新令牌轮换：重放旧令牌返回 401，并连带吊销该设备整条令牌链")
    void refreshRotationDetectsReplayAndRevokesDeviceChain() throws Exception {
        String deviceId = device();
        String loginJson = login("flow-refresh", deviceId);
        String firstRefresh = readString(loginJson, "$.data.refreshToken");
        String accessToken = accessTokenOf(loginJson);

        MvcResult rotated = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(firstRefresh, deviceId)))
                .andExpect(status().isOk())
                .andReturn();
        String rotatedRefresh = readString(rotated.getResponse().getContentAsString(), "$.data.refreshToken");
        assertThat(rotatedRefresh).isNotEqualTo(firstRefresh);

        // 重放已被轮换掉的旧令牌。
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(firstRefresh, deviceId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));

        // 重放发生后，该设备最新签发的令牌也必须失效，否则攻击者可以继续续期。
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(rotatedRefresh, deviceId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));

        // 换设备同样无效。
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(rotatedRefresh, device())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));

        // Access Token 是无状态的，在 15 分钟窗口内仍然有效——这是有意的权衡。
        mockMvc.perform(get("/api/v1/users/me").header(AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("登出吊销刷新令牌且可重复调用，之后刷新返回 401")
    void logoutRevokesRefreshTokenIdempotently() throws Exception {
        String deviceId = device();
        String loginJson = login("flow-logout", deviceId);
        String refreshToken = readString(loginJson, "$.data.refreshToken");
        String accessToken = accessTokenOf(loginJson);
        String body = "{\"refreshToken\":\"" + refreshToken + "\"}";

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken, deviceId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
    }

    // ------------------------------------------------------------------
    // 分类与文件
    // ------------------------------------------------------------------

    @Test
    @DisplayName("分类树匿名可读，恰好两级且二级节点不带 children")
    void categoryTreeHasExactlyTwoLevels() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/categories/tree"))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> categories =
                JsonPath.read(result.getResponse().getContentAsString(), "$.data.categories");
        assertThat(categories).hasSize(5);
        assertThat(categories.stream().mapToLong(node -> childrenOf(node).size()).sum()).isEqualTo(7);
        assertThat(categories).allSatisfy(node -> {
            assertThat(node.get("parentId")).isNull();
            assertThat(node).containsKey("children");
            childrenOf(node).forEach(child -> {
                assertThat(child).doesNotContainKey("children");
                assertThat(child.get("parentId")).isNotNull();
                assertThat(child.get("status")).isEqualTo("ENABLED");
            });
        });
    }

    @Test
    @DisplayName("三级分类与同级重名都被拒绝，二级分类可正常新建")
    void thirdLevelCategoryIsRejected() throws Exception {
        String adminToken = adminToken();
        String name = "闭环测试分类" + UUID.randomUUID().toString().substring(0, 6);

        MvcResult created = mockMvc.perform(post("/api/v1/admin/categories")
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"parentId\":null,\"sortNo\":99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.parentId").value(nullValue()))
                .andReturn();
        String rootId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        MvcResult child = mockMvc.perform(post("/api/v1/admin/categories")
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "-子\",\"parentId\":" + rootId + ",\"sortNo\":1}"))
                .andExpect(status().isCreated())
                .andReturn();
        String levelTwoId = JsonPath.read(child.getResponse().getContentAsString(), "$.data.id");

        // 三级分类越界。
        mockMvc.perform(post("/api/v1/admin/categories")
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "-孙\",\"parentId\":" + levelTwoId + ",\"sortNo\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 同级重名：契约给出了唯一性约束但没有对应 409 错误码，这里按 400 处理。
        mockMvc.perform(post("/api/v1/admin/categories")
                        .header(AUTHORIZATION, bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "-子\",\"parentId\":" + rootId + ",\"sortNo\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("头像上传：非图片与超大文件被拒，合法 PNG 可绑定并返回受控 URL")
    void avatarUploadEnforcesTypeAndSize() throws Exception {
        String token = accessTokenOf(login("flow-file"));

        // 文件名与 Content-Type 都伪装成 PNG，也必须按魔数拒绝。
        MockMultipartFile fake = new MockMultipartFile("file", "avatar.png", "image/png",
                "这不是图片".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/files").file(fake).param("bizType", "AVATAR")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_INVALID_TYPE"));

        // 头像上限 2MB。
        byte[] oversize = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(PNG_HEADER, 0, oversize, 0, PNG_HEADER.length);
        MockMultipartFile tooLarge = new MockMultipartFile("file", "big.png", "image/png", oversize);
        mockMvc.perform(multipart("/api/v1/files").file(tooLarge).param("bizType", "AVATAR")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

        // MVP 不接受认证证据文件。
        MockMultipartFile evidence = new MockMultipartFile("file", "proof.png", "image/png", validPng());
        mockMvc.perform(multipart("/api/v1/files").file(evidence).param("bizType", "CERTIFICATION_EVIDENCE")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_INVALID_TYPE"));

        MockMultipartFile valid = new MockMultipartFile("file", "avatar.png", "image/png", validPng());
        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/files").file(valid).param("bizType", "AVATAR")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.bizType").value("AVATAR"))
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andReturn();
        String uploadedJson = uploaded.getResponse().getContentAsString();
        String fileId = readString(uploadedJson, "$.data.fileId");
        // 只返回受控地址，绝不暴露对象存储内部路径。
        assertThat(readString(uploadedJson, "$.data.url")).isEqualTo("/media/" + fileId);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarFileId\":" + fileId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatarUrl").value("/media/" + fileId));

        mockMvc.perform(get("/media/{fileId}", fileId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("禁止绑定他人上传的头像文件")
    void avatarCannotBeBoundByAnotherUser() throws Exception {
        String ownerToken = accessTokenOf(login("flow-file-owner"));
        String intruderToken = accessTokenOf(login("flow-file-intruder"));

        MockMultipartFile valid = new MockMultipartFile("file", "avatar.png", "image/png", validPng());
        MvcResult uploaded = mockMvc.perform(multipart("/api/v1/files").file(valid).param("bizType", "AVATAR")
                        .header(AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isCreated())
                .andReturn();
        String fileId = readString(uploaded.getResponse().getContentAsString(), "$.data.fileId");

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(AUTHORIZATION, bearer(intruderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatarFileId\":" + fileId + "}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILE_NOT_OWNED"));
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    /** 用固定 code 登录：Mock 客户端的 openid 由 code 决定，因此同一 code 总是同一个用户。 */
    private String login(String code) throws Exception {
        return login(code, "device-" + code);
    }

    private String login(String code, String deviceId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"deviceId\":\"" + deviceId + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    /** 每次调用生成新的设备标识，用于验证「换设备也刷新不了」。 */
    private String device() {
        return "device-" + UUID.randomUUID().toString().substring(0, 12);
    }

    /** 建一个管理员：先按普通用户登录建号，再直接提权。 */
    private String adminToken() throws Exception {
        String loginJson = login("flow-admin");
        Long userId = Long.valueOf(readString(loginJson, "$.data.user.id"));
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", userId);
        return accessTokenOf(loginJson);
    }

    private String accessTokenOf(String loginJson) {
        return readString(loginJson, "$.data.accessToken");
    }

    private String pendingCertificationIdOf(String adminToken, String userId) throws Exception {
        return certificationEntries(adminToken, "PENDING").stream()
                .filter(entry -> entry.userId().equals(userId))
                .map(CertificationEntry::id)
                .findFirst()
                .orElseThrow(() -> new AssertionError("管理端列表中没有属于用户 " + userId + " 的 PENDING 申请"));
    }

    private List<String> pendingCertificationIds(String adminToken) throws Exception {
        return certificationEntries(adminToken, "PENDING").stream().map(CertificationEntry::id).toList();
    }

    private List<CertificationEntry> certificationEntries(String adminToken, String status) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/certifications")
                        .param("page", "0").param("size", "100").param("status", status)
                        .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> items =
                JsonPath.read(result.getResponse().getContentAsString(), "$.data.items");
        return items.stream()
                .map(item -> new CertificationEntry(
                        String.valueOf(item.get("id")), String.valueOf(item.get("userId"))))
                .toList();
    }

    private record CertificationEntry(String id, String userId) {
    }

    private String submitBody() {
        return "{\"type\":\"MANUAL\",\"realName\":\"张三\",\"studentNo\":\"2021001234\"}";
    }

    private String refreshBody(String refreshToken, String deviceId) {
        return "{\"refreshToken\":\"" + refreshToken + "\",\"deviceId\":\"" + deviceId + "\"}";
    }

    private byte[] validPng() {
        byte[] content = new byte[64];
        Arrays.fill(content, (byte) 0x20);
        System.arraycopy(PNG_HEADER, 0, content, 0, PNG_HEADER.length);
        return content;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> childrenOf(Map<String, Object> node) {
        Object children = node.get("children");
        return children == null ? List.of() : (List<Map<String, Object>>) children;
    }

    private static String readString(String json, String path) {
        return JsonPath.read(json, path);
    }

    private static int readInt(String json, String path) {
        return ((Number) JsonPath.read(json, path)).intValue();
    }
}
