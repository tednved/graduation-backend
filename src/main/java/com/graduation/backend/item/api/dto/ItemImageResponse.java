package com.graduation.backend.item.api.dto;

import com.graduation.backend.item.domain.ItemImage;

/** 商品图片。{@code sortNo} 由服务端按入参顺序生成 1..9。 */
public record ItemImageResponse(Long fileId, String url, Integer sortNo) {

    public static ItemImageResponse from(ItemImage image) {
        return new ItemImageResponse(image.getFileObjectId(), image.getImageUrl(), image.getSortNo());
    }
}
