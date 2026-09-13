package com.graduation.backend.admin.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 管理端用户查询（MyBatis-Plus 物理分页）。
 *
 * <p>投影里没有 {@code openid} 列：契约要求管理端响应也不返回登录标识。
 */
@Mapper
public interface AdminUserQueryMapper {

    /** 按关键字、账号状态、认证状态分页；{@code keyword} 是已转义的 LIKE 模式或 {@code null}。 */
    IPage<AdminUserRow> selectUsers(
            IPage<AdminUserRow> page,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("certificationStatus") String certificationStatus);

    /** 单个用户，用于禁用/恢复后的回显。 */
    AdminUserRow selectById(@Param("id") Long id);
}
