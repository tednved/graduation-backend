package com.graduation.backend.certification.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 管理端认证列表查询。
 *
 * <p>使用 MyBatis-Plus 的物理分页：分页参数由 {@code PaginationInnerInterceptor} 追加，
 * SQL 里不写 LIMIT，避免「先查全表再内存分页」。
 */
@Mapper
public interface CertificationQueryMapper {

    @Select("""
            <script>
            SELECT id, user_id, campus_id, type, real_name_masked, student_no_masked,
                   status, reject_reason, reviewed_at, created_at, updated_at
            FROM certifications
            <where>
                <if test="status != null">status = #{status}</if>
            </where>
            ORDER BY created_at DESC, id DESC
            </script>
            """)
    IPage<CertificationListRow> selectPage(IPage<CertificationListRow> page, @Param("status") String status);
}
