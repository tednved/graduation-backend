package com.graduation.backend.category.application;

import com.graduation.backend.category.api.dto.CategoryLeafResponse;
import com.graduation.backend.category.api.dto.CategoryNodeResponse;
import com.graduation.backend.category.api.dto.CategoryResponse;
import com.graduation.backend.category.api.dto.CategoryTreeResponse;
import com.graduation.backend.category.api.dto.CreateCategoryRequest;
import com.graduation.backend.category.api.dto.UpdateCategoryRequest;
import com.graduation.backend.category.domain.Category;
import com.graduation.backend.category.domain.CategoryRepository;
import com.graduation.backend.common.audit.AuditLogService;
import com.graduation.backend.common.domain.CategoryStatus;
import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 两级分类的读写边界。
 *
 * <p>停用规则（契约 §8.4）在本实现中的取舍：只有「分类自身」存在 {@code ON_SALE}/{@code RESERVED}
 * 商品才返回 {@code CATEGORY_IN_USE}；停用一级分类时，其下没有在售商品的子分类会被一并停用，
 * 有在售商品的子分类保持启用。这样才能同时满足「先停子、后停父」的实际运营顺序。
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public CategoryService(CategoryRepository categoryRepository, AuditLogService auditLogService, Clock clock) {
        this.categoryRepository = categoryRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CategoryTreeResponse tree() {
        List<Category> roots = categoryRepository
                .findByStatusAndParentIdIsNullOrderBySortNoAscIdAsc(CategoryStatus.ENABLED);
        if (roots.isEmpty()) {
            return new CategoryTreeResponse(List.of());
        }
        List<Long> rootIds = roots.stream().map(Category::getId).toList();
        Map<Long, List<CategoryLeafResponse>> childrenByParent = categoryRepository
                .findByStatusAndParentIdInOrderBySortNoAscIdAsc(CategoryStatus.ENABLED, rootIds).stream()
                .collect(Collectors.groupingBy(Category::getParentId, LinkedHashMap::new,
                        Collectors.mapping(CategoryLeafResponse::from, Collectors.toList())));
        return new CategoryTreeResponse(roots.stream()
                .map(root -> CategoryNodeResponse.from(root, childrenByParent.getOrDefault(root.getId(), List.of())))
                .toList());
    }

    @Transactional
    public CategoryResponse create(Long operatorId, CreateCategoryRequest request) {
        Instant now = clock.instant();
        Long parentId = null;
        if (request.parentId() != null) {
            Category parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "父分类不存在"));
            if (!parent.isLevelOne()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "只支持两级分类，父分类必须是一级分类");
            }
            if (!parent.isEnabled()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "父分类已停用，不能在其下新建子分类");
            }
            parentId = parent.getId();
        }
        String name = request.name().trim();
        requireNameAvailable(parentId, name, null);

        Category saved = categoryRepository.save(
                Category.create(parentId, name, request.iconUrl(), request.sortNo(), now));
        auditLogService.record(operatorId, "CATEGORY_CREATE", "CATEGORY", saved.getId(),
                detail("name", name, "parentId", parentId == null ? "none" : parentId.toString()));
        return CategoryResponse.from(saved);
    }

    @Transactional
    public CategoryResponse update(Long operatorId, Long categoryId, UpdateCategoryRequest request) {
        Category category = require(categoryId);
        Instant now = clock.instant();

        if (request.hasName()) {
            String name = request.getName().trim();
            requireNameAvailable(category.getParentId(), name, category.getId());
            category.rename(name, now);
        }
        if (request.hasIconUrl()) {
            category.changeIcon(request.getIconUrl(), now);
        }
        if (request.hasSortNo()) {
            category.changeSortNo(request.getSortNo(), now);
        }

        auditLogService.record(operatorId, "CATEGORY_UPDATE", "CATEGORY", category.getId(),
                detail("name", category.getName(), "status", category.getStatus().name()));
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse enable(Long operatorId, Long categoryId) {
        Category category = require(categoryId);
        Instant now = clock.instant();
        category.changeStatus(CategoryStatus.ENABLED, now);
        if (category.isLevelOne()) {
            categoryRepository.findByParentIdAndStatusOrderBySortNoAscIdAsc(category.getId(), CategoryStatus.DISABLED)
                    .forEach(child -> child.changeStatus(CategoryStatus.ENABLED, now));
        }
        auditLogService.record(operatorId, "CATEGORY_ENABLE", "CATEGORY", category.getId(),
                detail("name", category.getName(), "status", CategoryStatus.ENABLED.name()));
        return CategoryResponse.from(category);
    }

    @Transactional
    public CategoryResponse disable(Long operatorId, Long categoryId) {
        Category category = require(categoryId);
        Instant now = clock.instant();
        if (categoryRepository.countBlockingItems(List.of(category.getId())) > 0) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE, "该分类下仍有在售或已预订商品，无法停用");
        }
        category.changeStatus(CategoryStatus.DISABLED, now);
        if (category.isLevelOne()) {
            for (Category child : categoryRepository.findByParentIdOrderBySortNoAscIdAsc(category.getId())) {
                if (child.isEnabled() && categoryRepository.countBlockingItems(List.of(child.getId())) == 0) {
                    child.changeStatus(CategoryStatus.DISABLED, now);
                }
            }
        }
        auditLogService.record(operatorId, "CATEGORY_DISABLE", "CATEGORY", category.getId(),
                detail("name", category.getName(), "status", CategoryStatus.DISABLED.name()));
        return CategoryResponse.from(category);
    }

    private Category require(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "分类不存在"));
    }

    /**
     * 同级名称唯一。
     *
     * <p>契约描述了该约束但未在 §7.2 定义对应错误码，{@code Error409} 的可选码里也没有合适的一项，
     * 因此这里返回 400 {@code VALIDATION_ERROR}，与 {@code Error400} 允许的错误码保持一致。
     */
    private void requireNameAvailable(Long parentId, String name, Long selfId) {
        Optional<Category> existing = parentId == null
                ? categoryRepository.findByParentIdIsNullAndName(name)
                : categoryRepository.findByParentIdAndName(parentId, name);
        if (existing.isPresent() && !existing.get().getId().equals(selfId)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "同级分类名称已存在");
        }
    }

    private Map<String, Object> detail(String key1, String value1, String key2, String value2) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put(key1, value1);
        detail.put(key2, value2);
        return detail;
    }
}
