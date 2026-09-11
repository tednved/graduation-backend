package com.graduation.backend.user.api.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.AssertTrue;

import java.util.regex.Pattern;

/**
 * 修改本人资料。
 *
 * <p>契约要求区分「字段省略」与「显式 null」：省略表示保留原值，而 {@code phone}、{@code avatarFileId}
 * 显式 null 表示清除。因此这里用手写 setter 记录字段是否出现，而不是用 record —— record 无法区分两者。
 *
 * <p>出现 {@code role}、{@code status}、{@code openid}、评分等字段由全局
 * {@code fail-on-unknown-properties} 直接判为 400，不在此类中逐个显式拒绝。
 */
public class UpdateProfileRequest {

    private static final Pattern PHONE = Pattern.compile("^1[3-9][0-9]{9}$");
    private static final int NICKNAME_MAX = 20;

    private String nickname;
    private boolean nicknameProvided;
    private Long avatarFileId;
    private boolean avatarFileIdProvided;
    private String phone;
    private boolean phoneProvided;

    @JsonSetter("nickname")
    public void setNickname(String nickname) {
        this.nickname = nickname;
        this.nicknameProvided = true;
    }

    @JsonSetter("avatarFileId")
    public void setAvatarFileId(Long avatarFileId) {
        this.avatarFileId = avatarFileId;
        this.avatarFileIdProvided = true;
    }

    @JsonSetter("phone")
    public void setPhone(String phone) {
        this.phone = phone;
        this.phoneProvided = true;
    }

    /** 昵称不允许显式 null；长度按清理首尾空格后的值判定。 */
    @AssertTrue(message = "昵称不能为空，且清理首尾空格后长度需在 1~20 之间")
    public boolean isNicknameAcceptable() {
        if (!nicknameProvided) {
            return true;
        }
        if (nickname == null) {
            return false;
        }
        String trimmed = nickname.trim();
        return !trimmed.isEmpty() && trimmed.length() <= NICKNAME_MAX;
    }

    @AssertTrue(message = "手机号格式不正确")
    public boolean isPhoneAcceptable() {
        if (!phoneProvided || phone == null) {
            return true;
        }
        return PHONE.matcher(phone).matches();
    }

    public boolean hasNickname() {
        return nicknameProvided;
    }

    public String nicknameTrimmed() {
        return nickname == null ? null : nickname.trim();
    }

    public boolean hasAvatarFileId() {
        return avatarFileIdProvided;
    }

    public Long getAvatarFileId() {
        return avatarFileId;
    }

    public boolean hasPhone() {
        return phoneProvided;
    }

    public String getPhone() {
        return phone;
    }
}
