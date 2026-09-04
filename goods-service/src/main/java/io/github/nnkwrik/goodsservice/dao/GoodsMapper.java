package io.github.nnkwrik.goodsservice.dao;

import io.github.nnkwrik.goodsservice.model.po.Goods;
import io.github.nnkwrik.goodsservice.model.po.GoodsComment;
import io.github.nnkwrik.goodsservice.model.po.GoodsGallery;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * @author nnkwrik
 * @date 18/11/16 19:15
 */
@Mapper
public interface GoodsMapper {

    /**
     * 热度算法
     * Score = (1 * click + 10 * want + 500) / e^(elapsed days / 10)
     *       = (1 * click + 10 * want + 500) * e^(-elapsed seconds / 864000)
     */
    String popular_score = "(1 * browse_count + 10 * want_count + 500) * exp(-greatest(timestampdiff(second, last_edit, now()), 0) / 864000.0)";


    /**
     * 列出所有简单商品信息
     *
     * @return
     */
    @Select("select id, `name`, primary_pic_url, price\n" +
            "from goods\n" +
            "where is_selling = 1 and is_delete = 0\n" +
            "order by " + popular_score + " desc")
    List<Goods> findSimpleGoods();

    /**
     * 根据分类id列出分类下的所有简单商品信息
     *
     * @return
     */
    @Select("select id, `name`, primary_pic_url, price\n" +
            "from goods\n" +
            "where category_id = #{cateId}\n" +
            "and is_selling = 1 and is_delete = 0\n" +
            "order by " + popular_score + " desc")
    List<Goods> findSimpleGoodsByCateId(@Param("cateId") int cateId);

    /**
     * 通过商品id查找商品的详细信息
     *
     * @param goodsId
     * @return
     */
    @Select("select * from goods where id = #{goodsId}")
    Goods findDetailGoodsByGoodsId(@Param("goodsId") int goodsId);

    /**
     * 通过商品id查找该商品关联的图片
     *
     * @param goodsId
     * @return
     */
    @Select("select id, img_url\n" +
            "from goods_gallery\n" +
            "where goods_id = #{goodsId}")
    List<GoodsGallery> findGalleryByGoodsId(@Param("goodsId") int goodsId);


    /**
     * 查找与该商品位于同一个子分类的简单商品信息
     *
     * @param goodsId
     * @return
     */
    @Select("select id, `name`, primary_pic_url, price\n" +
            "from goods\n" +
            "where category_id = (select category_id from goods where id = #{goodsId})\n" +
            "  and id != #{goodsId}\n" +
            "  and is_selling = 1\n" +
            "  and is_delete = 0\n" +
            "order by " + popular_score + " desc")
    List<Goods> findSimpleGoodsInSameCate(@Param("goodsId") int goodsId);


    /**
     * 查找与该商品位于同一个父分类的简单商品信息
     *
     * @param goodsId
     * @return
     */
    @Select("select id, `name`, primary_pic_url, price\n" +
            "from goods\n" +
            "where category_id in (select bar.id\n" +
            "                      from goods\n" +
            "                             inner join category as foo on foo.id = goods.category_id\n" +
            "                             inner join category as bar on foo.parent_id = bar.parent_id\n" +
            "                      where goods.id = #{goodsId})\n" +
            "  and id != #{goodsId}\n" +
            "  and is_selling = 1\n" +
            "  and is_delete = 0\n" +
            "order by " + popular_score + " desc")
    List<Goods> findSimpleGoodsInSameParentCate(@Param("goodsId") int goodsId);

    @Update("update goods set browse_count = browse_count + #{add} where id = #{goodsId}")
    void addBrowseCount(@Param("goodsId") int id, @Param("add") int add);


    @Select("select id, user_id, if(is_delete, '该留言已删除', content) as content, create_time, is_delete\n" +
            "from goods_comment\n" +
            "where reply_comment_id = 0\n" +
            "  and (is_delete = false or exists (select 1 from goods_comment replies where replies.reply_comment_id = goods_comment.id and replies.is_delete = false))\n" +
            "  and goods_id = #{goodsId}\n" +
            "order by create_time asc")
    List<GoodsComment> findBaseComment(@Param("goodsId") int goodsId);


    @Select("select id, user_id, reply_user_id, content, create_time\n" +
            "from goods_comment\n" +
            "where reply_comment_id = #{reply_comment_id}\n" +
            "  and is_delete = false\n" +
            "order by create_time asc")
    List<GoodsComment> findReplyComment(@Param("reply_comment_id") int commentId);

    @Insert("insert into goods_comment (goods_id, user_id, reply_comment_id, reply_user_id, content)\n" +
            "values (#{goods_id}, #{user_id}, #{reply_comment_id}, #{reply_user_id}, #{content})")
    void addComment(@Param("goods_id") int goodsId,
                    @Param("user_id") String userId,
                    @Param("reply_comment_id") int replyCommentId,
                    @Param("reply_user_id") String replyUserId,
                    @Param("content") String content);


    @Insert("insert into goods (category_id,\n" +
            "                   seller_id,\n" +
            "                   name,\n" +
            "                   price,\n" +
            "                   market_price,\n" +
            "                   postage,\n" +
            "                   primary_pic_url,\n" +
            "                   `desc`,\n" +
            "                   region_id,\n" +
            "                   region,\n" +
            "                   able_express,\n" +
            "                   able_meet,\n" +
            "                   able_self_take)\n" +
            "values (#{categoryId},#{sellerId},#{name},#{price}," +
            "#{marketPrice},#{postage}," +
            "#{primaryPicUrl}," +
            "#{desc}," +
            "#{regionId},#{region},#{ableExpress},#{ableMeet},#{ableSelfTake})")
    @SelectKey(resultType = Integer.class, before = false, keyProperty = "id", statement = "SELECT LAST_INSERT_ID()")
    void addGoods(Goods goods);

    @Insert({
            "<script>",
            "INSERT INTO goods_gallery",
            "(goods_id, img_url)",
            "VALUES" +
                    "<foreach item='item' collection='galleryList' open='' separator=',' close=''>" +
                    "(" +
                    "#{item.goodsId},#{item.imgUrl}" +
                    ")" +
                    "</foreach>",
            "</script>"})
    void addGalleryList(@Param("galleryList") List<GoodsGallery> galleryList);

    /**
     * 用户卖出的商品
     * @param sellerId
     * @return
     */
    @Select("select count(*) from goods where seller_id = #{seller_id} and sold_time is not null")
    Integer getSellerHistory(@Param("seller_id") String sellerId);

    /**
     * 确认是用户发布的商品
     * @param goodsId
     * @param userId
     * @return
     */
    @Select("SELECT EXISTS(SELECT 1 FROM goods WHERE seller_id=#{seller_id} and id=#{goods_id})")
    Boolean validateSeller(@Param("goods_id") int goodsId, @Param("seller_id") String userId);

    @Update("update goods set is_delete = true, is_selling = false, last_edit = now() where id = #{goods_id}")
    void deleteGoods(@Param("goods_id") int goodsId);

    @Select("select * from goods_comment where id = #{id}")
    GoodsComment findComment(int id);

    @Update("update goods_comment set is_delete=true where id=#{id} and user_id=#{userId}")
    int deleteComment(@Param("id") int id, @Param("userId") String userId);

    @Select("select exists(select 1 from category where id=#{id})")
    boolean categoryExists(int id);

    @Select("select exists(select 1 from region where id=#{id})")
    boolean regionExists(int id);

    @Update("update goods set category_id=#{categoryId}, name=#{name}, price=#{price}, market_price=#{marketPrice}, "
            + "postage=#{postage}, primary_pic_url=#{primaryPicUrl}, `desc`=#{desc}, region_id=#{regionId}, region=#{region}, "
            + "able_express=#{ableExpress}, able_meet=#{ableMeet}, able_self_take=#{ableSelfTake}, last_edit=now() where id=#{id}")
    void updateGoods(Goods goods);

    @Delete("delete from goods_gallery where goods_id=#{goodsId}")
    void deleteGallery(int goodsId);

    @Update("update goods set is_selling=#{isSelling}, last_edit=now() where id=#{goodsId}")
    void setSelling(@Param("goodsId") int goodsId, @Param("isSelling") boolean isSelling);

    @Select("select * from goods where seller_id=#{userId} and is_delete=0 order by last_edit desc, id desc")
    List<Goods> findManageGoods(String userId);

}
