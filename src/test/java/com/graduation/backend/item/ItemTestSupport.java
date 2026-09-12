package com.graduation.backend.item;

import com.graduation.backend.support.RealMySqlTestBase;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品/收藏端到端用例的公共脚手架。
 *
 * <p>测试用 {@code app.wechat.mock-openid} 为空，openid 由 {@code code} 派生，
 * 因此同一 {@code code} 就是同一个用户，不同 {@code code} 就是不同用户——
 * 「不能收藏自己商品」「收藏他人商品会出现在收藏列表」这类跨用户场景才能真实覆盖。
 *
 * <p>商品写接口要求认证 {@code APPROVED}，所以卖家统一用
 * {@link #certifiedToken(String)}（登录 → 提交认证 → 管理员通过）拿令牌。
 */
public abstract class ItemTestSupport extends RealMySqlTestBase {

    protected static final String AUTHORIZATION = "Authorization";

    /** 最小合法 PNG 头；文件服务按魔数识别类型，不看扩展名与 Content-Type。 */
    private static final byte[] PNG_HEADER = {
            (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A,
            (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0D,
            (byte) 'I', (byte) 'H', (byte) 'D', (byte) 'R'
    };

    /** 满足契约 minLength=10 的描述文本。 */
    protected static final String VALID_DESCRIPTION = "这是一段用于端到端测试的商品描述文本";

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected String login(String code) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/wechat-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"deviceId\":\"device-" + code + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    /** 登录并完成认证审批，返回可调用商品写接口的 access token。 */
    protected String certifiedToken(String code) throws Exception {
        String loginJson = login(code);
        String token = accessTokenOf(loginJson);
        String userId = userIdOf(loginJson);

        mockMvc.perform(post("/api/v1/certifications")
                        .header(AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MANUAL\",\"realName\":\"张三\",\"studentNo\":\"2021001234\"}"))
                .andExpect(status().isCreated());

        String adminToken = adminToken();
        String certificationId = pendingCertificationIdOf(adminToken, userId);
        mockMvc.perform(post("/api/v1/admin/certifications/{id}/approve", certificationId)
                        .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk());
        return token;
    }

    /** 建一个管理员：先按普通用户登录建号，再直接提权。 */
    protected String adminToken() throws Exception {
        String loginJson = login("item-admin");
        Long userId = Long.valueOf(userIdOf(loginJson));
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", userId);
        return accessTokenOf(loginJson);
    }

    protected String accessTokenOf(String loginJson) {
        return readString(loginJson, "$.data.accessToken");
    }

    protected String userIdOf(String loginJson) {
        return readString(loginJson, "$.data.user.id");
    }

    /** 上传一张 ITEM_IMAGE，返回 fileId。 */
    protected String uploadItemImage(String token) throws Exception {
        return uploadFile(token, "ITEM_IMAGE");
    }

    /** 上传一张 AVATAR，用于「类型不符」的负例。 */
    protected String uploadAvatar(String token) throws Exception {
        return uploadFile(token, "AVATAR");
    }

    private String uploadFile(String token, String bizType) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "upload.png", "image/png", validPng());
        MvcResult result = mockMvc.perform(multipart("/api/v1/files")
                        .file(file)
                        .param("bizType", bizType)
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isCreated())
                .andReturn();
        return readString(result.getResponse().getContentAsString(), "$.data.fileId");
    }

    /** 创建商品请求体；图片 ID 按给定顺序生成 sortNo。 */
    protected String createItemBody(String title, String price, String categoryId, List<String> fileIds) {
        return "{\"title\":\"" + title + "\","
                + "\"description\":\"" + VALID_DESCRIPTION + "\","
                + "\"price\":\"" + price + "\","
                + "\"condition\":\"GOOD\","
                + "\"categoryId\":" + categoryId + ","
                + "\"imageFileIds\":[" + String.join(",", fileIds) + "]}";
    }

    protected String uniqueTitle(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    protected String enabledSecondLevelCategoryId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE parent_id IS NOT NULL AND status = 'ENABLED' ORDER BY id LIMIT 1",
                String.class);
    }

    protected String levelOneCategoryId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM categories WHERE parent_id IS NULL ORDER BY id LIMIT 1", String.class);
    }

    protected String mainCampusId() {
        return jdbcTemplate.queryForObject("SELECT id FROM campuses WHERE code = 'MAIN'", String.class);
    }

    protected String pendingCertificationIdOf(String adminToken, String userId) throws Exception {
        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get("/api/v1/admin/certifications")
                                .param("page", "0").param("size", "100").param("status", "PENDING")
                                .header(AUTHORIZATION, bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        List<java.util.Map<String, Object>> items =
                JsonPath.read(result.getResponse().getContentAsString(), "$.data.items");
        return items.stream()
                .filter(item -> String.valueOf(item.get("userId")).equals(userId))
                .map(item -> String.valueOf(item.get("id")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("管理端列表中没有属于用户 " + userId + " 的 PENDING 申请"));
    }

    protected byte[] validPng() {
        byte[] content = new byte[64];
        Arrays.fill(content, (byte) 0x20);
        System.arraycopy(PNG_HEADER, 0, content, 0, PNG_HEADER.length);
        return content;
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected static String readString(String json, String path) {
        return JsonPath.read(json, path);
    }

    protected static int readInt(String json, String path) {
        return ((Number) JsonPath.read(json, path)).intValue();
    }
}
