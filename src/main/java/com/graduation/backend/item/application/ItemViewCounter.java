package com.graduation.backend.item.application;

import com.graduation.backend.item.domain.ItemRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 商品浏览量自增。
 *
 * <p>单独成 bean 并开 {@link Propagation#REQUIRES_NEW}：调用方（详情查询）
 * 是只读事务，而自增是一条写语句。放在新事务里，即使外层查询事务因任何原因回滚，
 * 浏览量也已经独立提交；同时详情接口可以用 try/catch 吞掉这里的失败，
 * 满足契约「浏览量增量失败不得导致详情失败」。
 *
 * <p>计数走 {@code UPDATE items SET view_count = view_count + 1} 原子 SQL，
 * 不使用实体字段赋值，避免并发下互相覆盖。
 */
@Component
public class ItemViewCounter {

    private final ItemRepository itemRepository;

    public ItemViewCounter(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordView(Long itemId) {
        itemRepository.incrementViewCount(itemId);
    }
}
