package io.github.nnkwrik.goodsservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fangxianyu.innerApi.user.UserClientHandler;
import io.github.nnkwrik.goodsservice.model.po.ContentComment;
import io.github.nnkwrik.goodsservice.model.po.ContentPost;
import io.github.nnkwrik.goodsservice.model.po.RecruitmentJob;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ContentServiceTests {
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private UserClientHandler users;
    private ContentService service;

    @Before
    public void setUp() {
        service = new ContentService(jdbc, new ObjectMapper(), users);
    }

    @Test
    public void validatesPostTextImageUrlsAndRecruitmentFields() {
        ContentPost post = community();
        post.setImages(Collections.singletonList("https://example.com/image.jpg"));
        ContentService.validatePost(post);
        assertEquals("附近的活动", post.getTitle());
        for (String url : Arrays.asList("javascript:alert(1)", "file:///tmp/a.jpg", "https://user:secret@example.com/a.jpg", "https:///image.jpg")) {
            post.setImages(Collections.singletonList(url));
            invalid(() -> ContentService.validatePost(post));
        }
        post.setImages(Collections.nCopies(10, "https://example.com/a.jpg"));
        invalid(() -> ContentService.validatePost(post));
        post.setImages(Collections.emptyList());
        post.setBody("  ");
        invalid(() -> ContentService.validatePost(post));

        ContentPost recruitment = recruitment();
        ContentService.validatePost(recruitment);
        recruitment.getJob().setSalary(new BigDecimal("-1"));
        invalid(() -> ContentService.validatePost(recruitment));
        recruitment.getJob().setSalary(new BigDecimal("100.123"));
        invalid(() -> ContentService.validatePost(recruitment));
        recruitment.getJob().setSalary(new BigDecimal("100"));
        recruitment.getJob().setHeadcount(0);
        invalid(() -> ContentService.validatePost(recruitment));
        recruitment.getJob().setHeadcount(1);
        recruitment.getJob().setContactPhone("not-a-phone");
        invalid(() -> ContentService.validatePost(recruitment));
    }

    @Test
    public void rejectsUnboundedPaginationBeforeQueryingDatabase() {
        invalid(() -> ContentService.validatePage(0, 10));
        invalid(() -> ContentService.validatePage(1, 0));
        invalid(() -> ContentService.validatePage(1, 51));
        invalid(() -> ContentService.validatePage(Integer.MAX_VALUE, 50));
        ContentService.validatePage(1, 50);
        verifyZeroInteractions(jdbc);
    }

    @Test
    public void cannotEditAnotherUsersContent() {
        when(jdbc.query(anyString(), anyPostMapper(), eq(7))).thenReturn(Collections.singletonList(storedPost()));
        try {
            service.update(7, community(), "other-user");
            fail("Another user's content must not be editable");
        } catch (ContentException error) {
            assertEquals(ContentService.FORBIDDEN, error.getErrno());
        }
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    public void rejectsReplyToCommentOutsideTheCurrentPost() {
        when(jdbc.query(contains("from content_post p"), anyPostMapper(), eq(7)))
                .thenReturn(Collections.singletonList(storedPost()));
        when(jdbc.query(contains("where id=? and post_id=? and deleted=0"), anyCommentMapper(), eq(99), eq(7)))
                .thenReturn(Collections.emptyList());
        invalid(() -> service.comment(7, "reader", "这条留言不能跨帖子回复", 99));
        verify(jdbc, never()).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    public void readAllUsesEachSourcesOwnRecipientAndSnapshotLimit() {
        service.readAllNotifications("reader", 8, 3);
        verify(jdbc).update("update content_comment set read_at=now() where recipient_id=? and id<=? and read_at is null", "reader", 8);
        verify(jdbc).update("update goods_comment set read_at=now() where reply_user_id=? and id<=? and read_at is null", "reader", 3);
        invalid(() -> service.readAllNotifications("reader", -1, 3));
        invalid(() -> service.readNotifications("reader", Arrays.asList(1, -2), Collections.emptyList()));
        invalid(() -> service.readNotifications("reader", Collections.emptyList(), Collections.emptyList()));
    }

    private ContentPost community() {
        ContentPost post = new ContentPost();
        post.setKind("COMMUNITY");
        post.setTitle(" 附近的活动 ");
        post.setBody("周末一起去盐湖散步。");
        return post;
    }

    private ContentPost storedPost() {
        ContentPost post = community();
        post.setId(7);
        post.setAuthorId("owner");
        post.setStatus("PUBLISHED");
        return post;
    }

    private ContentPost recruitment() {
        ContentPost post = community();
        post.setKind("RECRUITMENT");
        RecruitmentJob job = new RecruitmentJob();
        job.setWorkType("PART_TIME");
        job.setIndustry("餐饮服务");
        job.setSalary(new BigDecimal("180.00"));
        job.setSalaryUnit("DAY");
        job.setSettlement("WEEKLY");
        job.setAddress("运城市盐湖区");
        job.setHeadcount(2);
        job.setCompany("本地餐厅");
        job.setRequirements("周末可以到岗");
        job.setContactName("李女士");
        job.setContactPhone("13800138000");
        post.setJob(job);
        return post;
    }

    private void invalid(Runnable action) {
        try {
            action.run();
            fail("Invalid input must be rejected");
        } catch (ContentException error) {
            assertEquals(ContentService.INVALID, error.getErrno());
        }
    }

    private RowMapper<ContentPost> anyPostMapper() {
        return any();
    }

    private RowMapper<ContentComment> anyCommentMapper() {
        return any();
    }
}
