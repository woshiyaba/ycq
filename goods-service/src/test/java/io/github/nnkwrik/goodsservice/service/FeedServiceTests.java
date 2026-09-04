package io.github.nnkwrik.goodsservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fangxianyu.innerApi.user.UserClientHandler;
import io.github.nnkwrik.goodsservice.dao.IndexMapper;
import io.github.nnkwrik.goodsservice.model.po.Category;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FeedServiceTests {
    @Test
    public void rejectsInvalidFiltersAndAnonymousFollowingBeforeDatabaseQueries() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        UserClientHandler users = mock(UserClientHandler.class);
        IndexMapper index = mock(IndexMapper.class);
        FeedService service = new FeedService(jdbc, users, index, new ObjectMapper());
        rejects(6001, () -> service.feed("OTHER", "NEW", null, null, 1, 20, null));
        rejects(6001, () -> service.feed("HOME", "OTHER", null, null, 1, 20, null));
        rejects(6001, () -> service.feed("HOME", "NEW", "unknown", null, 1, 20, null));
        rejects(6001, () -> service.feed("HOME", "NEW", null, null, 0, 20, null));
        rejects(6001, () -> service.feed("HOME", "NEW", null, null, 1, 51, null));
        rejects(6001, () -> service.feed("HOME", "NEW", null, String.join("", Collections.nCopies(101, "a")), 1, 20, null));
        rejects(3003, () -> service.feed("HOME", "FOLLOWING", null, null, 1, 20, null));
        verifyZeroInteractions(jdbc, users, index);
    }

    @Test
    public void homeDiscoveryMixesPublicSourcesWhileFeaturedAndSpecialChannelsKeepTheirBoundaries() {
        CapturingJdbc jdbc = new CapturingJdbc();
        FeedService service = new FeedService(jdbc, mock(UserClientHandler.class), mock(IndexMapper.class), new ObjectMapper());
        for (String channel : Arrays.asList("RECOMMENDED", "NEW", "HOT")) {
            service.feed("HOME", channel, null, null, 2, 20, null);
            assertTrue(jdbc.sql.contains(" from goods g"));
            assertTrue(jdbc.sql.contains(" from content_post p"));
            assertTrue(jdbc.sql.contains(" union all "));
            assertTrue(jdbc.sql.contains("r.type='LIKE'"));
            assertTrue(jdbc.sql.contains("c.deleted=0"));
            service.feed("FEATURED", channel, null, null, 2, 20, null);
            assertTrue(jdbc.sql.contains(" from goods g"));
            assertFalse(jdbc.sql.contains("content_post"));
        }
        for (String channel : Arrays.asList("SQUARE", "CIRCLES", "RESOURCES")) {
            service.feed("HOME", channel, null, null, 2, 20, null);
            assertTrue(jdbc.sql.contains(" from content_post p"));
            assertFalse(jdbc.sql.contains(" from goods g"));
        }
        service.feed("HOME", "CLOTHING", null, null, 2, 20, null);
        assertTrue(jdbc.sql.contains(" from goods g"));
        assertFalse(jdbc.sql.contains("content_post"));
    }

    @Test
    public void categoryUsesAllDescendantsAndFallbackSearchIsBoundWhileFollowingRestrictsBothSources() {
        CapturingJdbc jdbc = new CapturingJdbc();
        UserClientHandler users = mock(UserClientHandler.class);
        IndexMapper index = mock(IndexMapper.class);
        FeedService service = new FeedService(jdbc, users, index, new ObjectMapper());
        jdbc.categories = Arrays.asList(category(10, 0, "家用电器"), category(11, 10, "厨房电器"),
                category(12, 11, "电饭煲"), category(20, 0, "家用电器配件专区"));
        Map<String, Object> result = service.feed("FEATURED", "RECOMMENDED", "appliances", null, 1, 20, null);
        assertTrue(jdbc.sql.contains("g.category_id in (?,?,?)"));
        assertEquals(Arrays.asList(10, 11, 12), jdbc.args);
        assertFalse(jdbc.sql.contains(" like "));
        assertFalse(jdbc.sql.contains("content_post"));
        assertEquals(Collections.emptyList(), result.get("items"));
        assertEquals(0L, result.get("total"));
        assertEquals(false, result.get("hasMore"));
        verify(index).findAd();

        jdbc.categories = Arrays.asList(category(1, 0, "手机数码"), category(5, 1, "鼠标"), category(8, 1, "电脑配件"),
                category(11, 1, "笔记本电脑"), category(58, 53, "办公用品"), category(43, 0, "家用电器"), category(45, 43, "厨房电器"));
        service.feed("FEATURED", "NEW", "office", null, 2, 20, null);
        assertEquals(new HashSet<>(Arrays.asList(5, 8, 11, 58)), new HashSet<>(jdbc.args));
        service.feed("FEATURED", "NEW", "digital", null, 2, 20, null);
        assertEquals(new HashSet<>(Arrays.asList(1, 5, 8, 11, 43, 45)), new HashSet<>(jdbc.args));

        jdbc.categories = Collections.emptyList();
        service.feed("FEATURED", "NEW", "broadband", "100%_!套餐", 2, 20, null);
        assertFalse(jdbc.sql.contains("g.category_id in"));
        assertTrue(jdbc.sql.contains("like ? escape '!'"));
        assertTrue(jdbc.args.contains("%宽带%"));
        assertTrue(jdbc.args.contains("%100!%!_!!套餐%"));
        assertFalse(jdbc.sql.contains("100%_!套餐"));

        service.feed("HOME", "FOLLOWING", null, null, 2, 20, "viewer");
        assertTrue(jdbc.sql.contains(" union all "));
        assertTrue(jdbc.sql.contains("f.followed_id=g.seller_id"));
        assertTrue(jdbc.sql.contains("f.followed_id=p.author_id"));
        assertTrue(jdbc.sql.contains("g.is_selling=1 and g.is_delete=0"));
        assertTrue(jdbc.sql.contains("p.kind='COMMUNITY' and p.status='PUBLISHED'"));
        assertEquals(Arrays.asList("viewer", "viewer"), jdbc.args);
        verifyNoMoreInteractions(index);
        verifyZeroInteractions(users);
    }

    private static Category category(int id, int parentId, String name) {
        Category category = new Category();
        category.setId(id);
        category.setParentId(parentId);
        category.setName(name);
        return category;
    }

    private static void rejects(int errno, Runnable action) {
        try {
            action.run();
            fail("Invalid request must be rejected");
        } catch (ContentException expected) {
            assertEquals(errno, expected.getErrno());
        }
    }

    private static class CapturingJdbc extends JdbcTemplate {
        private List<Category> categories = Collections.emptyList();
        private String sql;
        private List<Object> args;

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> mapper) {
            assertEquals("select id,name,parent_id from category", sql);
            return (List<T>) categories;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            this.sql = sql;
            this.args = Arrays.asList(args);
            return requiredType.cast(0L);
        }
    }
}
