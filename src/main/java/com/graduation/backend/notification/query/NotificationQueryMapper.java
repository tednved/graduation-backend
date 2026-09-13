package com.graduation.backend.notification.query;

import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 消息列表查询（MyBatis-Plus 物理分页）。
 *
 * <p>用户维度过滤写在 SQL 里而不是查出来再筛：越权读取他人的消息在数据库层就不可能发生。
 */
@Mapper
public interface NotificationQueryMapper {

    /** 我的消息：按类型与已读状态筛选（都可省略），创建时间倒序。 */
    IPage<NotificationRow> selectNotifications(
            IPage<NotificationRow> page,
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("read") Boolean read);
}
