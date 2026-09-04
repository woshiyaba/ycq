package io.github.nnkwrik.goodsservice.service;

import io.github.nnkwrik.goodsservice.dao.OrderMapper;
import io.github.nnkwrik.goodsservice.model.po.Goods;
import io.github.nnkwrik.goodsservice.model.po.Order;
import org.apache.ibatis.session.Configuration;
import org.junit.Before;
import org.junit.Test;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class OrderServiceTests {
    private OrderMapper mapper;
    private OrderService service;
    private Goods goods;
    private AtomicReference<Order> saved;

    @Before
    public void setUp() {
        mapper = mock(OrderMapper.class);
        service = new OrderService(mapper);
        goods = new Goods();
        goods.setId(7);
        goods.setSellerId("seller");
        goods.setName("二手相机");
        goods.setPrice(99.99);
        goods.setPostage(1.24);
        goods.setIsSelling(true);
        goods.setAbleExpress(true);
        goods.setAbleMeet(true);
        saved = new AtomicReference<>();
        when(mapper.lockGoods(7)).thenReturn(goods);
        when(mapper.find(anyLong())).thenAnswer(call -> saved.get());
        when(mapper.lock(anyLong())).thenAnswer(call -> saved.get());
        doAnswer(call -> {
            Order order = call.getArgument(0);
            order.setId(1L);
            saved.set(order);
            return null;
        }).when(mapper).insert(any(Order.class));
    }

    @Test
    public void calculatesServerPriceAndSnapshotsAddressWithRequestIdempotency() {
        Order.Create request = request("express", "EXPRESS");
        Order.Address address = new Order.Address();
        address.setName(" 小张 ");
        address.setPhone("13800138000");
        address.setRegion("山西 运城 盐湖");
        address.setDetail("人民路 10 号");
        request.setAddress(address);
        invalid(() -> service.create("seller", request), "自己");
        Order order = service.create("buyer", request);
        assertEquals(new BigDecimal("101.23"), order.getAmount());
        assertEquals(new BigDecimal("1.24"), order.getPostage());
        assertEquals("小张", order.getAddress().getName());
        when(mapper.findRequest("buyer", "express")).thenReturn(order);
        assertSame(order, service.create("buyer", request));
        address.setDetail("新地址");
        assertEquals("人民路 10 号", order.getAddress().getDetail());
        invalid(() -> service.create("buyer", request), "请求编号");
        verify(mapper, times(1)).insert(any(Order.class));
    }

    @Test
    public void enforcesParticipantsAndCompletesPaymentDeliveryAndReviewOnlyOnce() {
        Order order = service.create("buyer", request("meet", "MEET"));
        assertEquals(new BigDecimal("99.99"), order.getAmount());
        assertEquals(new BigDecimal("0.00"), order.getPostage());
        when(mapper.reserveGoods(7, "buyer")).thenReturn(1);
        when(mapper.pay(1L)).thenAnswer(call -> { order.setStatus("PAID"); return 1; });
        when(mapper.ship(1L, "")).thenAnswer(call -> { order.setStatus("SHIPPED"); return 1; });
        when(mapper.receive(1L)).thenAnswer(call -> { order.setStatus("COMPLETED"); return 1; });
        doAnswer(call -> {
            Order.Review review = call.getArgument(1);
            order.setReviewRating(review.getRating());
            order.setReviewContent(review.getContent());
            return null;
        }).when(mapper).review(eq(order), any(Order.Review.class));

        invalid(() -> service.detail(1L, "stranger"), "无权");
        invalid(() -> service.pay(1L, "seller"), "买家");
        invalid(() -> service.ship(1L, "seller", ""), "尚未付款");
        assertEquals("PAID", service.pay(1L, "buyer").getStatus());
        assertEquals("PAID", service.pay(1L, "buyer").getStatus());
        invalid(() -> service.cancel(1L, "buyer"), "待付款");
        invalid(() -> service.ship(1L, "buyer", ""), "卖家");
        invalid(() -> service.receive(1L, "buyer"), "尚未发货");
        Order.Review review = new Order.Review();
        review.setRating(5);
        review.setContent("发货很快");
        invalid(() -> service.review(1L, "buyer", review), "确认收货");
        service.ship(1L, "seller", null);
        service.ship(1L, "seller", null);
        invalid(() -> service.receive(1L, "seller"), "买家");
        service.receive(1L, "buyer");
        service.receive(1L, "buyer");
        service.review(1L, "buyer", review);
        service.review(1L, "buyer", review);
        assertEquals(Integer.valueOf(5), order.getReview().getRating());
        verify(mapper, times(1)).reserveGoods(7, "buyer");
        verify(mapper, times(1)).pay(1L);
        verify(mapper, times(1)).ship(1L, "");
        verify(mapper, times(1)).receive(1L);
        verify(mapper, times(1)).completeGoods(7, "buyer");
        verify(mapper, times(1)).review(eq(order), any(Order.Review.class));
    }

    @Test
    public void cancellationIsIdempotentAndDoesNotPayOrMarkGoodsSold() {
        Order order = service.create("buyer", request("cancel", "MEET"));
        when(mapper.cancel(1L)).thenAnswer(call -> { order.setStatus("CANCELLED"); return 1; });
        service.cancel(1L, "seller");
        service.cancel(1L, "buyer");
        invalid(() -> service.pay(1L, "buyer"), "不能支付");
        verify(mapper, times(1)).cancel(1L);
        verify(mapper, never()).reserveGoods(anyInt(), anyString());
        verify(mapper, never()).completeGoods(anyInt(), anyString());
    }

    @Test
    public void rejectsUnavailableProductsAndFailedAtomicReservation() {
        goods.setIsSelling(false);
        invalid(() -> service.create("buyer", request("unlisted", "MEET")), "下架");
        goods.setIsSelling(true);
        goods.setAbleMeet(false);
        invalid(() -> service.create("buyer", request("delivery", "MEET")), "交付方式");
        goods.setAbleMeet(true);
        service.create("buyer", request("race-pay", "MEET"));
        when(mapper.reserveGoods(7, "buyer")).thenReturn(0);
        invalid(() -> service.pay(1L, "buyer"), "下架");
        verify(mapper, never()).pay(anyLong());
    }

    @Test
    public void concurrentBuyersCannotBothCreateAnActiveOrder() throws Exception {
        // Force both buyers past the availability read; emulate the database unique active_goods_id constraint.
        CyclicBarrier bothRead = new CyclicBarrier(2);
        when(mapper.countActive(7)).thenAnswer(call -> { bothRead.await(10, TimeUnit.SECONDS); return 0; });
        doAnswer(call -> {
            Order order = call.getArgument(0);
            order.setId(1L);
            if (!saved.compareAndSet(null, order)) throw new DuplicateKeyException("uk_active_goods");
            return null;
        }).when(mapper).insert(any(Order.class));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> tryCreate("buyer1"));
            Future<Boolean> second = pool.submit(() -> tryCreate("buyer2"));
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertNotNull(saved.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void bindsOrderListQueriesToTheSelectedParticipantRole() {
        Configuration configuration = new Configuration();
        configuration.addMapper(OrderMapper.class);
        Map<String, Object> params = new HashMap<>();
        params.put("role", "seller");
        params.put("userId", "seller-id");
        params.put("status", "PAID");
        params.put("offset", 0);
        params.put("size", 10);
        String statement = OrderMapper.class.getName() + ".list";
        String sql = configuration.getMappedStatement(statement).getBoundSql(params).getSql();
        assertTrue(sql.contains("o.seller_id"));
        assertTrue(sql.contains("o.status"));
        assertFalse(sql.contains("seller-id"));
        params.put("role", "buyer");
        params.put("status", "");
        sql = configuration.getMappedStatement(statement).getBoundSql(params).getSql();
        assertTrue(sql.contains("o.buyer_id"));
        assertFalse(sql.contains("o.status"));
    }

    private boolean tryCreate(String buyer) {
        try {
            service.create(buyer, request(buyer, "MEET"));
            return true;
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("已有交易"));
            return false;
        }
    }

    private Order.Create request(String id, String delivery) {
        Order.Create request = new Order.Create();
        request.setGoodsId(7);
        request.setRequestId(id);
        request.setDeliveryMethod(delivery);
        return request;
    }

    private void invalid(Runnable action, String message) {
        try {
            action.run();
            fail("Expected rejection: " + message);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(message));
        }
    }
}
