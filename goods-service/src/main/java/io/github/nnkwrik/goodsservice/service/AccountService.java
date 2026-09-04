package io.github.nnkwrik.goodsservice.service;

import fangxianyu.innerApi.user.UserClient;
import fangxianyu.innerApi.user.UserClientHandler;
import io.github.nnkwrik.common.dto.Response;
import io.github.nnkwrik.common.dto.SimpleUser;
import io.github.nnkwrik.common.dto.UserProfile;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

@Service
public class AccountService {
    @Autowired private JdbcTemplate db;
    @Autowired private UserClient users;
    @Autowired private UserClientHandler userInfo;

    public Map<String, Object> profile(String id, String viewer) {
        Response<UserProfile> response = users.getProfile(id);
        if (response.getErrno() != 0 || response.getData() == null) throw new IllegalArgumentException("用户不存在");
        UserProfile user = response.getData();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openId", id);
        result.put("nickName", user.getNickName());
        result.put("avatarUrl", user.getAvatarUrl());
        result.put("registerTime", user.getRegisterTime());
        result.put("bio", user.getBio());
        result.put("gender", user.getGender());
        result.put("region", user.getRegion());
        result.put("following", viewer != null && count("SELECT COUNT(*) FROM user_follow WHERE follower_id=? AND followed_id=?", viewer, id) > 0);
        result.put("followerCount", count("SELECT COUNT(*) FROM user_follow WHERE followed_id=?", id));
        result.put("followingCount", count("SELECT COUNT(*) FROM user_follow WHERE follower_id=?", id));
        result.put("postedCount", count("SELECT COUNT(*) FROM goods WHERE seller_id=? AND is_delete=0 AND sold_time IS NULL", id));
        String visibleStatus = id.equals(viewer) ? "status<>'DELETED'" : "status='PUBLISHED'";
        result.put("postCount", count("SELECT COUNT(*) FROM content_post WHERE author_id=? AND kind='COMMUNITY' AND " + visibleStatus, id));
        result.put("jobCount", count("SELECT COUNT(*) FROM content_post WHERE author_id=? AND kind='RECRUITMENT' AND " + visibleStatus, id));
        if (id.equals(viewer)) {
            result.put("soldCount", count("SELECT COUNT(*) FROM goods_order WHERE seller_id=? AND status<>'CANCELLED'", id));
            result.put("boughtCount", count("SELECT COUNT(*) FROM goods_order WHERE buyer_id=? AND status<>'CANCELLED'", id));
            result.put("favoriteCount", count("SELECT COUNT(DISTINCT goods_id) FROM user_preference WHERE user_id=? AND type=1", id)
                    + count("SELECT COUNT(*) FROM content_reaction WHERE user_id=? AND type='FAVORITE'", id));
            result.put("historyCount", count("SELECT COUNT(*) FROM browse_history WHERE user_id=?", id));
        }
        return result;
    }

    public Map<String, Object> updateProfile(String id, String token, UserProfile profile) {
        Response<UserProfile> response = users.updateProfile(token, profile);
        if (response.getErrno() != 0) throw new IllegalArgumentException(response.getErrmsg());
        return profile(id, id);
    }

    public void follow(String viewer, String target, boolean follow) {
        if (viewer.equals(target)) throw new IllegalArgumentException("不能关注自己");
        if (follow) {
            SimpleUser user = userInfo.getSimpleUser(target);
            if (user == null || user.getOpenId() == null) throw new IllegalArgumentException("用户不存在");
            db.update("INSERT INTO user_follow(follower_id,followed_id) VALUES(?,?) ON DUPLICATE KEY UPDATE followed_id=VALUES(followed_id)", viewer, target);
        } else db.update("DELETE FROM user_follow WHERE follower_id=? AND followed_id=?", viewer, target);
    }

    public Map<String, Object> following(String viewer, int page, int size) {
        checkPage(page, size);
        List<String> ids = db.queryForList("SELECT followed_id FROM user_follow WHERE follower_id=? ORDER BY created_at DESC,followed_id LIMIT ? OFFSET ?",
                String.class, viewer, size, (page - 1) * size);
        Map<String, SimpleUser> info = userInfo.getSimpleUserList(ids);
        List<SimpleUser> items = new ArrayList<>();
        for (String id : ids) {
            SimpleUser u = info.get(id);
            if (u == null) { u = SimpleUser.unknownUser(); u.setOpenId(id); }
            items.add(u);
        }
        return page(items, count("SELECT COUNT(*) FROM user_follow WHERE follower_id=?", viewer), page, size);
    }

    public List<Address> addresses(String viewer) {
        return db.query("SELECT id,name,phone,region,detail,is_default FROM user_address WHERE user_id=? ORDER BY is_default DESC,id DESC",
                new BeanPropertyRowMapper<>(Address.class), viewer);
    }

    @Transactional
    public Address saveAddress(String viewer, Integer id, Address address) {
        validateAddress(address);
        if (id != null && count("SELECT COUNT(*) FROM user_address WHERE id=? AND user_id=?", id, viewer) == 0)
            throw new IllegalArgumentException("地址不存在或无权修改");
        if (id == null && count("SELECT COUNT(*) FROM user_address WHERE user_id=?", viewer) >= 20)
            throw new IllegalArgumentException("最多保存20个收货地址");
        if (Boolean.TRUE.equals(address.getIsDefault())) db.update("UPDATE user_address SET is_default=0 WHERE user_id=? AND is_default=1", viewer);
        if (id == null) {
            KeyHolder key = new GeneratedKeyHolder();
            db.update(c -> {
                PreparedStatement p = c.prepareStatement("INSERT INTO user_address(user_id,name,phone,region,detail,is_default) VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                p.setString(1, viewer); p.setString(2, address.getName()); p.setString(3, address.getPhone());
                p.setString(4, address.getRegion()); p.setString(5, address.getDetail()); p.setBoolean(6, Boolean.TRUE.equals(address.getIsDefault()));
                return p;
            }, key);
            id = key.getKey().intValue();
        } else db.update("UPDATE user_address SET name=?,phone=?,region=?,detail=?,is_default=? WHERE id=? AND user_id=?",
                address.getName(), address.getPhone(), address.getRegion(), address.getDetail(), Boolean.TRUE.equals(address.getIsDefault()), id, viewer);
        address.setId(id);
        return address;
    }

    public void deleteAddress(String viewer, int id) {
        if (db.update("DELETE FROM user_address WHERE id=? AND user_id=?", id, viewer) == 0)
            throw new IllegalArgumentException("地址不存在或无权删除");
    }

    public void recordHistory(String viewer, String kind, int id) {
        boolean goods = "GOODS".equals(kind);
        if (!goods && !"COMMUNITY".equals(kind) && !"RECRUITMENT".equals(kind)) throw new IllegalArgumentException("浏览类型不正确");
        long found = goods ? count("SELECT COUNT(*) FROM goods WHERE id=? AND is_delete=0", id)
                : count("SELECT COUNT(*) FROM content_post WHERE id=? AND kind=? AND status='PUBLISHED'", id, kind);
        if (found == 0) throw new IllegalArgumentException("内容不存在");
        db.update("INSERT INTO browse_history(user_id,kind,target_id) VALUES(?,?,?) ON DUPLICATE KEY UPDATE visited_at=CURRENT_TIMESTAMP(3)", viewer, kind, id);
    }

    public Map<String, Object> history(String viewer, int page, int size) {
        checkPage(page, size);
        String from = " FROM browse_history h LEFT JOIN goods g ON h.kind='GOODS' AND g.id=h.target_id " +
                "LEFT JOIN content_post p ON h.kind<>'GOODS' AND p.id=h.target_id WHERE h.user_id=? " +
                "AND ((g.id IS NOT NULL AND g.is_delete=0) OR (p.id IS NOT NULL AND p.status='PUBLISHED'))";
        List<Map<String, Object>> items = db.queryForList("SELECT h.target_id id,h.kind,h.visited_at visitedAt," +
                "COALESCE(g.name,p.title) title,COALESCE(g.name,p.title) name,COALESCE(g.primary_pic_url,JSON_UNQUOTE(JSON_EXTRACT(p.images,'$[0]'))) primaryPicUrl,p.images,g.price" + from +
                " ORDER BY h.visited_at DESC,h.target_id DESC LIMIT ? OFFSET ?", viewer, size, (page-1)*size);
        return page(items, count("SELECT COUNT(*)" + from, viewer), page, size);
    }

    public void clearHistory(String viewer) { db.update("DELETE FROM browse_history WHERE user_id=?", viewer); }

    public Map<String, Object> goods(String viewer, String status, int page, int size) {
        checkPage(page, size);
        String filter = " WHERE seller_id=? AND is_delete=0 AND sold_time IS NULL";
        if ("PUBLISHED".equals(status)) filter += " AND is_selling=1";
        else if ("OFFLINE".equals(status)) filter += " AND is_selling=0 AND (buyer_id IS NULL OR buyer_id='' OR buyer_id='0')";
        else if (status != null && !status.isEmpty() && !"ALL".equals(status)) throw new IllegalArgumentException("商品状态不正确");
        List<Map<String,Object>> items = db.queryForList("SELECT id,name,price,primary_pic_url primaryPicUrl,is_selling isSelling,last_edit lastEdit,browse_count browseCount FROM goods" + filter + " ORDER BY last_edit DESC,id DESC LIMIT ? OFFSET ?", viewer,size,(page-1)*size);
        return page(items,count("SELECT COUNT(*) FROM goods"+filter,viewer),page,size);
    }

    private long count(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    private static void checkPage(int page, int size) {
        if (page < 1 || page > 100000 || size < 1 || size > 50) throw new IllegalArgumentException("分页参数不正确");
    }
    private static Map<String,Object> page(Object items, long total, int page, int size) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("items",items); result.put("total",total); result.put("page",page); result.put("size",size); result.put("hasMore", (long)page*size<total);
        return result;
    }
    static void validateAddress(Address a) {
        if (a == null || !validText(a.getName(),40) || !validText(a.getRegion(),100) || !validText(a.getDetail(),200)
                || a.getPhone() == null || !a.getPhone().matches("[+0-9() -]{6,30}")) throw new IllegalArgumentException("请填写有效的收货人、电话和完整地址");
    }
    private static boolean validText(String value, int limit) { return value != null && !value.trim().isEmpty() && value.length() <= limit; }

    @Data
    public static class Address {
        private Integer id;
        private String name;
        private String phone;
        private String region;
        private String detail;
        private Boolean isDefault = false;
    }
}
