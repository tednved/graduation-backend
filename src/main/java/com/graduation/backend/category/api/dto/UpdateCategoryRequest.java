package com.graduation.backend.category.api.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 修改分类。
 *
 * <p>与资料修改同理，省略字段保留原值、{@code iconUrl} 显式 null 表示清除，
 * 因此需要记录字段是否出现。分类层级 {@code parentId} 不在本请求中，无法通过本接口改变。
 */
public class UpdateCategoryRequest {

    private String name;
    private boolean nameProvided;
    private String iconUrl;
    private boolean iconUrlProvided;
    private Integer sortNo;
    private boolean sortNoProvided;

    @JsonSetter("name")
    public void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    @JsonSetter("iconUrl")
    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
        this.iconUrlProvided = true;
    }

    @JsonSetter("sortNo")
    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
        this.sortNoProvided = true;
    }

    @AssertTrue(message = "名称不能为空，且长度需在 1~20 之间")
    public boolean isNameAcceptable() {
        if (!nameProvided) {
            return true;
        }
        return name != null && !name.isBlank() && name.length() <= 20;
    }

    @AssertTrue(message = "排序号需在 0~9999 之间")
    public boolean isSortNoAcceptable() {
        if (!sortNoProvided) {
            return true;
        }
        return sortNo != null && sortNo >= 0 && sortNo <= 9999;
    }

    public boolean hasName() {
        return nameProvided;
    }

    public String getName() {
        return name;
    }

    public boolean hasIconUrl() {
        return iconUrlProvided;
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public boolean hasSortNo() {
        return sortNoProvided;
    }

    public Integer getSortNo() {
        return sortNo;
    }
}
