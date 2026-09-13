package com.graduation.backend.notification.application;

import com.graduation.backend.notification.domain.Notification;
import com.graduation.backend.notification.domain.NotificationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 单条站内消息的落库事务。
 *
 * <p>独立存在的唯一理由是<b>让 {@code REQUIRES_NEW} 的事务边界落在调用方能捕获的位置</b>：
 * 事务由代理在这个方法的外围开启与提交，调用方（{@link NotificationPublisher}）把整个调用包在
 * {@code try/catch} 里，于是「开事务、写入、提交」任一环节失败都不会传播到业务调用方。
 * 见 {@code NotificationPublisher} 的类注释。
 *
 * <p>本类不吞异常：失败必须原样抛给调用方决定怎么处理，这里不做日志也不降级。
 */
@Component
public class NotificationWriter {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationWriter(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    /** 在独立事务中写入一条消息；异常直接抛出，由调用方捕获。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(NotificationMessage message) {
        notificationRepository.save(Notification.of(
                message.userId(), message.type(), message.title(), message.content(),
                message.bizType(), message.bizId(), clock.instant()));
    }
}
