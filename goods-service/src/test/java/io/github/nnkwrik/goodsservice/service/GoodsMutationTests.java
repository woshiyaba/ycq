package io.github.nnkwrik.goodsservice.service;

import io.github.nnkwrik.goodsservice.dao.GoodsMapper;
import io.github.nnkwrik.goodsservice.dao.OrderMapper;
import io.github.nnkwrik.goodsservice.model.po.Goods;
import io.github.nnkwrik.goodsservice.model.po.GoodsComment;
import io.github.nnkwrik.goodsservice.model.po.PostExample;
import io.github.nnkwrik.goodsservice.service.impl.GoodsServiceImpl;
import io.github.nnkwrik.goodsservice.service.impl.PostServiceImpl;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GoodsMutationTests {
    private GoodsMapper mapper;
    private OrderMapper orders;
    private GoodsServiceImpl comments;
    private PostServiceImpl posts;
    private Goods goods;

    @Before
    public void setUp() {
        mapper = mock(GoodsMapper.class);
        orders = mock(OrderMapper.class);
        comments = new GoodsServiceImpl();
        posts = new PostServiceImpl();
        ReflectionTestUtils.setField(comments, "goodsMapper", mapper);
        ReflectionTestUtils.setField(comments, "orderMapper", orders);
        ReflectionTestUtils.setField(posts, "goodsMapper", mapper);
        ReflectionTestUtils.setField(posts, "orderMapper", orders);
        goods = new Goods();
        goods.setId(7);
        goods.setSellerId("seller");
        when(orders.lockGoods(7)).thenReturn(goods);
    }

    @Test
    public void computesReplyRecipientAndRejectsCrossProductRepliesAndOtherUsersDeletion() {
        comments.addComment(7, "buyer", 0, "forged-recipient", " 留言 ");
        verify(mapper).addComment(7, "buyer", 0, "seller", "留言");
        GoodsComment reply = new GoodsComment();
        reply.setId(22);
        reply.setGoodsId(7);
        reply.setUserId("reply-author");
        reply.setReplyCommentId(11);
        when(mapper.findComment(22)).thenReturn(reply);
        comments.addComment(7, "buyer", 22, "forged-recipient", "回复");
        verify(mapper).addComment(7, "buyer", 11, "reply-author", "回复");
        reply.setGoodsId(8);
        invalid(() -> comments.addComment(7, "buyer", 22, "reply-author", "跨商品"));
        invalid(() -> comments.deleteComment(22, "buyer"));
        verify(mapper, never()).deleteComment(anyInt(), anyString());
        reply.setGoodsId(7);
        comments.deleteComment(22, "reply-author");
        verify(mapper).deleteComment(22, "reply-author");
        goods.setIsDelete(true);
        invalid(() -> comments.addComment(7, "buyer", 0, null, "不存在的商品"));
        invalid(() -> comments.addComment(7, "buyer", 0, null, "  "));
    }

    @Test
    public void blocksOtherSellersAndOrdersInProgressAndUsesSoftDelete() {
        invalid(() -> posts.deleteGoods(7, "stranger"));
        when(orders.countActive(7)).thenReturn(1);
        invalid(() -> posts.setSelling(7, "seller", false));
        invalid(() -> posts.deleteGoods(7, "seller"));
        when(orders.countActive(7)).thenReturn(0);
        posts.setSelling(7, "seller", false);
        posts.deleteGoods(7, "seller");
        verify(mapper).setSelling(7, false);
        verify(mapper).deleteGoods(7);
        verify(orders, never()).completeGoods(anyInt(), anyString());
    }

    @Test
    public void validatesMoneyAndRejectsClientManagedFieldsWhenEditing() {
        PostExample post = new PostExample();
        post.setName(" 商品 ");
        post.setDesc("描述");
        post.setCategoryId(2);
        post.setRegionId(3);
        post.setRegion("运城 盐湖区");
        post.setPrice(-1D);
        post.setAbleMeet(true);
        post.setImages(Collections.singletonList("https://example.com/image.jpg"));
        when(mapper.categoryExists(2)).thenReturn(true);
        when(mapper.regionExists(3)).thenReturn(true);
        invalid(() -> posts.postGoods(post));
        post.setPrice(1.001D);
        invalid(() -> posts.postGoods(post));
        post.setPrice(12.34D);
        post.setSellerId("forged");
        post.setId(99);
        posts.updateGoods(7, "seller", post);
        assertEquals(Integer.valueOf(7), post.getId());
        assertEquals("seller", post.getSellerId());
        assertEquals("商品", post.getName());
        verify(mapper).updateGoods(post);
        verify(mapper).deleteGallery(7);
        verify(mapper, never()).addGoods(any(Goods.class));
    }

    private void invalid(Runnable action) {
        try {
            action.run();
            fail("Expected validation or ownership rejection");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
