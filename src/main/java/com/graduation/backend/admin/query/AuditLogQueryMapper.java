package com.graduation.backend.admin.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

/** 审计日志查询（MyBatis-Plus 物理分页），只读。 */
@Mapper
public interface AuditLogQueryMapper {

    /** 按操作者、动作、目标与时间范围分页；所有条件都可选，时间范围两端都含。 */
    IPage<AuditLogRow> selectAuditLogs(
            IPage<AuditLogRow> page,
            @Param("operatorId") Long operatorId,
            @Param("action") String action,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
