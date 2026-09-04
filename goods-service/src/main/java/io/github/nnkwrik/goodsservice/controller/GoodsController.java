package io.github.nnkwrik.goodsservice.controller;

import fangxianyu.innerApi.user.UserClientHandler;
import io.github.nnkwrik.common.dto.JWTUser;
import io.github.nnkwrik.common.dto.Response;
import io.github.nnkwrik.common.dto.SimpleUser;
import io.github.nnkwrik.common.token.injection.JWT;
import io.github.nnkwrik.goodsservice.cache.BrowseCache;
import io.github.nnkwrik.goodsservice.model.po.Goods;
import io.github.nnkwrik.goodsservice.model.po.GoodsComment;
import io.github.nnkwrik.goodsservice.model.po.GoodsGallery;
import io.github.nnkwrik.goodsservice.model.po.PostExample;
import io.github.nnkwrik.goodsservice.model.vo.CategoryPageVo;
import io.github.nnkwrik.goodsservice.model.vo.CommentVo;
import io.github.nnkwrik.goodsservice.model.vo.GoodsDetailPageVo;
import io.github.nnkwrik.goodsservice.service.GoodsService;
import io.github.nnkwrik.goodsservice.service.PostService;
import io.github.nnkwrik.goodsservice.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 商品浏览相关api
 *
 * @author nnkwrik
 * @date 18/11/14 18:42
 */
@Slf4j
@RestController
@RequestMapping("/goods")
public class GoodsController {

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private PostService postService;

    @Autowired
    private UserService userService;

    @Autowired
    private BrowseCache browseCache;

    @Autowired
    private UserClientHandler userClientHandler;

    /**
     * 通过分类浏览商品,获取选定目录下的商品列表和同级的兄弟目录
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/category/index/{categoryId}")

    public Response<CategoryPageVo> getCategoryPage(@PathVariable("categoryId") int categoryId,
                                                    @RequestParam(value = "page", defaultValue = "1") int page,
                                                    @RequestParam(value = "size", defaultValue = "10") int size) {


        CategoryPageVo vo = goodsService.getGoodsAndBrotherCateById(categoryId, page, size);
        log.info("通过分类浏览商品 : 分类id = {},展示{}个商品", categoryId, vo.getGoodsList().size());

        return Response.ok(vo);
    }

    /**
     * 通过分类浏览商品,获取选定目录下的商品列表
     *
     * @param categoryId
     * @param page
     * @param size
     * @return
     */
    @GetMapping("/category/{categoryId}")
    public Response<Goods> getGoodsByCategory(@PathVariable("categoryId") int categoryId,
                                              @RequestParam(value = "page", defaultValue = "1") int page,
                                              @RequestParam(value = "size", defaultValue = "10") int size) {

        List<Goods> goodsList = goodsService.getGoodsByCateId(categoryId, page, size);
        log.info("通过分类浏览商品 : 分类id = {},展示{}个商品", categoryId, goodsList.size());
        return Response.ok(goodsList);

    }

    /**
     * 获取商品的详细信息,包括:商品信息,商品图片,商品评论,卖家信息,用户是否收藏了该商品
     *
     * @param goodsId
     * @param jwtUser
     * @return
     */
    @GetMapping("/detail/{goodsId}")
    public Response<GoodsDetailPageVo> getGoodsDetail(@PathVariable("goodsId") int goodsId,
                                                      @JWT JWTUser jwtUser) {
        //获取商品详情
        Goods goods = goodsService.getGoodsDetail(goodsId);
        if (goods == null || Boolean.TRUE.equals(goods.getIsDelete())) {
            return Response.fail(Response.GOODS_IN_NOT_EXIST, "商品不存在或已删除");
        }
        browseCache.add(goodsId);
        //获取买家信息
        SimpleUser seller = userClientHandler.getSimpleUser(goods.getSellerId());
        if (seller == null) {
            log.info("搜索goodsId = 【{}】的详情时出错", goodsId);
            return Response.fail(Response.USER_IS_NOT_EXIST, "无法搜索到商品卖家的信息");
        }
        //卖家出售过的商品数
        int sellerHistory = goodsService.getSellerHistory(goods.getSellerId());

        List<GoodsGallery> goodsGallery = goodsService.getGoodsGallery(goodsId);
        List<CommentVo> comment = goodsService.getGoodsComment(goodsId);

        //用户是否收藏
        boolean userHasCollect = false;
        if (jwtUser != null)
            userHasCollect = userService.userHasCollect(jwtUser.getOpenId(), goodsId);

        GoodsDetailPageVo vo = new GoodsDetailPageVo(goods, goodsGallery, seller, sellerHistory, comment, userHasCollect);
        log.info("浏览商品详情 : 商品id={}，商品名={}", vo.getInfo().getId(), vo.getInfo().getName());

        return Response.ok(vo);
    }

    /**
     * 获取与id商品相关的商品
     *
     * @param goodsId
     * @return
     */
    @GetMapping("/related/{goodsId}")
    public Response<List<Goods>> getGoodsRelated(@PathVariable("goodsId") int goodsId,
                                                 @RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "size", defaultValue = "10") int size) {
        List<Goods> goodsList = goodsService.getGoodsRelated(goodsId, page, size);
        log.info("获取与 goodsId=[{}] 相关的商品 : 展示{}个商品", goodsId, goodsList.size());

        return Response.ok(goodsList);
    }

    /**
     * 发表评论
     *
     * @param goodsId
     * @param comment
     * @param user
     * @return
     */
    @PostMapping("/comment/post/{goodsId}")
    public Response postComment(@PathVariable("goodsId") int goodsId,
                                @RequestBody GoodsComment comment,
                                @JWT(required = true) JWTUser user) {
        goodsService.addComment(goodsId, user.getOpenId(), comment.getReplyCommentId() == null ? 0 : comment.getReplyCommentId(),
                comment.getReplyUserId(), comment.getContent());
        return Response.ok();

    }

    @DeleteMapping("/comments/{id}")
    public Response deleteComment(@PathVariable int id, @JWT(required = true) JWTUser user) {
        goodsService.deleteComment(id, user.getOpenId());
        return Response.ok();
    }

    @GetMapping("/manage")
    public Response manage(@JWT(required = true) JWTUser user,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "10") int size) {
        return Response.ok(postService.manageGoods(user.getOpenId(), page, size));
    }

    @PutMapping("/manage/{id}")
    public Response edit(@PathVariable int id, @RequestBody PostExample post, @JWT(required = true) JWTUser user) {
        postService.updateGoods(id, user.getOpenId(), post);
        return Response.ok();
    }

    @PutMapping("/manage/{id}/status")
    public Response status(@PathVariable int id, @RequestBody Map<String, Boolean> body, @JWT(required = true) JWTUser user) {
        postService.setSelling(id, user.getOpenId(), body.get("isSelling"));
        return Response.ok();
    }

}
