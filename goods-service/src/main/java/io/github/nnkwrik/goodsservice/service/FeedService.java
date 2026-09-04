package io.github.nnkwrik.goodsservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fangxianyu.innerApi.user.UserClientHandler;
import io.github.nnkwrik.common.dto.SimpleUser;
import io.github.nnkwrik.goodsservice.dao.GoodsMapper;
import io.github.nnkwrik.goodsservice.dao.IndexMapper;
import io.github.nnkwrik.goodsservice.model.po.Category;
import io.github.nnkwrik.goodsservice.model.vo.FeedItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FeedService {
    private static final Map<String, List<String>> CATEGORY_NAMES = categoryNames();
    private static final List<String> CLOTHING = Arrays.asList("服饰", "服饰鞋包", "服装", "衣服", "男装", "女装", "童装", "男鞋", "女鞋");
    private static final String GOODS_SELECT = "select g.id,'GOODS' kind,g.name title,g.`desc` description," +
            "g.primary_pic_url,null images_json,g.region,g.price,coalesce(g.able_express=1 and g.postage=0,0) free_shipping," +
            "g.seller_id author_id,g.post_time created_at," + GoodsMapper.popular_score + " score" +
            " from goods g where g.is_selling=1 and g.is_delete=0";
    // Match the goods baseline and decay; only real likes and visible comments add engagement points.
    private static final String COMMUNITY_SCORE = "(500 + 10*(select count(*) from content_reaction r where r.post_id=p.id and r.type='LIKE')" +
            " + 10*(select count(*) from content_comment c where c.post_id=p.id and c.deleted=0))" +
            " * exp(-greatest(timestampdiff(second,p.created_at,now()),0)/864000.0)";
    private static final String COMMUNITY_SELECT = "select p.id,'COMMUNITY' kind,p.title,p.body description," +
            "case when json_valid(p.images) then nullif(json_unquote(json_extract(p.images,'$[0]')),'null') else null end primary_pic_url," +
            "p.images images_json,p.region,null price,0 free_shipping,p.author_id,p.created_at," + COMMUNITY_SCORE + " score" +
            " from content_post p where p.kind='COMMUNITY' and p.status='PUBLISHED'";
    private final JdbcTemplate jdbc;
    private final UserClientHandler users;
    private final IndexMapper index;
    private final ObjectMapper json;

    public FeedService(JdbcTemplate jdbc, UserClientHandler users, IndexMapper index, ObjectMapper json) {
        this.jdbc = jdbc;
        this.users = users;
        this.index = index;
        this.json = json;
    }

    public Map<String, Object> feed(String scene, String channel, String categoryKey, String keyword,
                                    int page, int size, String viewerId) {
        ContentService.validatePage(page, size);
        scene = option(scene, "HOME", "页面", "HOME", "FEATURED");
        channel = option(channel, "RECOMMENDED", "频道", "RECOMMENDED", "FOLLOWING", "SQUARE", "NEW", "HOT", "CIRCLES", "RESOURCES", "CLOTHING");
        categoryKey = categoryKey == null ? "" : categoryKey.trim().toLowerCase(Locale.ROOT);
        if (!categoryKey.isEmpty() && !CATEGORY_NAMES.containsKey(categoryKey)) throw invalid("分类入口无效");
        keyword = keyword == null ? "" : keyword.trim();
        if (keyword.length() > 100 || keyword.indexOf('\0') >= 0) throw invalid("关键词不能超过100字");
        boolean following = "FOLLOWING".equals(channel);
        if (following && (viewerId == null || viewerId.trim().isEmpty())) throw new ContentException(3003, "请先登录查看关注");

        boolean community = "HOME".equals(scene) && !"CLOTHING".equals(channel);
        boolean goods = "FEATURED".equals(scene) || Arrays.asList("RECOMMENDED", "NEW", "HOT", "FOLLOWING", "CLOTHING").contains(channel);
        List<String> aliases = CATEGORY_NAMES.get(categoryKey);
        List<Category> categories = goods && (aliases != null || "CLOTHING".equals(channel))
                ? jdbc.query("select id,name,parent_id from category", new BeanPropertyRowMapper<>(Category.class))
                : Collections.emptyList();
        List<Object> args = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        if (goods) {
            StringBuilder sql = new StringBuilder(GOODS_SELECT);
            if (following) followed(sql, args, "g.seller_id", viewerId);
            if (aliases != null) categoryFilter(sql, args, categories, aliases);
            if ("CLOTHING".equals(channel)) categoryFilter(sql, args, categories, CLOTHING);
            if (!keyword.isEmpty()) textFilter(sql, args, "g.name", "g.`desc`", Collections.singletonList(keyword));
            sources.add(sql.toString());
        }
        if (community) {
            StringBuilder sql = new StringBuilder(COMMUNITY_SELECT);
            if (following) followed(sql, args, "p.author_id", viewerId);
            if ("RESOURCES".equals(channel)) textFilter(sql, args, "p.title", "p.body", Arrays.asList("资源", "供应"));
            if (aliases != null) textFilter(sql, args, "p.title", "p.body", aliases);
            if (!keyword.isEmpty()) textFilter(sql, args, "p.title", "p.body", Collections.singletonList(keyword));
            sources.add(sql.toString());
        }
        String source = " from (" + String.join(" union all ", sources) + ") feed";
        long total = jdbc.queryForObject("select count(*)" + source, Long.class, args.toArray());
        List<FeedItem> items = Collections.emptyList();
        if (total > (long) (page - 1) * size) {
            boolean popular = "RECOMMENDED".equals(channel) || "HOT".equals(channel);
            String order = popular ? "feed.score desc,feed.created_at desc,feed.kind,feed.id desc" : "feed.created_at desc,feed.kind,feed.id desc";
            args.add(size);
            args.add((page - 1) * size);
            items = jdbc.query("select feed.*" + source + " order by " + order + " limit ? offset ?",
                    new BeanPropertyRowMapper<>(FeedItem.class), args.toArray());
            enrich(items);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("hasMore", (long) page * size < total);
        if (page == 1) result.put("banners", index.findAd());
        return result;
    }

    private void enrich(List<FeedItem> items) {
        if (items.isEmpty()) return;
        List<String> ids = items.stream().map(FeedItem::getAuthorId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<String, Long> followers = new HashMap<>();
        if (!ids.isEmpty()) {
            jdbc.query("select followed_id,count(*) follower_count from user_follow where followed_id in (" + marks(ids.size()) + ") group by followed_id",
                    (RowCallbackHandler) rs -> followers.put(rs.getString("followed_id"), rs.getLong("follower_count")), ids.toArray());
        }
        List<Integer> goodsIds = items.stream().filter(item -> "GOODS".equals(item.getKind())).map(FeedItem::getId).collect(Collectors.toList());
        Map<Integer, List<String>> galleries = new HashMap<>();
        if (!goodsIds.isEmpty()) {
            jdbc.query("select goods_id,img_url from goods_gallery where goods_id in (" + marks(goodsIds.size()) + ") order by id",
                    (RowCallbackHandler) rs -> {
                        String url = rs.getString("img_url");
                        if (url != null && !url.trim().isEmpty()) galleries.computeIfAbsent(rs.getInt("goods_id"), ignored -> new ArrayList<>()).add(url);
                    }, goodsIds.toArray());
        }
        Map<String, SimpleUser> authors = Collections.emptyMap();
        try {
            Map<String, SimpleUser> found = users.getSimpleUserList(ids);
            if (found != null) authors = found;
        } catch (RuntimeException error) {
            log.warn("信息流作者暂时无法加载，保留作者编号", error);
        }
        for (FeedItem item : items) {
            SimpleUser author = authors.get(item.getAuthorId());
            if (author == null) {
                author = new SimpleUser();
                author.setOpenId(item.getAuthorId());
                author.setNickName("圈友");
            }
            item.setAuthor(author);
            item.setFollowerCount(followers.getOrDefault(item.getAuthorId(), 0L));
            List<String> images = "GOODS".equals(item.getKind()) ? galleries.getOrDefault(item.getId(), Collections.emptyList()) : images(item.getImagesJson());
            if (images.isEmpty() && item.getPrimaryPicUrl() != null && !item.getPrimaryPicUrl().isEmpty()) images = Collections.singletonList(item.getPrimaryPicUrl());
            item.setImages(images);
        }
    }

    private List<String> images(String value) {
        if (value == null || value.isEmpty()) return Collections.emptyList();
        try {
            List<String> images = json.readValue(value, new TypeReference<List<String>>() {});
            return images == null ? Collections.emptyList() : images;
        } catch (Exception error) {
            return Collections.emptyList();
        }
    }

    private static void followed(StringBuilder sql, List<Object> args, String authorColumn, String viewerId) {
        sql.append(" and exists(select 1 from user_follow f where f.follower_id=? and f.followed_id=").append(authorColumn).append(")");
        args.add(viewerId);
    }

    private static void categoryFilter(StringBuilder sql, List<Object> args, List<Category> categories, List<String> aliases) {
        Set<Integer> ids = new LinkedHashSet<>();
        Map<Integer, List<Integer>> children = new HashMap<>();
        for (Category category : categories) {
            if (aliases.contains(category.getName() == null ? "" : category.getName().trim())) ids.add(category.getId());
            children.computeIfAbsent(category.getParentId(), ignored -> new ArrayList<>()).add(category.getId());
        }
        Deque<Integer> pending = new ArrayDeque<>(ids);
        while (!pending.isEmpty()) {
            for (Integer child : children.getOrDefault(pending.removeFirst(), Collections.emptyList())) {
                if (ids.add(child)) pending.addLast(child);
            }
        }
        if (ids.isEmpty()) {
            textFilter(sql, args, "g.name", "g.`desc`", aliases);
        } else {
            sql.append(" and g.category_id in (").append(marks(ids.size())).append(")");
            args.addAll(ids);
        }
    }

    private static void textFilter(StringBuilder sql, List<Object> args, String title, String body, List<String> words) {
        sql.append(" and (");
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) sql.append(" or ");
            sql.append(title).append(" like ? escape '!' or ").append(body).append(" like ? escape '!'");
            String like = "%" + words.get(i).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(")");
    }

    private static String option(String value, String fallback, String label, String... allowed) {
        value = value == null || value.trim().isEmpty() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!Arrays.asList(allowed).contains(value)) throw invalid(label + "无效");
        return value;
    }

    private static ContentException invalid(String message) {
        return new ContentException(ContentService.INVALID, message);
    }

    private static String marks(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private static Map<String, List<String>> categoryNames() {
        Map<String, List<String>> names = new LinkedHashMap<>();
        names.put("mobile", Arrays.asList("智能手机", "手机", "二手手机", "手机通讯"));
        names.put("office", Arrays.asList("电脑办公", "电脑", "电脑配件", "笔记本电脑", "台式机", "一体机", "鼠标", "键盘", "显示器",
                "办公用品", "办公设备", "办公文具", "办公", "文具"));
        names.put("appliance-cleaning", Arrays.asList("家电清洗", "空调清洗", "洗衣机清洗"));
        names.put("housekeeping", Arrays.asList("家政保洁", "家政服务", "家政", "保洁"));
        names.put("renovation", Arrays.asList("装修建材", "装修", "家装", "家居建材", "家装市场"));
        names.put("appliance-repair", Arrays.asList("家电维修", "家电修理", "维修家电"));
        names.put("car-care", Arrays.asList("汽车保养", "汽车美容", "洗车", "汽车维修", "养车"));
        names.put("broadband", Arrays.asList("宽带办理", "宽带", "网络宽带", "宽带安装"));
        names.put("food", Arrays.asList("美食", "餐饮美食", "美食餐饮", "食品", "食品饮料", "餐饮"));
        names.put("digital", Arrays.asList("数码家电", "手机数码", "数码", "数码产品", "电脑数码", "电子数码", "家用电器", "家电", "家居家电"));
        names.put("housing", Arrays.asList("房产", "房屋租售", "租房买房", "租房", "二手房", "房屋"));
        names.put("pets", Arrays.asList("宠物", "宠物/用品", "萌宠", "宠物用品", "萌宠之家"));
        names.put("appliances", Arrays.asList("家用电器", "家电", "家居家电", "家电市场"));
        names.put("cleaning", names.get("housekeeping"));
        names.put("home-improvement", Arrays.asList("家具饰品", "家具", "家居", "家装", "装修建材"));
        names.put("cars", Arrays.asList("汽车/用品", "汽车", "汽车用品", "车辆", "爱车之家"));
        names.put("supply", Arrays.asList("供应链", "供需", "供应", "资源", "批发"));
        return Collections.unmodifiableMap(names);
    }
}
