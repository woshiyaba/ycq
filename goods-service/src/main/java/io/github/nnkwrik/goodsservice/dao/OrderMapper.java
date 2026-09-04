package io.github.nnkwrik.goodsservice.dao;

import io.github.nnkwrik.goodsservice.model.po.Goods;
import io.github.nnkwrik.goodsservice.model.po.Order;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface OrderMapper {
    String DETAILS = "select o.*, r.rating as review_rating, r.content as review_content, "
            + "r.created_at as review_created_at from goods_order o "
            + "left join goods_order_review r on r.order_id = o.id ";

    @Select("select * from goods where id = #{id} for update")
    Goods lockGoods(int id);

    @Select(DETAILS + "where o.id = #{id}")
    Order find(long id);

    @Select(DETAILS + "where o.id = #{id} for update")
    Order lock(long id);

    @Select(DETAILS + "where o.buyer_id = #{buyerId} and o.request_id = #{requestId} for update")
    Order findRequest(@Param("buyerId") String buyerId, @Param("requestId") String requestId);

    @Select("select count(*) from goods_order where goods_id = #{goodsId} and status != 'CANCELLED'")
    int countActive(int goodsId);

    @Insert("insert into goods_order (order_no, goods_id, goods_name, goods_image, buyer_id, seller_id, "
            + "amount, postage, delivery_method, address_name, address_phone, address_region, address_detail, request_id, status) "
            + "values (#{orderNo}, #{goodsId}, #{goodsName}, #{goodsImage}, #{buyerId}, #{sellerId}, "
            + "#{amount}, #{postage}, #{deliveryMethod}, #{addressName}, #{addressPhone}, #{addressRegion}, "
            + "#{addressDetail}, #{requestId}, 'PENDING')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Order order);

    @Select({"<script>", DETAILS,
            "where <choose><when test='role == &quot;seller&quot;'>o.seller_id</when><otherwise>o.buyer_id</otherwise></choose> = #{userId}",
            "<if test='status != null and status != &quot;&quot;'>and o.status = #{status}</if>",
            "order by o.id desc limit #{size} offset #{offset}", "</script>"})
    List<Order> list(@Param("userId") String userId, @Param("role") String role,
                     @Param("status") String status, @Param("offset") int offset, @Param("size") int size);

    @Select({"<script>", "select count(*) from goods_order o where",
            "<choose><when test='role == &quot;seller&quot;'>o.seller_id</when><otherwise>o.buyer_id</otherwise></choose> = #{userId}",
            "<if test='status != null and status != &quot;&quot;'>and o.status = #{status}</if>", "</script>"})
    long count(@Param("userId") String userId, @Param("role") String role, @Param("status") String status);

    @Update("update goods_order set status='PAID', paid_at=now() where id=#{id} and status='PENDING'")
    int pay(long id);

    @Update("update goods set is_selling=0, buyer_id=#{buyerId} where id=#{goodsId} "
            + "and is_selling=1 and is_delete=0 and sold_time is null and (buyer_id is null or buyer_id='0' or buyer_id='')")
    int reserveGoods(@Param("goodsId") int goodsId, @Param("buyerId") String buyerId);

    @Update("update goods_order set status='CANCELLED' where id=#{id} and status='PENDING'")
    int cancel(long id);

    @Update("update goods_order set status='SHIPPED', tracking_no=#{trackingNo}, shipped_at=now() "
            + "where id=#{id} and status='PAID'")
    int ship(@Param("id") long id, @Param("trackingNo") String trackingNo);

    @Update("update goods_order set status='COMPLETED', completed_at=now() where id=#{id} and status='SHIPPED'")
    int receive(long id);

    @Update("update goods set sold_time=now() where id=#{goodsId} and buyer_id=#{buyerId} and sold_time is null")
    void completeGoods(@Param("goodsId") int goodsId, @Param("buyerId") String buyerId);

    @Insert("insert into goods_order_review (order_id, buyer_id, seller_id, rating, content) "
            + "values (#{order.id}, #{order.buyerId}, #{order.sellerId}, #{review.rating}, #{review.content})")
    void review(@Param("order") Order order, @Param("review") Order.Review review);
}
