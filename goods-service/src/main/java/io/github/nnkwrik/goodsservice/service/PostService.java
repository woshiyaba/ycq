package io.github.nnkwrik.goodsservice.service;

import io.github.nnkwrik.goodsservice.model.po.Category;
import io.github.nnkwrik.goodsservice.model.po.PostExample;
import io.github.nnkwrik.goodsservice.model.po.Region;

import java.util.List;
import java.util.Map;

/**
 * @author nnkwrik
 * @date 18/12/16 21:35
 */
public interface PostService {

    void postGoods(PostExample post);

    void deleteGoods(int goodsId, String userId);

    void updateGoods(int goodsId, String userId, PostExample post);

    void setSelling(int goodsId, String userId, Boolean isSelling);

    Map<String, Object> manageGoods(String userId, int page, int size);

    List<Region> getRegionList(int regionId);

    List<Category> getCateList(int cateId);
}
