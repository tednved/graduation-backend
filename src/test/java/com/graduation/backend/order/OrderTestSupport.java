package com.graduation.backend.order;

import com.graduation.backend.item.ItemTestSupport;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单/评价/消息/管理端端到端用例的公共脚手架。
 *
 * <p>继承 {@link ItemTestSupport}：下单要真实走完「认证 → 发布商品 → 上架」，
 * 用户构造与图片上传都在父类，这里只补组合动作——造一件可购买的商品、走完一笔订单、
 * 直接读库确认商品状态。
 *
 * <p>用例类不加 {@code @Transactional}：接口必须真的提交，消息监听器才在
 * {@code AFTER_COMMIT} 之后落库，因此断言的是提交后的真实状态。
 */
public abstract class OrderTestSupport extends ItemTestSupport {

    /**
     * 本轮运行的用户后缀。
     *
     * <p>真实 MySQL 用例不做事务回滚，同一个 mock code 在两次运行之间是同一个账号：
     * 上一轮攒下的订单与消息会把「恰好 N 条」这类断言撑爆。因此这里给每个登录都加上
     * 本轮唯一后缀，让用例只看到自己造的数据。
     */
    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

    /** 登录前统一派生本轮账号；管理员账号同样每轮新建，不会继承上一轮的角色与数据。 */
    @Override
    protected String login(String code) throws Exception {
        return super.login(code + "-" + RUN_ID);
    }

    /** 本轮运行后缀，供需要自己拼 mock code 的用例（例如直接打登录接口）保持一致。 */
    protected String runId() {
        return RUN_ID;
    }

    /** 已上架的测试商品。标题要留着：订单快照断言用的是下架时的商品标题。 */
    protected record PublishedItem(String itemId, String title) {
    }

    /** 认证卖家发布并上架一件商品，返回商品 ID 与标题。 */
    protected PublishedItem publishItem(String sellerToken, String price) throws Exception {
        String title = uniqueTitle("订单商品");
        String image = uploadItemImage(sellerToken);
        MvcResult created = mockMvc.perform(post("/api/v1/items")
                        .header(AUTHORIZATION, bearer(sellerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createItemBody(title, price, enabledSecondLevelCategoryId(), List.of(image))))
                .andExpect(status().isCreated())
                .andReturn();
        String itemId = readString(created.getResponse().getContentAsString(), "$.data.id");
        mockMvc.perform(post("/api/v1/items/{id}/publish", itemId)
                        .header(AUTHORIZATION, bearer(sellerToken)))
                .andExpect(status().isOk());
        return new PublishedItem(itemId, title);
    }

    /** 下单请求：状态码由调用方断言（首次 201、幂等重放 200），所以这里不设期望。 */
    protected MvcResult createOrder(String buyerToken, String itemId, String clientRequestId) throws Exception {
        return mockMvc.perform(post("/api/v1/orders")
                        .header(AUTHORIZATION, bearer(buyerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderBody(itemId, clientRequestId)))
                .andReturn();
    }

    protected String orderBody(String itemId, String clientRequestId) {
        return "{\"itemId\":" + itemId + ",\"tradeMode\":\"OFFLINE\","
                + "\"clientRequestId\":\"" + clientRequestId + "\"}";
    }

    /** 每次下单都用新的幂等键，避免上一笔订单的键把新的下单判成重放。 */
    protected String newRequestId() {
        return UUID.randomUUID().toString();
    }

    /** 新下一笔订单并走到「已完成」，返回订单 ID，供评价与消息用例起步。 */
    protected String completeOrder(String sellerToken, String buyerToken, String itemId) throws Exception {
        String body = createOrder(buyerToken, itemId, newRequestId()).getResponse().getContentAsString();
        String orderId = readString(body, "$.data.id");
        advanceToCompleted(sellerToken, buyerToken, orderId);
        return orderId;
    }

    /**
     * 把已存在的订单推到期终态。
     *
     * <p>与 {@link #completeOrder} 分开：用例常常需要先读订单详情拿到买卖双方 ID，
     * 再推进状态，重复下单会撞上商品已被预留。
     */
    protected void advanceToCompleted(String sellerToken, String buyerToken, String orderId) throws Exception {
        mockMvc.perform(post("/api/v1/orders/{id}/confirm", orderId)
                        .header(AUTHORIZATION, bearer(sellerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{id}/deliver", orderId)
                        .header(AUTHORIZATION, bearer(sellerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/orders/{id}/receive", orderId)
                        .header(AUTHORIZATION, bearer(buyerToken)))
                .andExpect(status().isOk());
    }

    protected String reasonBody(String reason) {
        return "{\"reason\":\"" + reason + "\"}";
    }

    /** 评价请求：内容为 null 时整个字段省略，用于覆盖「未填写内容」的分支。 */
    protected String reviewBody(String orderId, int rating, String content) {
        StringBuilder body = new StringBuilder("{\"orderId\":").append(orderId)
                .append(",\"rating\":").append(rating);
        if (content != null) {
            body.append(",\"content\":\"").append(content).append("\"");
        }
        return body.append("}").toString();
    }

    /** 订单详情里读对端用户 ID，省去在用例里手工推断买卖双方。 */
    protected String sellerIdOf(String viewerToken, String orderId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header(AUTHORIZATION, bearer(viewerToken)))
                .andExpect(status().isOk())
                .andReturn();
        return readString(result.getResponse().getContentAsString(), "$.data.seller.id");
    }

    /** 商品状态直接读库：接口侧的状态断言会被响应组装掩盖，库里的值才是状态机的结论。 */
    protected String itemStatus(String itemId) {
        return jdbcTemplate.queryForObject("SELECT status FROM items WHERE id = ?",
                String.class, Long.valueOf(itemId));
    }

    protected String orderStatus(String orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?",
                String.class, Long.valueOf(orderId));
    }

    protected String orderVersion(String orderId) {
        return jdbcTemplate.queryForObject("SELECT version FROM orders WHERE id = ?",
                String.class, Long.valueOf(orderId));
    }

    protected int unreadCount(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header(AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return readInt(result.getResponse().getContentAsString(), "$.data.count");
    }
}
