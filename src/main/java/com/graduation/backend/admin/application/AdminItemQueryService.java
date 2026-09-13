package com.graduation.backend.admin.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.backend.admin.api.dto.AdminItemResponse;
import com.graduation.backend.admin.query.AdminItemQueryMapper;
import com.graduation.backend.admin.query.AdminItemRow;
import com.graduation.backend.common.web.LikePatterns;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.item.domain.ItemStatus;
import com.graduation.backend.user.application.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理端商品查询。
 *
 * <p>与公开搜索的区别在于「不过滤状态」：管理端要能查到草稿、下架、已售与已删除的商品，
 * 因此状态是可选筛选条件而不是隐含约束。
 */
@Service
public class AdminItemQueryService {

    private static final int KEYWORD_MAX = 50;

    private final AdminItemQueryMapper adminItemQueryMapper;
    private final UserService userService;

    public AdminItemQueryService(AdminItemQueryMapper adminItemQueryMapper, UserService userService) {
        this.adminItemQueryMapper = adminItemQueryMapper;
        this.userService = userService;
    }

    /** 商品分页：支持多状态、多卖家（此处仅单个）与标题关键字筛选。 */
    @Transactional(readOnly = true)
    public PageResult<AdminItemResponse> list(Long adminId, List<ItemStatus> statuses, Long sellerId,
                                              String keyword, int page, int size) {
        userService.requireAdmin(adminId);
        List<String> statusNames = (statuses == null || statuses.isEmpty())
                ? null
                : statuses.stream().map(ItemStatus::name).toList();
        IPage<AdminItemRow> result = adminItemQueryMapper.selectItems(
                new Page<>(page + 1L, size), statusNames, sellerId, LikePatterns.contains(keyword, KEYWORD_MAX));
        return PageResult.of(page, size, result.getTotal(),
                result.getRecords().stream().map(AdminItemResponse::fromRow).toList());
    }
}
