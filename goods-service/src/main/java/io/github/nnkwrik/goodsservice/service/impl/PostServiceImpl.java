package io.github.nnkwrik.goodsservice.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.github.nnkwrik.goodsservice.dao.GoodsMapper;
import io.github.nnkwrik.goodsservice.dao.OrderMapper;
import io.github.nnkwrik.goodsservice.dao.PostMapper;
import io.github.nnkwrik.goodsservice.model.po.Category;
import io.github.nnkwrik.goodsservice.model.po.Goods;
import io.github.nnkwrik.goodsservice.model.po.GoodsGallery;
import io.github.nnkwrik.goodsservice.model.po.PostExample;
import io.github.nnkwrik.goodsservice.model.po.Region;
import io.github.nnkwrik.goodsservice.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

/**
 * @author nnkwrik
 * @date 18/12/16 21:37
 */
@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Override
    @Transactional
    public void postGoods(PostExample post) {
        validate(post);
        post.setPrimaryPicUrl(post.getImages().get(0));
        goodsMapper.addGoods(post);
        saveGallery(post);
    }

    @Override
    @Transactional
    public void deleteGoods(int goodsId, String userId) {
        editable(goodsId, userId);
        goodsMapper.deleteGoods(goodsId);
    }

    @Override
    @Transactional
    public void updateGoods(int goodsId, String userId, PostExample post) {
        editable(goodsId, userId);
        validate(post);
        post.setId(goodsId);
        post.setSellerId(userId);
        post.setPrimaryPicUrl(post.getImages().get(0));
        goodsMapper.updateGoods(post);
        goodsMapper.deleteGallery(goodsId);
        saveGallery(post);
    }

    @Override
    @Transactional
    public void setSelling(int goodsId, String userId, Boolean isSelling) {
        require(isSelling != null, "请指定上架或下架");
        editable(goodsId, userId);
        goodsMapper.setSelling(goodsId, isSelling);
    }

    @Override
    public Map<String, Object> manageGoods(String userId, int page, int size) {
        require(page >= 1 && page <= 1000000 && size >= 1 && size <= 50, "分页参数不正确");
        PageHelper.startPage(page, size);
        List<Goods> items = goodsMapper.findManageGoods(userId);
        long total = new PageInfo<>(items).getTotal();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("hasMore", (long) page * size < total);
        return result;
    }

    private void editable(int goodsId, String userId) {
        Goods goods = orderMapper.lockGoods(goodsId);
        require(goods != null && !Boolean.TRUE.equals(goods.getIsDelete()), "商品不存在或已删除");
        require(userId.equals(goods.getSellerId()), "只能管理自己发布的商品");
        require(goods.getSoldTime() == null && (goods.getBuyerId() == null || "0".equals(goods.getBuyerId()) || goods.getBuyerId().isEmpty()), "已成交商品不能修改");
        require(orderMapper.countActive(goodsId) == 0, "商品已有交易，待付款订单取消后才可修改");
    }

    private void validate(PostExample post) {
        require(post != null, "请填写商品信息");
        post.setName(text(post.getName(), "商品名称", 100));
        post.setDesc(text(post.getDesc(), "商品描述", 5000));
        post.setRegion(text(post.getRegion(), "发货地区", 64));
        require(post.getCategoryId() != null && post.getCategoryId() > 0 && goodsMapper.categoryExists(post.getCategoryId()), "请选择有效的商品分类");
        require(post.getRegionId() != null && post.getRegionId() > 0 && goodsMapper.regionExists(post.getRegionId()), "请选择有效的发货地区");
        require(validMoney(post.getPrice()) && post.getPrice() > 0, "商品价格必须大于零且最多保留两位小数");
        if (post.getMarketPrice() == null) post.setMarketPrice(0D);
        if (post.getPostage() == null) post.setPostage(0D);
        require(validMoney(post.getMarketPrice()) && validMoney(post.getPostage()), "原价和运费必须为非负金额且最多保留两位小数");
        post.setAbleExpress(Boolean.TRUE.equals(post.getAbleExpress()));
        post.setAbleMeet(Boolean.TRUE.equals(post.getAbleMeet()));
        post.setAbleSelfTake(Boolean.TRUE.equals(post.getAbleSelfTake()));
        require(post.getAbleExpress() || post.getAbleMeet() || post.getAbleSelfTake(), "请选择至少一种交付方式");
        if (!post.getAbleExpress()) post.setPostage(0D);
        require(post.getImages() != null && !post.getImages().isEmpty() && post.getImages().size() <= 9, "请上传 1 至 9 张图片");
        for (String url : post.getImages()) {
            require(url != null && url.length() <= 255 && url.matches("https?://[^\\s]+"), "图片地址无效，请重新上传");
        }
    }

    private boolean validMoney(Double value) {
        return value != null && Double.isFinite(value) && value >= 0 && value <= 99999999.99
                && BigDecimal.valueOf(value).stripTrailingZeros().scale() <= 2;
    }

    private String text(String value, String label, int max) {
        require(value != null && !value.trim().isEmpty() && value.trim().length() <= max, label + "不能为空且不能超过 " + max + " 字");
        return value.trim();
    }

    private void require(boolean valid, String message) {
        if (!valid) throw new IllegalArgumentException(message);
    }

    private void saveGallery(PostExample post) {
        List<GoodsGallery> galleries = new ArrayList<>();
        for (String url : post.getImages()) {
            GoodsGallery gallery = new GoodsGallery();
            gallery.setGoodsId(post.getId());
            gallery.setImgUrl(url);
            galleries.add(gallery);
        }
        goodsMapper.addGalleryList(galleries);
    }

    @Override
    public List<Region> getRegionList(int regionId) {
        return postMapper.getRegionByParentId(regionId);
    }

    @Override
    public List<Category> getCateList(int cateId) {
        return postMapper.getCateByParentId(cateId);
    }
}
