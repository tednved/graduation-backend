package com.graduation.backend.order.application;

import com.graduation.backend.common.domain.CertificationStatus;
import com.graduation.backend.common.error.BusinessException;
import com.graduation.backend.common.error.ErrorCode;
import com.graduation.backend.common.web.RequestIds;
import com.graduation.backend.item.domain.Item;
import com.graduation.backend.item.domain.ItemImage;
import com.graduation.backend.item.domain.ItemImageRepository;
import com.graduation.backend.item.domain.ItemRepository;
import com.graduation.backend.item.domain.ItemStatus;
import com.graduation.backend.notification.application.NotificationMessage;
import com.graduation.backend.notification.domain.NotificationType;
import com.graduation.backend.order.api.dto.CreateOrderRequest;
import com.graduation.backend.order.api.dto.OrderDetailResponse;
import com.graduation.backend.order.domain.Order;
import com.graduation.backend.order.domain.OrderAction;
import com.graduation.backend.order.domain.OrderEvent;
import com.graduation.backend.order.domain.OrderEventRepository;
import com.graduation.backend.order.domain.OrderRepository;
import com.graduation.backend.order.domain.OrderSnapshot;
import com.graduation.backend.order.domain.OrderSnapshotRepository;
import com.graduation.backend.order.domain.OrderStatus;
import com.graduation.backend.user.application.UserService;
import com.graduation.backend.user.domain.User;
import com.graduation.backend.user.domain.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 订单写路径。
 *
 * <p>身份与状态一律在事务内重新读库（控制器只传用户 ID），商品与订单行都用悲观锁串行化，
 * 让并发冲突表现为确定的 409，而不是提交时的乐观锁 500。
 *
 * <p>状态变更、订单事件、商品状态三者同事务：任何一步失败都整体回滚，
 * 不会出现「订单已转 CONFIRMED 但商品仍被预留」这类中间态。
 *
 * <p>站内消息不在这里直接写库：只发布 {@link NotificationMessage}，由监听器在提交后另起事务落库，
 * 消息写失败不影响已成交的订单。
 */
@Service
public class OrderApplicationService {

    private static final DateTimeFormatter ORDER_NO_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);
    private static final char[] ORDER_NO_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final int ORDER_NO_RANDOM_LENGTH = 6;

    private final OrderRepository orderRepository;
    private final OrderSnapshotRepository orderSnapshotRepository;
    private final OrderEventRepository orderEventRepository;
    private final OrderQueryService orderQueryService;
    private final ItemRepository itemRepository;
    private final ItemImageRepository itemImageRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public OrderApplicationService(OrderRepository orderRepository,
                                   OrderSnapshotRepository orderSnapshotRepository,
                                   OrderEventRepository orderEventRepository,
                                   OrderQueryService orderQueryService,
                                   ItemRepository itemRepository,
                                   ItemImageRepository itemImageRepository,
                                   UserRepository userRepository,
                                   UserService userService,
                                   ApplicationEventPublisher eventPublisher,
                                   Clock clock) {
        this.orderRepository = orderRepository;
        this.orderSnapshotRepository = orderSnapshotRepository;
        this.orderEventRepository = orderEventRepository;
        this.orderQueryService = orderQueryService;
        this.itemRepository = itemRepository;
        this.itemImageRepository = itemImageRepository;
        this.userRepository = userRepository;
        this.userService = userService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 创建订单。
     *
     * <p>幂等：先锁买家行，使同一买家的并发下单串行化，再按
     * {@code (buyerId, clientRequestId)} 查已有订单。命中且 {@code itemId} 与 {@code tradeMode}
     * 一致则按「重放」返回原订单；不一致说明客户端复用了同一个标识发起另一笔交易，返回 409。
     *
     * <p>商品冲突分两种：已被预留（{@code RESERVED}）是并发抢单，返回
     * {@code ITEM_CONCURRENTLY_RESERVED}；其余不可售状态返回 {@code ITEM_NOT_AVAILABLE}。
     */
    @Transactional
    public OrderCreationResult create(Long userId, CreateOrderRequest request) {
        if (!request.tradeMode().isSupported()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "暂不支持该交易方式");
        }
        User buyer = userService.requireActiveUserForUpdate(userId);
        requireCertified(buyer);

        Optional<Order> existing = orderRepository.findByBuyerIdAndClientRequestId(
                buyer.getId(), request.clientRequestId());
        if (existing.isPresent()) {
            return replay(existing.get(), request, buyer.getId());
        }

        Item item = itemRepository.findByIdForUpdate(request.itemId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在"));
        if (item.isOwnedBy(buyer.getId())) {
            throw new BusinessException(ErrorCode.ITEM_SELF_PURCHASE, "不能购买自己发布的商品");
        }
        if (item.getStatus() == ItemStatus.RESERVED) {
            throw new BusinessException(ErrorCode.ITEM_CONCURRENTLY_RESERVED, "商品已被其他买家下单");
        }
        if (item.getStatus() != ItemStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.ITEM_NOT_AVAILABLE, "商品当前不可购买");
        }

        User seller = requireUser(item.getSellerId());
        Instant now = clock.instant();
        Order order = Order.create(nextOrderNo(now), item.getId(), buyer.getId(), seller.getId(),
                request.clientRequestId(), item.getPrice(), request.tradeMode(), now);
        orderRepository.save(order);

        orderSnapshotRepository.save(OrderSnapshot.of(
                order.getId(), item.getTitle(), coverImageUrl(item.getId()), item.getPrice(),
                seller.getNickname(), buyer.getNickname(), now));
        appendEvent(order, OrderAction.CREATE, null, OrderStatus.PENDING_CONFIRMATION,
                buyer.getId(), null, now);

        item.reserve(now);

        publish(seller.getId(), NotificationType.ORDER_CREATED, "有新的订单",
                buyer.getNickname() + " 想要购买「" + item.getTitle() + "」，请尽快处理", order.getId());

        return OrderCreationResult.created(orderQueryService.assemble(order, buyer.getId()));
    }

    /** 卖家接单。 */
    @Transactional
    public OrderDetailResponse confirm(Long userId, Long orderId) {
        User actor = userService.requireActiveUser(userId);
        Order order = requireOrderForUpdate(orderId);
        Item item = requireItemForUpdate(order.getItemId());
        if (item.getStatus() != ItemStatus.RESERVED) {
            throw new BusinessException(ErrorCode.ORDER_ILLEGAL_STATUS_TRANSITION, "商品当前不处于预留状态，无法接单");
        }
        Instant now = clock.instant();
        order.confirm(actor.getId(), now);
        appendEvent(order, OrderAction.CONFIRM, OrderStatus.PENDING_CONFIRMATION, order.getStatus(),
                actor.getId(), null, now);

        publish(order.getBuyerId(), NotificationType.ORDER_CONFIRMED, "卖家已接单",
                "卖家已接受你的订单，请按约定完成交易", order.getId());

        return orderQueryService.assemble(order, actor.getId());
    }

    /** 卖家拒单，商品释放回在售。 */
    @Transactional
    public OrderDetailResponse reject(Long userId, Long orderId, String reason) {
        User actor = userService.requireActiveUser(userId);
        Order order = requireOrderForUpdate(orderId);
        Item item = requireItemForUpdate(order.getItemId());
        Instant now = clock.instant();
        order.reject(actor.getId(), reason, now);
        appendEvent(order, OrderAction.REJECT, OrderStatus.PENDING_CONFIRMATION, order.getStatus(),
                actor.getId(), reason, now);
        item.release(now);

        publish(order.getBuyerId(), NotificationType.ORDER_REJECTED, "卖家已拒单",
                "你的订单被拒绝：" + reason, order.getId());

        return orderQueryService.assemble(order, actor.getId());
    }

    /** 取消订单，商品释放回在售；通知另一方。 */
    @Transactional
    public OrderDetailResponse cancel(Long userId, Long orderId, String reason) {
        User actor = userService.requireActiveUser(userId);
        Order order = requireOrderForUpdate(orderId);
        OrderStatus from = order.getStatus();
        Item item = requireItemForUpdate(order.getItemId());
        Instant now = clock.instant();
        order.cancel(actor.getId(), reason, now);
        appendEvent(order, OrderAction.CANCEL, from, order.getStatus(), actor.getId(), reason, now);
        item.release(now);

        Long counterpartId = order.isBuyer(actor.getId()) ? order.getSellerId() : order.getBuyerId();
        publish(counterpartId, NotificationType.ORDER_CANCELLED, "订单已取消",
                "订单已被取消：" + reason, order.getId());

        return orderQueryService.assemble(order, actor.getId());
    }

    /** 卖家确认交付。 */
    @Transactional
    public OrderDetailResponse deliver(Long userId, Long orderId) {
        User actor = userService.requireActiveUser(userId);
        Order order = requireOrderForUpdate(orderId);
        Instant now = clock.instant();
        order.deliver(actor.getId(), now);
        appendEvent(order, OrderAction.DELIVER, OrderStatus.CONFIRMED, order.getStatus(),
                actor.getId(), null, now);

        publish(order.getBuyerId(), NotificationType.ORDER_DELIVERED, "卖家已交付",
                "卖家已确认交付，请及时确认收货", order.getId());

        return orderQueryService.assemble(order, actor.getId());
    }

    /** 买家确认收货：订单转已完成，商品转已售出，双方收到可评价通知。 */
    @Transactional
    public OrderDetailResponse receive(Long userId, Long orderId) {
        User actor = userService.requireActiveUser(userId);
        Order order = requireOrderForUpdate(orderId);
        Item item = requireItemForUpdate(order.getItemId());
        Instant now = clock.instant();
        order.receive(actor.getId(), now);
        appendEvent(order, OrderAction.RECEIVE, OrderStatus.PENDING_RECEIPT, order.getStatus(),
                actor.getId(), null, now);
        item.markSold(now);

        publish(order.getBuyerId(), NotificationType.ORDER_COMPLETED, "交易已完成",
                "交易已完成，快去评价对方吧", order.getId());
        publish(order.getSellerId(), NotificationType.ORDER_COMPLETED, "交易已完成",
                "交易已完成，快去评价对方吧", order.getId());

        return orderQueryService.assemble(order, actor.getId());
    }

    private OrderCreationResult replay(Order order, CreateOrderRequest request, Long buyerId) {
        boolean sameRequest = order.getItemId().equals(request.itemId())
                && order.getTradeMode() == request.tradeMode();
        if (!sameRequest) {
            throw new BusinessException(ErrorCode.ORDER_DUPLICATE_REQUEST,
                    "该请求标识已用于另一笔订单，请重新发起");
        }
        return OrderCreationResult.replayed(orderQueryService.assemble(order, buyerId));
    }

    private void requireCertified(User buyer) {
        if (buyer.getCertificationStatus() != CertificationStatus.APPROVED) {
            throw new BusinessException(ErrorCode.USER_CERTIFICATION_REQUIRED, "下单需要先通过校园认证");
        }
    }

    private void appendEvent(Order order, OrderAction action, OrderStatus from, OrderStatus to,
                            Long operatorId, String remark, Instant now) {
        orderEventRepository.save(OrderEvent.of(
                order.getId(), action, from, to, operatorId, remark, RequestIds.current(), now));
    }

    private void publish(Long userId, NotificationType type, String title, String content, Long orderId) {
        eventPublisher.publishEvent(new NotificationMessage(
                userId, type, title, content, NotificationMessage.BIZ_TYPE_ORDER, orderId));
    }

    private Order requireOrderForUpdate(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在"));
    }

    private Item requireItemForUpdate(Long itemId) {
        return itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在"));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "用户不存在"));
    }

    /** 商品首图：{@code sort_no} 最小的那张；没有图片时为 null（快照允许空图）。 */
    private String coverImageUrl(Long itemId) {
        List<ItemImage> images = itemImageRepository.findByItemIdOrderBySortNoAsc(itemId);
        return images.isEmpty() ? null : images.get(0).getImageUrl();
    }

    /**
     * 订单号：UTC 时间戳 + 随机后缀。
     *
     * <p>唯一索引是最终防线，随机后缀让「同一秒多笔」不撞号；格式固定 2 + 14 + 6 个字符，
     * 远小于列宽 64。
     */
    private String nextOrderNo(Instant now) {
        StringBuilder builder = new StringBuilder(22).append("OD").append(ORDER_NO_TIME.format(now));
        for (int index = 0; index < ORDER_NO_RANDOM_LENGTH; index++) {
            builder.append(ORDER_NO_ALPHABET[random.nextInt(ORDER_NO_ALPHABET.length)]);
        }
        return builder.toString();
    }
}
