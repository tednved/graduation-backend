package com.graduation.backend.item.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.graduation.backend.item.domain.ItemCondition;
import com.graduation.backend.item.domain.Money;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 修改商品（全量替换）。
 *
 * <p>契约把本接口定义为全量替换（{@code openapi.yaml} 的 {@code UpdateItemRequest.required}
 * 含全部业务字段）：{@code version} 与全部业务字段均必填，字段省略或显式 {@code null}
 * 一律 400 {@code VALIDATION_ERROR}，不保留原值。
 *
 * <p>唯一的例外是 {@code originalPrice}：允许为 {@code null}（表示清除原价），
 * 但**必须出现在请求体里**——JSON 里「键缺失」与「显式 null」是两件事，前者是非法请求。
 * 这个区别只能靠 {@link #setOriginalPrice} 打的存在标记拿到，所以本类不是 record；
 * 其余字段的必填由声明式校验完成，不再有「字段是否出现」的探测。
 *
 * <p>长度/格式注解在 getter 而不是字段上：本类含一个 {@code @AssertTrue} 存在性校验，
 * Bean Validation 不允许同一类混用字段访问与属性访问。值不做 trim，
 * 保证「校验的值」与「落库的值」完全一致。
 *
 * <p>{@code version} 必填，必须等于商品当前版本，否则 409 {@code ITEM_NOT_EDITABLE}。
 */
public class UpdateItemRequest {

    private Integer version;
    private String title;
    private String description;
    private String price;
    private String originalPrice;
    private ItemCondition condition;
    private Long categoryId;
    private List<Long> imageFileIds;
    private boolean originalPriceProvided;

    @JsonSetter("version")
    public void setVersion(Integer version) {
        this.version = version;
    }

    @JsonSetter("title")
    public void setTitle(String title) {
        this.title = title;
    }

    @JsonSetter("description")
    public void setDescription(String description) {
        this.description = description;
    }

    @JsonSetter("price")
    public void setPrice(String price) {
        this.price = price;
    }

    @JsonSetter("originalPrice")
    public void setOriginalPrice(String originalPrice) {
        this.originalPrice = originalPrice;
        this.originalPriceProvided = true;
    }

    @JsonSetter("condition")
    public void setCondition(ItemCondition condition) {
        this.condition = condition;
    }

    @JsonSetter("categoryId")
    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    @JsonSetter("imageFileIds")
    public void setImageFileIds(List<Long> imageFileIds) {
        this.imageFileIds = imageFileIds;
    }

    @NotNull(message = "版本号不能为空")
    @Min(value = 0, message = "版本号不能为负数")
    public Integer getVersion() {
        return version;
    }

    @NotBlank(message = "标题不能为空")
    @Size(min = 2, max = 80, message = "标题长度需在 2~80 之间")
    public String getTitle() {
        return title;
    }

    @NotBlank(message = "描述不能为空")
    @Size(min = 10, max = 2000, message = "描述长度需在 10~2000 之间")
    public String getDescription() {
        return description;
    }

    @NotBlank(message = "价格不能为空")
    @Pattern(regexp = Money.PATTERN_STR, message = "价格必须是两位小数的金额字符串")
    public String getPrice() {
        return price;
    }

    /** 可为 {@code null}（清除原价），但必须出现；见 {@link #isOriginalPriceProvided()}。 */
    @Pattern(regexp = Money.PATTERN_STR, message = "原价必须是两位小数的金额字符串")
    public String getOriginalPrice() {
        return originalPrice;
    }

    @NotNull(message = "商品成色不能为空")
    public ItemCondition getCondition() {
        return condition;
    }

    @NotNull(message = "分类不能为空")
    public Long getCategoryId() {
        return categoryId;
    }

    @NotEmpty(message = "至少上传一张商品图片")
    @Size(min = 1, max = 9, message = "商品图片数量需在 1~9 之间")
    public List<@NotNull(message = "图片 ID 不能为空") Long> getImageFileIds() {
        return imageFileIds;
    }

    /** 原价必须出现在请求体里：保留原价请传当前值，清除原价请显式传 {@code null}。 */
    @JsonIgnore
    @AssertTrue(message = "原价字段必填：保留原价请传当前值，清除原价请显式传 null")
    public boolean isOriginalPriceProvided() {
        return originalPriceProvided;
    }
}
