package com.graduation.backend.review.domain;

/** 评价的可见性。MVP 只有 {@code VISIBLE}，管理端隐藏能力属于后续里程碑。 */
public enum ReviewStatus {
    VISIBLE,
    HIDDEN
}
