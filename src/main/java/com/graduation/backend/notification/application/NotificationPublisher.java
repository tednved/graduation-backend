package com.graduation.backend.notification.application;

import com.graduation.backend.notification.domain.Notification;
import com.graduation.backend.notification.domain.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;

/**
 * 把业务事务里发布的 {@link NotificationMessage} 落成站内消息。
 *
 * <p>时机选择：{@link TransactionPhase#AFTER_COMMIT} 保证业务回滚时消息不会被写入
 * （事件在提交后才投递，回滚的事务根本不会走到这里）；独立的
 * {@link Propagation#REQUIRES_NEW} 事务保证「业务已提交、消息写得慢」不会把业务重新挂起，
 * 也不会因为消息写入失败而回滚已完成的订单。
 *
 * <p>因此不需要 MQ、Redis 或重试队列：消息丢失只影响一次提醒，不影响交易状态本身。
 * 写入失败只记告警日志，不让已经提交的业务操作给客户端返回错误。
 */
@Component
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationPublisher(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMessage(NotificationMessage message) {
        try {
            notificationRepository.save(Notification.of(
                    message.userId(), message.type(), message.title(), message.content(),
                    message.bizType(), message.bizId(), clock.instant()));
        } catch (RuntimeException ex) {
            log.warn("站内消息写入失败 type={} userId={} bizType={} bizId={}",
                    message.type(), message.userId(), message.bizType(), message.bizId(), ex);
        }
    }
}
