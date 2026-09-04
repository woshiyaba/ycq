package io.github.nnkwrik.goodsservice.controller;

import io.github.nnkwrik.common.dto.JWTUser;
import io.github.nnkwrik.common.dto.Response;
import io.github.nnkwrik.common.token.injection.JWT;
import io.github.nnkwrik.goodsservice.model.po.Category;
import io.github.nnkwrik.goodsservice.model.po.PostExample;
import io.github.nnkwrik.goodsservice.model.po.Region;
import io.github.nnkwrik.goodsservice.service.PostService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Collections;

/**
 * 发布商品相关api
 *
 * @author nnkwrik
 * @date 18/12/16 20:55
 */
@Slf4j
@RestController
@RequestMapping("/post")
public class PostController {

    @Autowired
    private PostService postService;

    /**
     * 发布商品
     *
     * @param post
     * @param user
     * @return
     */
    @PostMapping("/post")
    public Response postGoods(@RequestBody PostExample post,
                              @JWT(required = true) JWTUser user) {

        post.setSellerId(user.getOpenId());
        postService.postGoods(post);
        log.info("用户发布商品：用户昵称=【{}】，商品名=【{}】，{}张图片", user.getNickName(), post.getName(), post.getImages().size());

        return Response.ok(Collections.singletonMap("id", post.getId()));
    }

    /**
     * 删除已发布的商品
     *
     * @param goodsId
     * @param user
     * @return
     */
    @DeleteMapping("/delete/{goodsId}")
    public Response deleteGoods(@PathVariable int goodsId,
                                @JWT(required = true) JWTUser user) {
        postService.deleteGoods(goodsId, user.getOpenId());
        log.info("用户删除商品: 用户id=【{}】，商品Id=【{}】", user.getOpenId(), goodsId);
        return Response.ok();
    }

    /**
     * 获取发布商品时需要填选的发货地区
     *
     * @param regionId
     * @return
     */
    @GetMapping("/region/{regionId}")
    public Response getRegionList(@PathVariable("regionId") int regionId) {
        List<Region> regionList = postService.getRegionList(regionId);
        log.info("通过地区id=【{}】，搜索地区子列表。搜索到{}个结果", regionId, regionList.size());
        return Response.ok(regionList);

    }

    /**
     * 获取发布商品时需要填选的分类
     *
     * @param cateId
     * @return
     */
    @GetMapping("/category/{cateId}")
    public Response getPostCateList(@PathVariable("cateId") int cateId) {
        List<Category> cateList = postService.getCateList(cateId);
        log.info("通过分类id=【{}】，搜索分类子列表。搜索到{}个结果", cateId, cateList.size());
        return Response.ok(cateList);
    }
}
