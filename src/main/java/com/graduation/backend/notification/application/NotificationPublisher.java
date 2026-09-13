package com.graduation.backend.notification.application;

import com.graduation.backend.notification.domain.Notification;
import com.graduation.backend.notification.domain.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;

/**
 * 把业务事务里发布的 {@link NotificationMessage} 落成站内消息。
 *
 * <p>时机选择：{@link TransactionPhase#AFTER_COMMIT} 保证业务回滚时消息不会被写入
 * （事件在提交后才投递，回滚的事务根本不会走到这里）。
 *
 * <p>本方法<b>自身不加 {@code @Transactional}</b>：新事务的开启与提交都由代理在
 * {@link NotificationWriter#write} 的调用点完成。若把 {@code REQUIRES_NEW} 与 {@code try/catch}
 * 放在同一个方法里，代理方法返回之后才发生的提交异常（拿不到连接、提交失败）落在 catch 之外，
 * 会从 {@code afterCommit} 一路抛回业务调用方——业务明明已经提交，接口却返回 500。
 * 拆到独立 Bean 后，try 包住整个代理调用，新事务的开启、写入、提交都在 try 内。
 *
 * <p>因此不需要 MQ、Redis 或重试队列：消息丢失只影响一次提醒，不影响交易状态本身。
 * 写入失败只记告警日志，不让已经提交的业务操作给客户端返回错误。
 */
@Component
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final NotificationWriter notificationWriter;

    public NotificationPublisher(NotificationWriter notificationWriter) {
        this.notificationWriter = notificationWriter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessage(NotificationMessage message) {
        try {
            notificationWriter.write(message);
        } catch (RuntimeException ex) {
            log.warn("站内消息写入失败 type={} userId={} bizType={} bizId={}",
                    message.type(), message.userId(), message.bizType(), message.bizId(), ex);
        }
    }
}
