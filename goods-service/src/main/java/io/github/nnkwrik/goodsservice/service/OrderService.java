package io.github.nnkwrik.goodsservice.service;

import io.github.nnkwrik.goodsservice.dao.OrderMapper;
import io.github.nnkwrik.goodsservice.model.po.Goods;
import io.github.nnkwrik.goodsservice.model.po.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class OrderService {
    private final OrderMapper mapper;

    public OrderService(OrderMapper mapper) {
        this.mapper = mapper;
    }

    public Order create(String buyerId, Order.Create request) {
        require(request != null && request.getGoodsId() != null && request.getGoodsId() > 0, "请选择商品");
        request.setRequestId(text(request.getRequestId(), "请求编号", 64));
        String delivery = request.getDeliveryMethod();
        require(Arrays.asList("EXPRESS", "MEET", "SELF_TAKE").contains(delivery), "请选择有效的交付方式");
        if ("EXPRESS".equals(delivery)) {
            Order.Address address = request.getAddress();
            require(address != null, "请填写收货地址");
            address.setName(text(address.getName(), "收货人", 40));
            address.setPhone(text(address.getPhone(), "联系电话", 24));
            require(address.getPhone().matches("[+0-9() -]{6,24}"), "联系电话格式不正确");
            address.setRegion(text(address.getRegion(), "所在地区", 120));
            address.setDetail(text(address.getDetail(), "详细地址", 255));
        } else {
            request.setAddress(null);
        }

        // Every order mutation locks the product first, then the order, so competing buyers serialize in MySQL.
        Goods goods = mapper.lockGoods(request.getGoodsId());
        Order existing = mapper.findRequest(buyerId, request.getRequestId());
        if (existing != null) return sameRequest(existing, request);
        require(goods != null && !Boolean.TRUE.equals(goods.getIsDelete()), "商品不存在或已删除");
        require(!buyerId.equals(goods.getSellerId()), "不能购买自己发布的商品");
        require(Boolean.TRUE.equals(goods.getIsSelling()) && goods.getSoldTime() == null
                && (goods.getBuyerId() == null || goods.getBuyerId().isEmpty() || "0".equals(goods.getBuyerId())), "商品已售出或已下架");
        require(mapper.countActive(goods.getId()) == 0, "商品已有交易，请稍后再试");
        require(("EXPRESS".equals(delivery) && Boolean.TRUE.equals(goods.getAbleExpress()))
                || ("MEET".equals(delivery) && Boolean.TRUE.equals(goods.getAbleMeet()))
                || ("SELF_TAKE".equals(delivery) && Boolean.TRUE.equals(goods.getAbleSelfTake())), "商品不支持此交付方式");

        Order order = new Order();
        order.setOrderNo("YC" + UUID.randomUUID().toString().replace("-", ""));
        order.setGoodsId(goods.getId());
        order.setGoodsName(goods.getName());
        order.setGoodsImage(goods.getPrimaryPicUrl() == null ? "" : goods.getPrimaryPicUrl());
        order.setBuyerId(buyerId);
        order.setSellerId(goods.getSellerId());
        order.setDeliveryMethod(delivery);
        order.setRequestId(request.getRequestId());
        order.setStatus("PENDING");
        BigDecimal price = money(goods.getPrice());
        require(price.signum() > 0, "商品价格必须大于零");
        BigDecimal postage = "EXPRESS".equals(delivery) ? money(goods.getPostage()) : BigDecimal.ZERO.setScale(2);
        order.setPostage(postage);
        order.setAmount(price.add(postage));
        if (request.getAddress() != null) {
            order.setAddressName(request.getAddress().getName());
            order.setAddressPhone(request.getAddress().getPhone());
            order.setAddressRegion(request.getAddress().getRegion());
            order.setAddressDetail(request.getAddress().getDetail());
        }
        try {
            mapper.insert(order);
        } catch (DuplicateKeyException e) {
            existing = mapper.findRequest(buyerId, request.getRequestId());
            if (existing != null) return sameRequest(existing, request);
            throw new IllegalArgumentException("商品已有交易，请刷新后重试");
        }
        return mapper.find(order.getId());
    }

    public Map<String, Object> list(String userId, String role, String status, int page, int size) {
        require("buyer".equals(role) || "seller".equals(role), "订单类型不正确");
        require(page >= 1 && page <= 1000000 && size >= 1 && size <= 50, "分页参数不正确");
        require(status == null || status.isEmpty()
                || Arrays.asList("PENDING", "PAID", "SHIPPED", "COMPLETED", "CANCELLED").contains(status), "订单状态不正确");
        List<Order> items = mapper.list(userId, role, status, (page - 1) * size, size);
        long total = mapper.count(userId, role, status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("hasMore", (long) page * size < total);
        return result;
    }

    public Order detail(long id, String userId) {
        Order order = mapper.find(id);
        participant(order, userId);
        return order;
    }

    public Order pay(long id, String userId) {
        Order order = lock(id, userId);
        require(userId.equals(order.getBuyerId()), "只有买家可以支付");
        if (Arrays.asList("PAID", "SHIPPED", "COMPLETED").contains(order.getStatus())) return order;
        require("PENDING".equals(order.getStatus()), "此订单不能支付");
        require(mapper.reserveGoods(order.getGoodsId(), userId) == 1, "商品已售出或已下架");
        // This is the simulated payment channel: a valid order always succeeds, without calling WeChat Pay.
        require(mapper.pay(id) == 1, "订单状态已变化，请刷新");
        return mapper.find(id);
    }

    public Order cancel(long id, String userId) {
        Order order = lock(id, userId);
        if ("CANCELLED".equals(order.getStatus())) return order;
        require("PENDING".equals(order.getStatus()), "只有待付款订单可以取消");
        require(mapper.cancel(id) == 1, "订单状态已变化，请刷新");
        return mapper.find(id);
    }

    public Order ship(long id, String userId, String trackingNo) {
        Order order = lock(id, userId);
        require(userId.equals(order.getSellerId()), "只有卖家可以确认发货或交付");
        if ("SHIPPED".equals(order.getStatus()) || "COMPLETED".equals(order.getStatus())) return order;
        require("PAID".equals(order.getStatus()), "订单尚未付款，无法发货");
        trackingNo = trackingNo == null ? "" : trackingNo.trim();
        require(trackingNo.length() <= 80, "运单号不能超过 80 字");
        require(mapper.ship(id, trackingNo) == 1, "订单状态已变化，请刷新");
        return mapper.find(id);
    }

    public Order receive(long id, String userId) {
        Order order = lock(id, userId);
        require(userId.equals(order.getBuyerId()), "只有买家可以确认收货");
        if ("COMPLETED".equals(order.getStatus())) return order;
        require("SHIPPED".equals(order.getStatus()), "卖家尚未发货或确认交付");
        require(mapper.receive(id) == 1, "订单状态已变化，请刷新");
        mapper.completeGoods(order.getGoodsId(), userId);
        return mapper.find(id);
    }

    public Order review(long id, String userId, Order.Review review) {
        require(review != null && review.getRating() != null && review.getRating() >= 1 && review.getRating() <= 5, "请选择 1 至 5 星评价");
        review.setContent(review.getContent() == null ? "" : review.getContent().trim());
        require(review.getContent().length() <= 1000, "评价不能超过 1000 字");
        Order order = lock(id, userId);
        require(userId.equals(order.getBuyerId()), "只有买家可以评价");
        require("COMPLETED".equals(order.getStatus()), "确认收货后才能评价");
        if (order.getReview() != null) return order;
        mapper.review(order, review);
        return mapper.find(id);
    }

    private Order lock(long id, String userId) {
        Order order = detail(id, userId);
        mapper.lockGoods(order.getGoodsId());
        order = mapper.lock(id);
        participant(order, userId);
        return order;
    }

    private void participant(Order order, String userId) {
        require(order != null, "订单不存在");
        require(userId.equals(order.getBuyerId()) || userId.equals(order.getSellerId()), "无权操作该订单");
    }

    private Order sameRequest(Order existing, Order.Create request) {
        require(Objects.equals(existing.getGoodsId(), request.getGoodsId())
                && Objects.equals(existing.getDeliveryMethod(), request.getDeliveryMethod())
                && Objects.equals(existing.getAddress(), request.getAddress()), "请求编号已用于其他订单，请重新提交");
        return existing;
    }

    private BigDecimal money(Double value) {
        require(value != null && Double.isFinite(value) && value >= 0 && value <= 99999999.99, "商品金额不正确");
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String text(String value, String label, int max) {
        require(value != null && !value.trim().isEmpty() && value.trim().length() <= max, label + "不能为空且不能超过 " + max + " 字");
        return value.trim();
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
