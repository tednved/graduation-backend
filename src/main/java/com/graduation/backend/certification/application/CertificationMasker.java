package com.graduation.backend.certification.application;

/**
 * 认证信息的脱敏。
 *
 * <p>库内只落脱敏值，因此这里同时是「写入前唯一一次看到明文」的地方：明文既不入库也不写日志。
 */
public final class CertificationMasker {

    private CertificationMasker() {
    }

    /** 姓名保留首字，其余以 {@code *} 代替，例如 {@code 张三} → {@code 张*}。 */
    public static String maskRealName(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.length() == 1) {
            return value;
        }
        return value.charAt(0) + "*".repeat(value.length() - 1);
    }

    /**
     * 学号保留前 2 位与后 2 位，中间以 {@code *} 代替；长度不足 6 时退化为保留首尾各 1 位，
     * 保证 4 位学号也不会被原样保存。
     */
    public static String maskStudentNo(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        int length = value.length();
        if (length < 6) {
            if (length <= 2) {
                return "*".repeat(length);
            }
            return value.charAt(0) + "*".repeat(length - 2) + value.charAt(length - 1);
        }
        return value.substring(0, 2) + "*".repeat(length - 4) + value.substring(length - 2);
    }
}
