package com.graduation.backend.item.application;

import com.graduation.backend.category.domain.Category;
import com.graduation.backend.category.domain.CategoryRepository;
import com.graduation.backend.common.audit.AuditLogService;
import com.graduation.backend.common.domain.Campus;
import com.graduation.backend.common.domain.CampusRepository;
import com.graduation.backend.common.domain.CampusStatus;
import com.graduation.backend.common.domain.CertificationStatus;
import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import com.graduation.backend.file.application.FileService;
import com.graduation.backend.file.config.FileProperties;
import com.graduation.backend.file.domain.FileObject;
import com.graduation.backend.item.api.dto.CreateItemRequest;
import com.graduation.backend.item.api.dto.ItemDetailResponse;
import com.graduation.backend.item.api.dto.UpdateItemRequest;
import com.graduation.backend.item.domain.Item;
import com.graduation.backend.item.domain.ItemCondition;
import com.graduation.backend.item.domain.ItemImage;
import com.graduation.backend.item.domain.ItemImageRepository;
import com.graduation.backend.item.domain.ItemPermissionService;
import com.graduation.backend.item.domain.ItemRepository;
import com.graduation.backend.item.domain.ItemStatus;
import com.graduation.backend.item.domain.Money;
import com.graduation.backend.user.application.UserService;
import com.graduation.backend.user.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 商品写路径。
 *
 * <p>权限与状态校验放在事务内重新读库：控制器传来的只有用户 ID，管理员/认证状态都以事务内
 * 读到的实体为准，避免降权后仍在一次请求里生效。
 *
 * <p>每次写入都用 {@code findByIdForUpdate} 悲观锁定商品行，再显式比对 {@code version}。
 * 锁保证同一商品的写入串行化（乐观锁不可能在提交时才失败，也就不会把 409 变成 500），
 * {@code version} 仍然作为「客户端拿到的是旧数据」的显式判断。
 *
 * <p>图片替换用「先删后插 + flush」：{@code item_images} 上有 {@code (item_id, sort_no)} 与
 * {@code (item_id, file_object_id)} 两个唯一键，不先落盘删除就会撞键。
 */
@Service
public class ItemApplicationService {

    private static final int IMAGE_MIN = 1;
    private static final int IMAGE_MAX = 9;

    private final ItemRepository itemRepository;
    private final ItemImageRepository itemImageRepository;
    private final ItemQueryService itemQueryService;
    private final ItemPermissionService permissionService;
    private final FileService fileService;
    private final FileProperties fileProperties;
    private final CategoryRepository categoryRepository;
    private final CampusRepository campusRepository;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public ItemApplicationService(ItemRepository itemRepository,
                                  ItemImageRepository itemImageRepository,
                                  ItemQueryService itemQueryService,
                                  ItemPermissionService permissionService,
                                  FileService fileService,
                                  FileProperties fileProperties,
                                  CategoryRepository categoryRepository,
                                  CampusRepository campusRepository,
                                  UserService userService,
                                  AuditLogService auditLogService,
                                  Clock clock) {
        this.itemRepository = itemRepository;
        this.itemImageRepository = itemImageRepository;
        this.itemQueryService = itemQueryService;
        this.permissionService = permissionService;
        this.fileService = fileService;
        this.fileProperties = fileProperties;
        this.categoryRepository = categoryRepository;
        this.campusRepository = campusRepository;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /** 创建草稿：要求 ACTIVE 且认证 APPROVED，校区由服务端绑定 {@code MAIN}。 */
    @Transactional
    public ItemDetailResponse create(Long userId, CreateItemRequest request) {
        User seller = requireCertifiedSeller(userId);
        Category category = requireRequestedSecondLevel(request.categoryId());
        Campus campus = requireEnabledMainCampus();
        BigDecimal price = Money.parse(request.price(), "price");
        BigDecimal originalPrice = Money.parseOptional(request.originalPrice(), "originalPrice");
        requireOriginalPriceNotBelow(price, originalPrice);
        List<Long> fileIds = requireDistinctFileIds(request.imageFileIds());
        List<FileObject> files = loadBindableImages(fileIds, seller.getId());

        Instant now = clock.instant();
        Item item = Item.create(seller.getId(), category.getId(), campus.getId(),
                request.title(), request.description(), price, originalPrice, request.condition(), now);
        itemRepository.save(item);
        insertImages(item.getId(), fileIds, now);
        files.forEach(file -> fileService.bindItemImage(file, now));

        return itemQueryService.assemble(item, Optional.of(ItemViewerResolver.Viewer.of(seller)));
    }

    /** 修改：字段省略即保留原值，显式 null 仅对 {@code originalPrice} 有意义（清除原价）。 */
    @Transactional
    public ItemDetailResponse update(Long userId, Long itemId, UpdateItemRequest request) {
        User seller = userService.requireActiveUser(userId);
        Item item = requireItemForUpdate(itemId);
        permissionService.requireSeller(item, seller.getId(), seller.isAdmin());
        requireVersion(item, request.getVersion());

        String title = request.hasTitle() ? request.getTitle() : item.getTitle();
        String description = request.hasDescription() ? request.getDescription() : item.getDescription();
        BigDecimal price = request.hasPrice() ? Money.parse(request.getPrice(), "price") : item.getPrice();
        BigDecimal originalPrice = request.hasOriginalPrice()
                ? Money.parseOptional(request.getOriginalPrice(), "originalPrice")
                : item.getOriginalPrice();
        ItemCondition condition = request.hasCondition() ? request.getCondition() : item.getConditionLevel();
        Long categoryId = request.hasCategoryId()
                ? requireRequestedSecondLevel(request.getCategoryId()).getId()
                : item.getCategoryId();
        requireOriginalPriceNotBelow(price, originalPrice);

        Instant now = clock.instant();
        item.update(title, description, price, originalPrice, condition, categoryId, now);
        if (request.hasImageFileIds()) {
            replaceImages(item, requireDistinctFileIds(request.getImageFileIds()), seller.getId(), now);
        }

        return assembleAfterFlush(item, Optional.of(ItemViewerResolver.Viewer.of(seller)));
    }

    /** 上架：字段完整、至少一张图片、分类与校区启用、认证仍有效、未被管理员锁定。 */
    @Transactional
    public ItemDetailResponse publish(Long userId, Long itemId) {
        User seller = requireCertifiedSeller(userId);
        Item item = requireItemForUpdate(itemId);
        permissionService.requireSeller(item, seller.getId(), seller.isAdmin());
        requireStillSellable(item);
        if (itemImageRepository.findByItemIdOrderBySortNoAsc(itemId).isEmpty()) {
            throw new BusinessException(ErrorCode.ITEM_NOT_EDITABLE, "商品至少需要一张图片才能上架");
        }
        item.publish(clock.instant());

        return assembleAfterFlush(item, Optional.of(ItemViewerResolver.Viewer.of(seller)));
    }

    /** 主动下架：仅 {@code ON_SALE} 可下架。 */
    @Transactional
    public ItemDetailResponse offShelf(Long userId, Long itemId) {
        User seller = userService.requireActiveUser(userId);
        Item item = requireItemForUpdate(itemId);
        permissionService.requireSeller(item, seller.getId(), seller.isAdmin());
        item.offShelf(clock.instant());

        return assembleAfterFlush(item, Optional.of(ItemViewerResolver.Viewer.of(seller)));
    }

    /** 删除：仅 DRAFT/OFF_SHELF 且无进行中订单；状态改 DELETED，不物理删除。 */
    @Transactional
    public void delete(Long userId, Long itemId) {
        User seller = userService.requireActiveUser(userId);
        Item item = requireItemForUpdate(itemId);
        permissionService.requireSeller(item, seller.getId(), seller.isAdmin());
        if (itemRepository.countInProgressOrders(itemId) > 0) {
            throw new BusinessException(ErrorCode.ITEM_NOT_EDITABLE, "商品存在进行中的订单，不能删除");
        }
        item.delete(clock.instant());
    }

    /**
     * 管理员强制下架并锁定。
     *
     * <p>已删除的商品返回 404：本接口没有 409，也没必要对不可见资源做状态冲突判定。
     * 响应按匿名视图组装（{@code allowedActions} 为空数组），避免给管理端返回「可收藏/可购买」这类
     * 与操作者身份无关的动作。
     *
     * <p>契约还要求「创建卖家通知」，通知能力属于 BE-09 未实现，本次不写通知（已在交接中记录）。
     */
    @Transactional
    public ItemDetailResponse adminOffShelf(Long adminId, Long itemId, String reason) {
        User admin = userService.requireAdmin(adminId);
        Item item = requireItemForUpdate(itemId);
        if (item.getStatus() == ItemStatus.DELETED) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在");
        }
        item.adminOffShelf(reason, clock.instant());
        auditLogService.record(admin.getId(), "ITEM_ADMIN_OFF_SHELF", "ITEM", item.getId(),
                Map.of("reason", reason, "sellerId", item.getSellerId(), "status", item.getStatus().name()));

        return assembleAfterFlush(item, Optional.empty());
    }

    /** 创建时插入图片行：{@code sortNo} 按请求顺序生成 1..9，客户端不能指定。 */
    private void insertImages(Long itemId, List<Long> fileIds, Instant now) {
        for (int index = 0; index < fileIds.size(); index++) {
            Long fileId = fileIds.get(index);
            itemImageRepository.save(ItemImage.of(
                    itemId, fileId, fileProperties.publicUrlFor(fileId), index + 1, now));
        }
    }

    /**
     * 全量替换图片列表。
     *
     * <p>仍在本商品上的文件直接复用原有 URL（它们已经是 BOUND，不能再走「可绑定」校验）；
     * 新增文件必须是本人上传且状态 UPLOADED；被移除的文件解绑进入待清理状态。
     */
    private void replaceImages(Item item, List<Long> requestedFileIds, Long sellerId, Instant now) {
        Map<Long, String> existingUrls = itemImageRepository.findByItemIdOrderBySortNoAsc(item.getId())
                .stream()
                .collect(Collectors.toMap(ItemImage::getFileObjectId, ItemImage::getImageUrl,
                        (first, second) -> first, LinkedHashMap::new));

        List<FileObject> freshlyBound = new ArrayList<>();
        for (Long fileId : requestedFileIds) {
            if (!existingUrls.containsKey(fileId)) {
                freshlyBound.add(fileService.requireBindableItemImage(fileId, sellerId));
            }
        }

        itemImageRepository.deleteByItemId(item.getId());
        itemImageRepository.flush();

        for (int index = 0; index < requestedFileIds.size(); index++) {
            Long fileId = requestedFileIds.get(index);
            String url = existingUrls.containsKey(fileId)
                    ? existingUrls.get(fileId)
                    : fileProperties.publicUrlFor(fileId);
            itemImageRepository.save(ItemImage.of(item.getId(), fileId, url, index + 1, now));
        }

        existingUrls.keySet().stream()
                .filter(fileId -> !requestedFileIds.contains(fileId))
                .forEach(fileId -> fileService.unbindItemImage(fileService.requireReadable(fileId)));
        freshlyBound.forEach(file -> fileService.bindItemImage(file, now));
    }

    /** 图片 ID 列表：数量 1~9、不能重复（重复会撞 {@code (item_id, file_object_id)} 唯一键）。 */
    private List<Long> requireDistinctFileIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.size() < IMAGE_MIN || fileIds.size() > IMAGE_MAX) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "商品图片数量需在 1~9 之间");
        }
        if (fileIds.contains(null)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "图片 ID 不能为空");
        }
        if (new HashSet<>(fileIds).size() != fileIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "商品图片不能重复");
        }
        return fileIds;
    }

    private List<FileObject> loadBindableImages(List<Long> fileIds, Long sellerId) {
        List<FileObject> files = new ArrayList<>(fileIds.size());
        for (Long fileId : fileIds) {
            files.add(fileService.requireBindableItemImage(fileId, sellerId));
        }
        return files;
    }

    private User requireCertifiedSeller(Long userId) {
        User user = userService.requireActiveUser(userId);
        if (user.getCertificationStatus() != CertificationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.USER_CERTIFICATION_REQUIRED, "发布商品需要先通过校园认证");
        }
        return user;
    }

    /** 请求里的分类：不存在 → 404；存在但不是启用的二级分类 → 400。 */
    private Category requireRequestedSecondLevel(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "分类不存在"));
        if (category.isLevelOne() || !category.isEnabled()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "必须选择启用的二级分类");
        }
        return category;
    }

    /** 上架时复核商品已绑定的分类与校区：不满足都按「当前状态不允许上架」处理。 */
    private void requireStillSellable(Item item) {
        Category category = categoryRepository.findById(item.getCategoryId()).orElse(null);
        if (category == null || category.isLevelOne() || !category.isEnabled()) {
            throw new BusinessException(ErrorCode.ITEM_NOT_EDITABLE, "商品分类已停用，不能上架");
        }
        Campus campus = campusRepository.findById(item.getCampusId()).orElse(null);
        if (campus == null || campus.getStatus() != CampusStatus.ENABLED) {
            throw new BusinessException(ErrorCode.ITEM_NOT_EDITABLE, "校区已停用，不能上架");
        }
    }

    private Campus requireEnabledMainCampus() {
        Campus campus = campusRepository.findByCode(Campus.MAIN_CODE)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "默认校区不存在"));
        if (campus.getStatus() != CampusStatus.ENABLED) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "默认校区已停用");
        }
        return campus;
    }

    private void requireOriginalPriceNotBelow(BigDecimal price, BigDecimal originalPrice) {
        if (originalPrice != null && originalPrice.compareTo(price) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "原价不得小于售价");
        }
    }

    /** 乐观锁版本必须与库中当前值一致；不一致按契约返回 {@code ITEM_NOT_EDITABLE}。 */
    private void requireVersion(Item item, Integer requested) {
        long current = item.getVersion() == null ? 0L : item.getVersion();
        if (requested == null || requested.longValue() != current) {
            throw new BusinessException(ErrorCode.ITEM_NOT_EDITABLE, "商品已被修改，请刷新后重试");
        }
    }

    private Item requireItemForUpdate(Long itemId) {
        return itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在"));
    }

    /**
     * 改动后的回显必须先把实体刷盘。
     *
     * <p>{@code @Version} 由 Hibernate 在刷盘时才自增，提交发生在方法返回之后；不显式 flush
     * 就会把「改动前」的 version 回给客户端，而契约要求编辑携带最新 version——
     * 客户端下一次编辑必然 {@code ITEM_NOT_EDITABLE}。
     */
    private ItemDetailResponse assembleAfterFlush(Item item, Optional<ItemViewerResolver.Viewer> viewer) {
        itemRepository.flush();
        return itemQueryService.assemble(item, viewer);
    }
}
