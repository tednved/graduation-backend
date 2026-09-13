package com.graduation.backend.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 下单并发保证的真实 MySQL 用例。
 *
 * <p>与 {@link OrderApiFlowTests} 的区别是<b>真正的并发</b>：两个请求各在独立线程、
 * 各持独立事务，用同一个门闩起跑。串行地「第一个人下完单再让第二个人请求」只能测出
 * 状态判定，测不出悲观锁是否真的把并发请求串行化、以及幂等键在并发下是否仍然只落一笔。
 *
 * <p>断言的是<b>不变量</b>而不是时序：无论两个请求是否真的重叠，赢家都必须恰好一个。
 * 因此用例不会因为调度差异而随机失败，而在锁被移除时会稳定失败。
 *
 * <p>用例类不加 {@code @Transactional}：请求必须在各自线程上真实提交，锁才有意义。
 */
@EnabledIf("databaseConfigured")
class OrderConcurrencyTests extends OrderTestSupport {

    private static final int THREADS = 2;

    private static final long START_TIMEOUT_SECONDS = 10;

    private static final long FINISH_TIMEOUT_SECONDS = 60;

    @Test
    @DisplayName("两个买家并发抢同一件商品：恰好一人成功，另一人得到并发抢占冲突，库里只有一笔订单")
    void concurrentBuyersOnlyOneWins() throws Exception {
        String seller = certifiedToken("race-item-seller");
        String buyerA = certifiedToken("race-item-buyer-a");
        String buyerB = certifiedToken("race-item-buyer-b");
        PublishedItem item = publishItem(seller, "128.00");

        List<MvcResult> results = race(
                () -> createOrder(buyerA, item.itemId(), newRequestId()),
                () -> createOrder(buyerB, item.itemId(), newRequestId()));

        assertThat(statusesOf(results)).containsExactlyInAnyOrder(201, 409);
        MvcResult loser = statusOf(results.get(0)) == 409 ? results.get(0) : results.get(1);
        assertThat(readString(loser.getResponse().getContentAsString(), "$.code"))
                .isEqualTo("ITEM_CONCURRENTLY_RESERVED");

        // 悲观锁的功效：商品行被串行化，第二个人在锁释放后看到的是已经预留的状态，
        // 既不会两笔订单都落库，也不会把「已被抢」表现成 500。
        assertThat(orderCountOfItem(item.itemId())).isEqualTo(1);
        assertThat(itemStatus(item.itemId())).isEqualTo("RESERVED");
    }

    @Test
    @DisplayName("同一买家同一幂等键并发下单：只落一笔订单，另一次是同键重放且不新增事件")
    void concurrentSameRequestIdProducesOneOrder() throws Exception {
        String seller = certifiedToken("race-key-seller");
        String buyer = certifiedToken("race-key-buyer");
        PublishedItem item = publishItem(seller, "64.00");
        String requestId = newRequestId();

        List<MvcResult> results = race(
                () -> createOrder(buyer, item.itemId(), requestId),
                () -> createOrder(buyer, item.itemId(), requestId));

        // 买家行先被悲观锁定，所以两次请求必然串行：先到的建单 201，后到的读到已提交的订单重放 200。
        assertThat(statusesOf(results)).containsExactlyInAnyOrder(201, 200);
        String firstId = readString(results.get(0).getResponse().getContentAsString(), "$.data.id");
        String secondId = readString(results.get(1).getResponse().getContentAsString(), "$.data.id");
        assertThat(secondId).isEqualTo(firstId);

        String buyerId = readString(results.get(0).getResponse().getContentAsString(), "$.data.buyer.id");
        assertThat(orderCountOfBuyer(buyerId)).isEqualTo(1);
        // 重放不追加订单事件：并发下也只能有一条 CREATE。
        mockMvc.perform(get("/api/v1/orders/{id}", firstId).header(AUTHORIZATION, bearer(buyer)))
                .andExpect(jsonPath("$.data.events", hasSize(1)))
                .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRMATION"));
    }

    /**
     * 两个请求各占一个线程，在同一个门闩上同时起跑。
     *
     * <p>先等两个线程都就位再放行：否则先启动的那个可能已经跑完，用例就退化成串行，
     * 又回到「测不出锁」的老问题。
     */
    private List<MvcResult> race(Callable<MvcResult> first, Callable<MvcResult> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<MvcResult>> futures = new ArrayList<>();
            for (Callable<MvcResult> task : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    .as("两个请求线程都应在超时前就位")
                    .isTrue();
            start.countDown();

            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<Integer> statusesOf(List<MvcResult> results) {
        List<Integer> statuses = new ArrayList<>();
        for (MvcResult result : results) {
            statuses.add(statusOf(result));
        }
        return statuses;
    }

    private int statusOf(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private int orderCountOfItem(String itemId) {
        return count("SELECT COUNT(*) FROM orders WHERE item_id = ?", itemId);
    }

    private int orderCountOfBuyer(String buyerId) {
        return count("SELECT COUNT(*) FROM orders WHERE buyer_id = ?", buyerId);
    }

    private int count(String sql, String id) {
        return jdbcTemplate.queryForObject(sql, Integer.class, Long.valueOf(id));
    }
}
