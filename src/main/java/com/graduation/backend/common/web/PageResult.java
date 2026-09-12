package com.graduation.backend.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 分页响应。对应契约中 {@code PageResponse} 与各列表响应用 {@code allOf} 合并后的形状：
 * 分页元数据字段 + {@code items}。计数一律是 JSON 整数（使用 {@link Integer}），不受 ID 字符串化规则影响。
 */
public record PageResult<T>(
        Integer page,
        Integer size,
        Integer totalElements,
        Integer totalPages,
        boolean hasNext,
        List<T> items) {

    public static <E, T> PageResult<T> of(Page<E> source, Function<E, T> mapper) {
        int totalPages = source.getTotalPages();
        return new PageResult<>(
                source.getNumber(),
                source.getSize(),
                Math.toIntExact(source.getTotalElements()),
                totalPages,
                source.getNumber() + 1 < totalPages,
                source.getContent().stream().map(mapper).toList());
    }

    public static <T> PageResult<T> of(
            int page, int size, long totalElements, List<T> items) {
        int totalPages = size <= 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new PageResult<>(page, size, Math.toIntExact(totalElements), totalPages, page + 1 < totalPages, items);
    }
}
