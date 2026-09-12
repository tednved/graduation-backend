package com.graduation.backend.item.application;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.backend.category.domain.Category;
import com.graduation.backend.category.domain.CategoryRepository;
import com.graduation.backend.common.domain.Campus;
import com.graduation.backend.common.domain.CampusRepository;
import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import com.graduation.backend.common.web.PageResult;
import com.graduation.backend.common.web.dto.UserSummaryResponse;
import com.graduation.backend.favorite.domain.FavoriteRepository;
import com.graduation.backend.item.api.dto.CampusRefResponse;
import com.graduation.backend.item.api.dto.CategoryRefResponse;
import com.graduation.backend.item.api.dto.ItemCardResponse;
import com.graduation.backend.item.api.dto.ItemDetailResponse;
import com.graduation.backend.item.api.dto.ItemImageResponse;
import com.graduation.backend.item.api.dto.MyItemSummaryResponse;
import com.graduation.backend.item.domain.Item;
import com.graduation.backend.item.domain.ItemAction;
import com.graduation.backend.item.domain.ItemCondition;
import com.graduation.backend.item.domain.ItemPermissionService;
import com.graduation.backend.item.domain.ItemRepository;
import com.graduation.backend.item.domain.ItemStatus;
import com.graduation.backend.item.domain.ItemImageRepository;
import com.graduation.backend.item.domain.ItemSort;
import com.graduation.backend.item.query.ItemCardRow;
import com.graduation.backend.item.query.ItemQueryMapper;
import com.graduation.backend.user.application.UserService;
import com.graduation.backend.user.domain.User;
import com.graduation.backend.user.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 商品读取：详情组装、公开搜索、我的发布。
 *
 * <p>列表一律走 MyBatis-Plus 投影 + 物理分页（{@code PaginationInnerInterceptor} 追加 LIMIT），
 * 不加载实体；详情走 JPA 实体查询，因为可见性判定与身份相关的动作计算都需要领域对象。
 *
 * <p>写路径的详情回显复用 {@link #assemble}：刚写入的实体已在持久化上下文里，
 * 不必再查一次，也不会触发浏览量自增。
 */
@Service
public class ItemQueryService {

    private static final Logger log = LoggerFactory.getLogger(ItemQueryService.class);
    private static final int KEYWORD_MAX = 50;

    private final ItemRepository itemRepository;
    private final ItemImageRepository itemImageRepository;
    private final ItemQueryMapper itemQueryMapper;
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final CampusRepository campusRepository;
    private final ItemPermissionService permissionService;
    private final ItemViewCounter itemViewCounter;
    private final UserService userService;

    public ItemQueryService(ItemRepository itemRepository,
                           ItemImageRepository itemImageRepository,
                           ItemQueryMapper itemQueryMapper,
                           FavoriteRepository favoriteRepository,
                           UserRepository userRepository,
                           CategoryRepository categoryRepository,
                           CampusRepository campusRepository,
                           ItemPermissionService permissionService,
                           ItemViewCounter itemViewCounter,
                           UserService userService) {
        this.itemRepository = itemRepository;
        this.itemImageRepository = itemImageRepository;
        this.itemQueryMapper = itemQueryMapper;
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.campusRepository = campusRepository;
        this.permissionService = permissionService;
        this.itemViewCounter = itemViewCounter;
        this.userService = userService;
    }

    /** 商品详情。匿名查看者传 {@link Optional#empty()}。 */
    @Transactional(readOnly = true)
    public ItemDetailResponse detail(Long itemId, Optional<ItemViewerResolver.Viewer> viewer) {
        Item item = requireItem(itemId);
        permissionService.requireVisible(item, viewerId(viewer), isAdmin(viewer));
        recordViewQuietly(itemId);
        return assemble(item, viewer);
    }

    /**
     * 组装详情响应，不校验可见性、不自增浏览量。
     *
     * <p>调用方必须先完成权限判定（写路径的调用方本身就是卖家本人）。
     */
    public ItemDetailResponse assemble(Item item, Optional<ItemViewerResolver.Viewer> viewer) {
        List<ItemImageResponse> images = itemImageRepository.findByItemIdOrderBySortNoAsc(item.getId())
                .stream()
                .map(ItemImageResponse::from)
                .toList();
        User seller = userRepository.findById(item.getSellerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品卖家不存在"));
        Category category = categoryRepository.findById(item.getCategoryId()).orElse(null);
        Campus campus = campusRepository.findById(item.getCampusId()).orElse(null);

        Long viewerId = viewerId(viewer);
        boolean certified = viewer.map(ItemViewerResolver.Viewer::certified).orElse(false);
        boolean isOwner = item.isOwnedBy(viewerId);
        // 匿名必须返回 null，与「未收藏」区分；本人不可能是收藏者，直接给 false。
        Boolean favorited = viewerId == null ? null : favoriteRepository.existsByUserIdAndItemId(viewerId, item.getId());
        List<ItemAction> allowedActions = permissionService.allowedActions(item, viewerId, certified);

        return new ItemDetailResponse(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                item.getPrice(),
                item.getOriginalPrice(),
                item.getConditionLevel(),
                item.getStatus(),
                images,
                UserSummaryResponse.from(seller),
                CategoryRefResponse.from(category),
                CampusRefResponse.from(campus),
                favorited,
                isOwner,
                permissionService.canBuy(item, viewerId, certified),
                allowedActions,
                item.isAdminLock(),
                item.getOffShelfReason(),
                item.getFavoriteCount(),
                item.getViewCount(),
                item.getPublishedAt(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getVersion() == null ? 0 : item.getVersion().intValue());
    }

    /** 公开搜索：只返回在售商品。未知分类返回空页（该接口只允许 400/500，不能报 404）。 */
    @Transactional(readOnly = true)
    public PageResult<ItemCardResponse> search(int page, int size, String keyword, Long categoryId,
                                              ItemCondition condition, BigDecimal minPrice, BigDecimal maxPrice,
                                              ItemSort sort) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "最低价不能大于最高价");
        }
        List<Long> categoryIds = null;
        if (categoryId != null) {
            categoryIds = itemQueryMapper.findSellableCategoryIds(categoryId);
            if (categoryIds.isEmpty()) {
                return PageResult.of(page, size, 0, List.of());
            }
        }
        IPage<ItemCardRow> result = itemQueryMapper.searchCards(
                new Page<>(page + 1L, size),
                likePattern(keyword),
                categoryIds,
                condition == null ? null : condition.name(),
                minPrice,
                maxPrice,
                (sort == null ? ItemSort.NEWEST : sort).name());
        return toPage(page, size, result);
    }

    /** 我的发布：包含全部状态，可按状态筛选。 */
    @Transactional(readOnly = true)
    public PageResult<MyItemSummaryResponse> myItems(int page, int size, Long sellerId, ItemStatus status) {
        // 事务内重新读库校验账号状态：被禁用的账号带旧 Token 访问时必须是 403 USER_DISABLED。
        userService.requireActiveUser(sellerId);
        IPage<ItemCardRow> result = itemQueryMapper.selectMyItems(
                new Page<>(page + 1L, size), sellerId, status == null ? null : status.name());
        return PageResult.of(page, size, result.getTotal(),
                result.getRecords().stream().map(MyItemSummaryResponse::from).toList());
    }

    private PageResult<ItemCardResponse> toPage(int page, int size, IPage<ItemCardRow> result) {
        return PageResult.of(page, size, result.getTotal(),
                result.getRecords().stream().map(ItemCardResponse::from).toList());
    }

    /**
     * 浏览量失败不得影响详情：单独事务里自增并吞掉异常，只留一条告警日志。
     *
     * <p>吞异常是有意为之（契约明文要求），因此这里不重新抛出。
     */
    private void recordViewQuietly(Long itemId) {
        try {
            itemViewCounter.recordView(itemId);
        } catch (RuntimeException ex) {
            log.warn("商品浏览量自增失败 itemId={} type={}", itemId, ex.getClass().getSimpleName());
        }
    }

    private Item requireItem(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在"));
    }

    private Long viewerId(Optional<ItemViewerResolver.Viewer> viewer) {
        return viewer.map(ItemViewerResolver.Viewer::userId).orElse(null);
    }

    private boolean isAdmin(Optional<ItemViewerResolver.Viewer> viewer) {
        return viewer.map(ItemViewerResolver.Viewer::admin).orElse(false);
    }

    /**
     * 关键词转 LIKE 模式。
     *
     * <p>{@code %}/{@code _} 是用户输入而不是通配符，统一用 {@code !} 转义
     * （与 XML 里的 {@code ESCAPE '!'} 对应）；纯空白视为不筛选。
     */
    private String likePattern(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > KEYWORD_MAX) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "关键词长度需在 1~50 之间");
        }
        String escaped = trimmed.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}
