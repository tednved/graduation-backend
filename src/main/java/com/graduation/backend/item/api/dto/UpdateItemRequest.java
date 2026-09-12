package com.graduation.backend.item.api.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.graduation.backend.item.domain.ItemCondition;
import com.graduation.backend.item.domain.Money;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 修改商品。全量替换：字段省略表示保留原值。
 *
 * <p>不用 record：需要区分「字段未出现」与「显式 null」。除 {@code originalPrice} 外，
 * 显式 null 都是无意义的（{@code originalPrice} 显式 null 表示清除原价），由
 * {@link #isFieldsAcceptable()} 统一拦成 400，而不是把 null 当成「保留原值」悄悄忽略。
 *
 * <p>长度/格式不在字段上写注解：未出现的字段值是 null，字段级注解会误报必填；
 * 改为「只在出现时校验」的 {@code @AssertTrue} 方法。值不做 trim，保证「校验的值」
 * 与「落库的值」完全一致。
 *
 * <p>{@code version} 必填，必须与商品当前版本一致，否则 409 {@code ITEM_NOT_EDITABLE}。
 */
public class UpdateItemRequest {

    private static final Pattern MONEY = Pattern.compile(Money.PATTERN_STR);

    private Integer version;
    private String title;
    private boolean titleProvided;
    private String description;
    private boolean descriptionProvided;
    private String price;
    private boolean priceProvided;
    private String originalPrice;
    private boolean originalPriceProvided;
    private ItemCondition condition;
    private boolean conditionProvided;
    private Long categoryId;
    private boolean categoryIdProvided;
    private List<Long> imageFileIds;
    private boolean imageFileIdsProvided;

    @JsonSetter("version")
    public void setVersion(Integer version) {
        this.version = version;
    }

    @JsonSetter("title")
    public void setTitle(String title) {
        this.title = title;
        this.titleProvided = true;
    }

    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
        this.descriptionProvided = true;
    }

    @JsonSetter("price")
    public void setPrice(String price) {
        this.price = price;
        this.priceProvided = true;
    }

    @JsonSetter("originalPrice")
    public void setOriginalPrice(String originalPrice) {
        this.originalPrice = originalPrice;
        this.originalPriceProvided = true;
    }

    @JsonSetter("condition")
    public void setCondition(ItemCondition condition) {
        this.condition = condition;
        this.conditionProvided = true;
    }

    @JsonSetter("categoryId")
    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
        this.categoryIdProvided = true;
    }

    @JsonSetter("imageFileIds")
    public void setImageFileIds(List<Long> imageFileIds) {
        this.imageFileIds = imageFileIds;
        this.imageFileIdsProvided = true;
    }

    /** 除可清除的 {@code originalPrice} 外，任何「出现了但为 null」的字段都判为非法。 */
    @AssertTrue(message = "字段不能显式置为 null，省略表示保留原值")
    public boolean isFieldsAcceptable() {
        if (titleProvided && title == null) {
            return false;
        }
        if (descriptionProvided && description == null) {
            return false;
        }
        if (priceProvided && price == null) {
            return false;
        }
        if (conditionProvided && condition == null) {
            return false;
        }
        if (categoryIdProvided && categoryId == null) {
            return false;
        }
        if (imageFileIdsProvided && imageFileIds == null) {
            return false;
        }
        return !imageFileIdsProvided || !imageFileIds.contains(null);
    }

    @NotNull(message = "版本号不能为空")
    public Integer getVersion() {
        return version;
    }

    public boolean hasTitle() {
        return titleProvided;
    }

    public String getTitle() {
        return title;
    }

    public boolean hasDescription() {
        return descriptionProvided;
    }

    public String getDescription() {
        return description;
    }

    public boolean hasPrice() {
        return priceProvided;
    }

    public String getPrice() {
        return price;
    }

    public boolean hasOriginalPrice() {
        return originalPriceProvided;
    }

    /** 显式 null 表示清除原价；调用前需用 {@link #hasOriginalPrice()} 判断字段是否出现。 */
    public String getOriginalPrice() {
        return originalPrice;
    }

    public boolean hasCondition() {
        return conditionProvided;
    }

    public ItemCondition getCondition() {
        return condition;
    }

    public boolean hasCategoryId() {
        return categoryIdProvided;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public boolean hasImageFileIds() {
        return imageFileIdsProvided;
    }

    public List<Long> getImageFileIds() {
        return imageFileIds;
    }

    @AssertTrue(message = "标题不能为空，且长度需在 2~80 之间")
    public boolean isTitleAcceptable() {
        return !titleProvided
                || (title != null && !title.isBlank() && title.length() >= 2 && title.length() <= 80);
    }

    @AssertTrue(message = "描述不能为空，且长度需在 10~2000 之间")
    public boolean isDescriptionAcceptable() {
        return !descriptionProvided
                || (description != null && !description.isBlank()
                    && description.length() >= 10 && description.length() <= 2000);
    }

    @AssertTrue(message = "价格必须是两位小数的金额字符串")
    public boolean isPriceAcceptable() {
        return !priceProvided || (price != null && MONEY.matcher(price).matches());
    }

    @AssertTrue(message = "原价必须是两位小数的金额字符串")
    public boolean isOriginalPriceAcceptable() {
        return !originalPriceProvided || originalPrice == null || MONEY.matcher(originalPrice).matches();
    }

    @AssertTrue(message = "商品图片数量需在 1~9 之间")
    public boolean isImageFileIdsAcceptable() {
        return !imageFileIdsProvided || (imageFileIds != null
                && imageFileIds.size() >= 1 && imageFileIds.size() <= 9);
    }

    @AssertTrue(message = "版本号不能为负")
    public boolean isVersionAcceptable() {
        return version == null || version >= 0;
    }
}
