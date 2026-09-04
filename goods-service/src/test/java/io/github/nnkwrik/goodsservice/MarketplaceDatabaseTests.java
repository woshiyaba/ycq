package io.github.nnkwrik.goodsservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import fangxianyu.innerApi.user.UserClient;
import fangxianyu.innerApi.user.UserClientHandler;
import io.github.nnkwrik.common.dto.Response;
import io.github.nnkwrik.common.dto.SimpleUser;
import io.github.nnkwrik.common.dto.UserProfile;
import io.github.nnkwrik.goodsservice.dao.IndexMapper;
import io.github.nnkwrik.goodsservice.dao.OrderMapper;
import io.github.nnkwrik.goodsservice.model.po.ContentComment;
import io.github.nnkwrik.goodsservice.model.po.ContentPost;
import io.github.nnkwrik.goodsservice.model.po.Order;
import io.github.nnkwrik.goodsservice.model.po.RecruitmentJob;
import io.github.nnkwrik.goodsservice.model.vo.ContentPage;
import io.github.nnkwrik.goodsservice.model.vo.FeedItem;
import io.github.nnkwrik.goodsservice.service.AccountService;
import io.github.nnkwrik.goodsservice.service.ContentService;
import io.github.nnkwrik.goodsservice.service.FeedService;
import io.github.nnkwrik.goodsservice.service.OrderService;
import org.apache.ibatis.session.Configuration;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Opt-in MySQL 5.7 integration checks. Apply the repository migrations first and supply
 * YCQ_TEST_DB_URL (goods_service database), YCQ_TEST_DB_USER and YCQ_TEST_DB_PASSWORD.
 * No Spring Boot services start; every inserted/updated row is rolled back, including on failure.
 * MySQL may consume auto-increment values during rolled-back inserts.
 */
public class MarketplaceDatabaseTests {
    private static final String SELLER = "ycq_test_rollback_seller_v1";
    private static final String BUYER = "ycq_test_rollback_buyer_v1";
    private static final String IMAGE = "https://example.com/ycq-rollback-test.png";
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private ContentService content;
    private AccountService accounts;
    private OrderService orders;
    private FeedService feed;

    @Before
    public void setUp() throws Exception {
        String url = System.getenv("YCQ_TEST_DB_URL");
        String username = System.getenv("YCQ_TEST_DB_USER");
        String password = System.getenv("YCQ_TEST_DB_PASSWORD");
        Assume.assumeTrue("Optional database test: supply all three YCQ_TEST_DB_* environment variables",
                url != null && !url.trim().isEmpty() && username != null && !username.trim().isEmpty() && password != null);
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);

        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(OrderMapper.class);
        configuration.addMapper(IndexMapper.class);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        orders = new OrderService(session.getMapper(OrderMapper.class));

        Map<String, UserProfile> profiles = new HashMap<>();
        profiles.put(SELLER, profile(SELLER, "测试发布者"));
        profiles.put(BUYER, profile(BUYER, "测试圈友"));
        UserClient userClient = mock(UserClient.class);
        UserClientHandler userInfo = mock(UserClientHandler.class);
        when(userClient.getProfile(anyString())).thenAnswer(call -> Response.ok(profiles.get(call.getArgument(0))));
        when(userInfo.getSimpleUser(anyString())).thenAnswer(call -> profiles.get(call.getArgument(0)));
        when(userInfo.getSimpleUserList(anyList())).thenAnswer(call -> {
            List<String> requested = call.getArgument(0);
            Map<String, SimpleUser> result = new HashMap<>();
            for (String id : requested) if (profiles.containsKey(id)) result.put(id, profiles.get(id));
            return result;
        });
        content = new ContentService(jdbc, new ObjectMapper(), userInfo);
        accounts = new AccountService();
        ReflectionTestUtils.setField(accounts, "db", jdbc);
        ReflectionTestUtils.setField(accounts, "users", userClient);
        ReflectionTestUtils.setField(accounts, "userInfo", userInfo);
        feed = new FeedService(jdbc, userInfo, session.getMapper(IndexMapper.class), new ObjectMapper());
    }

    @Test
    public void homeAndFollowingFeedsMapMixedSourcesAndRealPopularityWithPagination() {
        Integer goodsId = rollback(() -> {
            assertUnusedTestUsers();
            int id = createGoods();
            String secondImage = IMAGE + "?second=1";
            jdbc.update("insert into goods_gallery(goods_id,img_url) values(?,?),(?,?)", id, IMAGE, id, secondImage);
            jdbc.update("update goods set postage=0,post_time='2026-01-01 09:00:00' where id=?", id);
            ContentPost request = community();
            request.setImages(Arrays.asList(secondImage, IMAGE));
            ContentPost post = content.create(request, SELLER);
            jdbc.update("update content_post set created_at='2026-01-01 10:00:00' where id=?", post.getId());
            content.create(community(), BUYER);
            accounts.follow(BUYER, SELLER, true);

            Map<String, Object> result = feed.feed("HOME", "FOLLOWING", null, null, 1, 20, BUYER);
            assertEquals(2, number(result, "total"));
            assertEquals(false, result.get("hasMore"));
            assertTrue(result.get("banners") instanceof List);
            List<FeedItem> items = feedItems(result);
            assertEquals(2, items.size());
            FeedItem community = items.get(0);
            FeedItem goods = items.get(1);
            assertEquals("COMMUNITY", community.getKind());
            assertEquals(post.getId(), community.getId());
            assertEquals(post.getTitle(), community.getTitle());
            assertEquals(post.getBody(), community.getDescription());
            assertNull(community.getPrice());
            assertFalse(community.isFreeShipping());
            assertEquals(secondImage, community.getPrimaryPicUrl());
            assertEquals(Arrays.asList(secondImage, IMAGE), community.getImages());
            assertEquals("GOODS", goods.getKind());
            assertEquals(Integer.valueOf(id), goods.getId());
            assertEquals(new BigDecimal("99.99"), goods.getPrice());
            assertTrue(goods.isFreeShipping());
            assertEquals(IMAGE, goods.getPrimaryPicUrl());
            assertEquals(Arrays.asList(IMAGE, secondImage), goods.getImages());
            for (FeedItem item : items) {
                assertEquals(SELLER, item.getAuthor().getOpenId());
                assertEquals("测试发布者", item.getAuthor().getNickName());
                assertEquals(IMAGE, item.getAuthor().getAvatarUrl());
                assertEquals("运城市", item.getRegion());
                assertEquals(1, item.getFollowerCount());
                assertNotNull(item.getCreatedAt());
            }

            Map<String, Object> first = feed.feed("HOME", "FOLLOWING", null, null, 1, 1, BUYER);
            Map<String, Object> second = feed.feed("HOME", "FOLLOWING", null, null, 2, 1, BUYER);
            assertEquals(2, number(first, "total"));
            assertEquals(2, number(second, "total"));
            assertEquals(1, number(first, "page"));
            assertEquals(2, number(second, "page"));
            assertEquals(1, number(first, "size"));
            assertEquals(true, first.get("hasMore"));
            assertEquals(false, second.get("hasMore"));
            assertEquals(Collections.singletonList(community), feedItems(first));
            assertEquals(Collections.singletonList(goods), feedItems(second));
            assertFalse(second.containsKey("banners"));

            String keyword = "ycq-feed-rollback-" + id;
            jdbc.update("update goods set name=?,want_count=1,last_edit='2026-01-01 10:00:00' where id=?", keyword, id);
            jdbc.update("update content_post set title=? where id=?", keyword, post.getId());
            Map<String, Object> beforeReactions = feed.feed("HOME", "HOT", null, keyword, 1, 20, null);
            assertEquals(2, number(beforeReactions, "total"));
            assertEquals("GOODS", feedItems(beforeReactions).get(0).getKind());
            content.react(post.getId(), BUYER, "LIKE", true);
            content.comment(post.getId(), BUYER, "这条真实留言计入热度", null);
            for (String channel : Arrays.asList("RECOMMENDED", "NEW", "HOT")) {
                Map<String, Object> mixed = feed.feed("HOME", channel, null, keyword, 1, 20, null);
                assertEquals(2, number(mixed, "total"));
                assertEquals(false, mixed.get("hasMore"));
                assertEquals("COMMUNITY", feedItems(mixed).get(0).getKind());
                assertEquals(post.getId(), feedItems(mixed).get(0).getId());
                assertEquals("GOODS", feedItems(mixed).get(1).getKind());
            }
            return id;
        });
        assertEquals(0, jdbc.queryForObject("select count(*) from goods where id=?", Integer.class, goodsId).intValue());
        assertEquals(0, jdbc.queryForObject("select count(*) from goods_gallery where goods_id=?", Integer.class, goodsId).intValue());
        assertEquals(0, jdbc.queryForObject("select count(*) from content_post where author_id in (?,?)", Integer.class, SELLER, BUYER).intValue());
        assertEquals(0, jdbc.queryForObject("select count(*) from user_follow where follower_id=?", Integer.class, BUYER).intValue());
    }

    @Test
    public void contentRecruitmentNotificationsAndAccountQueriesUseTheMigratedSchema() {
        Integer createdPost = rollback(() -> {
            assertUnusedTestUsers();
            ContentPost post = content.create(community(), SELLER);
            assertEquals("测试发布者", post.getAuthor().getNickName());
            assertEquals(Collections.singletonList(IMAGE), post.getImages());
            ContentComment first = content.comment(post.getId(), BUYER, "欢迎分享", null);
            ContentComment reply = content.comment(post.getId(), SELLER, "感谢留言", first.getId());
            ContentComment nestedReply = content.comment(post.getId(), BUYER, "再次回复", reply.getId());
            assertEquals(first.getId(), reply.getParentId());
            assertEquals(first.getId(), nestedReply.getParentId());
            assertEquals(SELLER, nestedReply.getReplyUser().getOpenId());
            assertEquals(3, content.comments(post.getId(), BUYER, 1, 20).getTotal());

            content.react(post.getId(), BUYER, "LIKE", true);
            content.react(post.getId(), BUYER, "LIKE", true);
            content.react(post.getId(), BUYER, "FAVORITE", true);
            content.react(post.getId(), BUYER, "FAVORITE", true);
            ContentPost detail = content.getPost(post.getId(), BUYER);
            assertTrue(detail.isLiked());
            assertTrue(detail.isFavorited());
            assertEquals(1, detail.getLikeCount());
            assertEquals(3, detail.getCommentCount());
            assertEquals(1, content.list("COMMUNITY", null, null, null, null, "LATEST", null,
                    false, false, true, 1, 10, BUYER).getTotal());

            int goodsId = createGoods();
            jdbc.update("insert into goods_comment(goods_id,user_id,reply_user_id,reply_comment_id,content) values(?,?,?,?,?)",
                    goodsId, BUYER, SELLER, 0, "商品留言也应出现在通知中");
            int goodsCommentId = jdbc.queryForObject("select id from goods_comment where goods_id=?", Integer.class, goodsId);
            Map<String, Object> notifications = content.notifications(SELLER, 1, 20);
            assertEquals(3, number(notifications, "unreadCount"));
            List<ContentComment> messages = notificationItems(notifications);
            assertEquals(1, messages.stream().filter(item -> "GOODS".equals(item.getSource())).count());
            assertEquals(Integer.valueOf(goodsId), messages.stream().filter(item -> "GOODS".equals(item.getSource())).findFirst().get().getGoodsId());
            content.readNotifications(SELLER, Collections.singletonList(first.getId()), Collections.singletonList(goodsCommentId));
            assertEquals(1, number(content.notifications(SELLER, 1, 20), "unreadCount"));
            content.readAllNotifications(SELLER, ((Number) notifications.get("maxId")).intValue(),
                    ((Number) notifications.get("goodsMaxId")).intValue());
            assertEquals(0, number(content.notifications(SELLER, 1, 20), "unreadCount"));
            assertEquals(1, number(content.notifications(BUYER, 1, 20), "unreadCount"));

            accounts.follow(BUYER, SELLER, true);
            accounts.follow(BUYER, SELLER, true);
            assertEquals(1, number(accounts.following(BUYER, 1, 10), "total"));
            assertEquals(1, content.list("COMMUNITY", "回滚动态", null, null, null, "RECOMMENDED", SELLER,
                    true, false, false, 1, 10, BUYER).getTotal());
            ContentPost job = content.create(recruitment(), SELLER);
            ContentPage<ContentPost> jobs = content.list("RECRUITMENT", "回滚招聘", "PART_TIME", "餐饮服务", "WEEKLY,DAILY",
                    "SALARY", SELLER, false, false, false, 1, 10, BUYER);
            assertEquals(1, jobs.getTotal());
            assertEquals(new BigDecimal("180.00"), jobs.getItems().get(0).getJob().getSalary());
            assertEquals(Arrays.asList("包吃", "交通补贴"), jobs.getItems().get(0).getJob().getBenefits());
            content.setStatus(job.getId(), "OFFLINE", SELLER);
            assertEquals(0, number(accounts.profile(SELLER, BUYER), "jobCount"));
            assertEquals(1, number(accounts.profile(SELLER, SELLER), "jobCount"));
            content.setStatus(job.getId(), "PUBLISHED", SELLER);

            AccountService.Address firstAddress = accounts.saveAddress(BUYER, null, address("测试地址一"));
            AccountService.Address secondAddress = accounts.saveAddress(BUYER, null, address("测试地址二"));
            assertNotEquals(firstAddress.getId(), secondAddress.getId());
            List<AccountService.Address> addresses = accounts.addresses(BUYER);
            assertEquals(2, addresses.size());
            assertEquals(1, addresses.stream().filter(item -> Boolean.TRUE.equals(item.getIsDefault())).count());
            assertEquals(secondAddress.getId(), addresses.get(0).getId());
            accounts.recordHistory(BUYER, "COMMUNITY", post.getId());
            accounts.recordHistory(BUYER, "COMMUNITY", post.getId());
            accounts.recordHistory(BUYER, "RECRUITMENT", job.getId());
            accounts.recordHistory(BUYER, "GOODS", goodsId);
            Map<String, Object> history = accounts.history(BUYER, 1, 10);
            assertEquals(3, number(history, "total"));
            for (Map<String, Object> item : mapItems(history)) assertEquals(IMAGE, item.get("primaryPicUrl"));
            Map<String, Object> buyer = accounts.profile(BUYER, BUYER);
            assertEquals(1, number(buyer, "favoriteCount"));
            assertEquals(1, number(buyer, "followingCount"));
            assertEquals(3, number(buyer, "historyCount"));
            Map<String, Object> seller = accounts.profile(SELLER, SELLER);
            assertEquals(1, number(seller, "postCount"));
            assertEquals(1, number(seller, "jobCount"));
            assertEquals(1, number(seller, "followerCount"));
            return post.getId();
        });
        assertEquals(0, jdbc.queryForObject("select count(*) from content_post where id=?", Integer.class, createdPost).intValue());
        assertEquals(0, jdbc.queryForObject("select count(*) from user_address where user_id=?", Integer.class, BUYER).intValue());
    }

    @Test
    public void orderCreationPaymentDeliveryReceiptAndReviewRunThroughMyBatisAndRollback() {
        Long orderId = rollback(() -> {
            assertUnusedTestUsers();
            int goodsId = createGoods();
            Order.Create request = new Order.Create();
            request.setGoodsId(goodsId);
            request.setDeliveryMethod("EXPRESS");
            request.setRequestId("ycq-rollback-express-request");
            Order.Address address = new Order.Address();
            address.setName("测试收件人");
            address.setPhone("13800138000");
            address.setRegion("山西 运城 盐湖");
            address.setDetail("回滚测试地址");
            request.setAddress(address);
            Order order = orders.create(BUYER, request);
            assertEquals("PENDING", order.getStatus());
            assertEquals(new BigDecimal("105.24"), order.getAmount());
            assertEquals(new BigDecimal("5.25"), order.getPostage());
            assertEquals(address, order.getAddress());
            assertEquals(order.getId(), orders.create(BUYER, request).getId());
            assertEquals(1, number(orders.list(BUYER, "buyer", "PENDING", 1, 10), "total"));
            assertEquals(1, number(orders.list(SELLER, "seller", "PENDING", 1, 10), "total"));

            Order paid = orders.pay(order.getId(), BUYER);
            assertEquals("PAID", paid.getStatus());
            assertNotNull(paid.getPaidAt());
            assertEquals(paid.getPaidAt(), orders.pay(order.getId(), BUYER).getPaidAt());
            assertEquals(BUYER, jdbc.queryForObject("select buyer_id from goods where id=?", String.class, goodsId));
            assertEquals("SHIPPED", orders.ship(order.getId(), SELLER, "YCQ-ROLLBACK-TRACKING").getStatus());
            assertEquals("COMPLETED", orders.receive(order.getId(), BUYER).getStatus());
            assertEquals("COMPLETED", orders.receive(order.getId(), BUYER).getStatus());
            assertEquals("COMPLETED", orders.pay(order.getId(), BUYER).getStatus());
            Order.Review review = new Order.Review();
            review.setRating(5);
            review.setContent("实际数据库回滚评价测试");
            assertEquals(Integer.valueOf(5), orders.review(order.getId(), BUYER, review).getReview().getRating());
            assertEquals(review.getContent(), orders.review(order.getId(), BUYER, review).getReview().getContent());
            assertEquals(1, jdbc.queryForObject("select count(*) from goods_order where goods_id=?", Integer.class, goodsId).intValue());
            assertEquals(1, jdbc.queryForObject("select count(*) from goods_order_review where order_id=?", Integer.class, order.getId()).intValue());
            assertNotNull(jdbc.queryForObject("select sold_time from goods where id=?", Date.class, goodsId));
            assertEquals(1, number(accounts.profile(BUYER, BUYER), "boughtCount"));
            assertEquals(1, number(accounts.profile(SELLER, SELLER), "soldCount"));
            return order.getId();
        });
        assertEquals(0, jdbc.queryForObject("select count(*) from goods_order where id=?", Integer.class, orderId).intValue());
        assertEquals(0, jdbc.queryForObject("select count(*) from goods where seller_id=?", Integer.class, SELLER).intValue());
    }

    private <T> T rollback(Supplier<T> action) {
        return transaction.execute(status -> {
            try {
                return action.get();
            } finally {
                status.setRollbackOnly();
            }
        });
    }

    private void assertUnusedTestUsers() {
        for (String table : Arrays.asList("content_post", "goods", "user_address", "browse_history")) {
            String column = "content_post".equals(table) ? "author_id" : "goods".equals(table) ? "seller_id" : "user_id";
            assertEquals("Reserved rollback-test user already has data in " + table, 0,
                    jdbc.queryForObject("select count(*) from " + table + " where " + column + " in (?,?)", Integer.class, SELLER, BUYER).intValue());
        }
        assertEquals("Reserved rollback-test users already have follow data", 0,
                jdbc.queryForObject("select count(*) from user_follow where follower_id in (?,?) or followed_id in (?,?)",
                        Integer.class, SELLER, BUYER, SELLER, BUYER).intValue());
    }

    private int createGoods() {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("insert into goods " +
                    "(seller_id,buyer_id,name,price,postage,primary_pic_url,`desc`,region,able_express,able_meet,able_self_take,is_selling,is_delete) " +
                    "values(?,'0','YCQ回滚测试商品',99.99,5.25,?,'仅事务内测试','运城市',1,1,1,1,0)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, SELLER);
            statement.setString(2, IMAGE);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).intValue();
    }

    private ContentPost community() {
        ContentPost post = new ContentPost();
        post.setKind("COMMUNITY");
        post.setTitle("YCQ回滚动态");
        post.setBody("这条内容只存在于测试事务，结束后回滚。");
        post.setImages(Collections.singletonList(IMAGE));
        post.setRegion("运城市");
        return post;
    }

    private ContentPost recruitment() {
        ContentPost post = community();
        post.setKind("RECRUITMENT");
        post.setTitle("YCQ回滚招聘");
        RecruitmentJob job = new RecruitmentJob();
        job.setWorkType("PART_TIME");
        job.setIndustry("餐饮服务");
        job.setSalary(new BigDecimal("180.00"));
        job.setSalaryUnit("DAY");
        job.setSettlement("WEEKLY");
        job.setAddress("回滚测试工作地址");
        job.setHeadcount(2);
        job.setCompany("回滚测试招聘方");
        job.setRequirements("仅验证实际数据库映射");
        job.setBenefits(Arrays.asList("包吃", "交通补贴"));
        job.setContactName("测试联系人");
        job.setContactPhone("13800138000");
        post.setJob(job);
        return post;
    }

    private AccountService.Address address(String detail) {
        AccountService.Address address = new AccountService.Address();
        address.setName("测试收件人");
        address.setPhone("13800138000");
        address.setRegion("运城市");
        address.setDetail(detail);
        address.setIsDefault(true);
        return address;
    }

    private UserProfile profile(String id, String name) {
        UserProfile profile = new UserProfile();
        profile.setOpenId(id);
        profile.setNickName(name);
        profile.setAvatarUrl(IMAGE);
        profile.setGender(0);
        profile.setBio("数据库测试资料");
        profile.setRegion("运城市");
        return profile;
    }

    private long number(Map<String, Object> value, String key) {
        return ((Number) value.get(key)).longValue();
    }

    @SuppressWarnings("unchecked")
    private List<ContentComment> notificationItems(Map<String, Object> value) {
        return (List<ContentComment>) value.get("items");
    }

    @SuppressWarnings("unchecked")
    private List<FeedItem> feedItems(Map<String, Object> value) {
        return (List<FeedItem>) value.get("items");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> mapItems(Map<String, Object> value) {
        return (List<Map<String, Object>>) value.get("items");
    }
}
