package io.github.nnkwrik.goodsservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fangxianyu.innerApi.user.UserClientHandler;
import io.github.nnkwrik.common.dto.SimpleUser;
import io.github.nnkwrik.goodsservice.model.po.ContentComment;
import io.github.nnkwrik.goodsservice.model.po.ContentPost;
import io.github.nnkwrik.goodsservice.model.po.RecruitmentJob;
import io.github.nnkwrik.goodsservice.model.vo.ContentPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ContentService {
    public static final int INVALID = 6001;
    public static final int NOT_FOUND = 6002;
    public static final int FORBIDDEN = 6003;
    private static final String POST_COLUMNS = "p.id,p.kind,p.author_id,p.title,p.body,p.images images_json," +
            "p.region,p.status,p.created_at,p.updated_at";
    private static final String POST_SELECT = "select " + POST_COLUMNS +
            ",(select count(*) from content_reaction r where r.post_id=p.id and r.type='LIKE') like_count" +
            ",(select count(*) from content_comment c where c.post_id=p.id and c.deleted=0) comment_count";
    private static final String JOB_SELECT = "select post_id,work_type,industry,salary,salary_unit,settlement," +
            "address,headcount,company,requirements,benefits benefits_json,contact_name,contact_phone from recruitment_job";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final UserClientHandler users;

    public ContentService(JdbcTemplate jdbc, ObjectMapper json, UserClientHandler users) {
        this.jdbc = jdbc;
        this.json = json;
        this.users = users;
    }

    public ContentPage<ContentPost> list(String kind, String keyword, String workType, String industry,
                                       String settlement, String sort, String authorId, boolean following,
                                       boolean mine, boolean favorites, int page, int size, String viewerId) {
        validatePage(page, size);
        kind = optionalEnum(kind, "内容类型", "COMMUNITY", "RECRUITMENT");
        workType = optionalEnum(workType, "招聘类型", "FULL_TIME", "PART_TIME");
        List<String> settlements = new ArrayList<>();
        if (settlement != null && !settlement.trim().isEmpty()) {
            if (settlement.length() > 80) throw invalid("结薪方式无效");
            for (String value : settlement.split(",", -1)) {
                settlements.add(requiredEnum(value, "结薪方式", "MONTHLY", "WEEKLY", "DAILY", "ON_COMPLETION"));
            }
        }
        sort = optionalEnum(sort, "排序", "LATEST", "RECOMMENDED", "SALARY", "HIGH_SALARY");
        keyword = text(keyword, "关键词", 100, false);
        industry = text(industry, "行业", 40, false);
        authorId = text(authorId, "作者", 32, false);
        StringBuilder where = new StringBuilder(mine ? " where p.status<>'DELETED'" : " where p.status='PUBLISHED'");
        List<Object> args = new ArrayList<>();
        filter(where, args, "p.kind", kind);
        filter(where, args, "j.work_type", workType);
        filter(where, args, "j.industry", industry);
        if (!settlements.isEmpty()) {
            where.append(" and j.settlement in (").append(marks(settlements.size())).append(")");
            args.addAll(settlements);
        }
        if (!keyword.isEmpty()) {
            String like = "%" + keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.append(" and (p.title like ? or p.body like ? or j.company like ?)");
            Collections.addAll(args, like, like, like);
        }
        filter(where, args, "p.author_id", mine ? requireUser(viewerId) : authorId);
        if (following) {
            where.append(" and exists(select 1 from user_follow f where f.follower_id=? and f.followed_id=p.author_id)");
            args.add(requireUser(viewerId));
        }
        if (favorites) {
            where.append(" and exists(select 1 from content_reaction r where r.post_id=p.id and r.user_id=? and r.type='FAVORITE')");
            args.add(requireUser(viewerId));
        }
        if ("HIGH_SALARY".equals(sort)) {
            where.append(" and p.kind='RECRUITMENT' and j.work_type='PART_TIME' and j.salary_unit<>'NEGOTIABLE'");
        }
        String from = " from content_post p left join recruitment_job j on j.post_id=p.id" + where;
        long total = jdbc.queryForObject("select count(*)" + from, Long.class, args.toArray());
        String order = " order by p.created_at desc,p.id desc";
        if ("RECOMMENDED".equals(sort)) order = " order by like_count desc,p.created_at desc,p.id desc";
        // Salary units are grouped: an hourly amount must not be compared directly with a monthly amount.
        if ("SALARY".equals(sort) || "HIGH_SALARY".equals(sort)) order = " order by j.salary_unit,j.salary desc,p.id desc";
        args.add(size);
        args.add((page - 1) * size);
        List<ContentPost> posts = jdbc.query(POST_SELECT + from + order + " limit ? offset ?", postMapper(), args.toArray());
        enrichPosts(posts, viewerId);
        return new ContentPage<>(posts, total, page, size);
    }

    public ContentPost getPost(int id, String viewerId) {
        ContentPost post = visiblePost(id, viewerId, false);
        enrichPosts(Collections.singletonList(post), viewerId);
        return post;
    }

    public List<ContentPost> getPostsByIds(List<Integer> ids) {
        validateIds(ids, true);
        if (ids.isEmpty()) return Collections.emptyList();
        List<ContentPost> posts = jdbc.query(POST_SELECT + " from content_post p where p.status='PUBLISHED' and p.id in (" +
                marks(ids.size()) + ")", postMapper(), ids.toArray());
        enrichPosts(posts, null);
        return posts;
    }

    @Transactional
    public ContentPost create(ContentPost post, String userId) {
        requireUser(userId);
        validatePost(post);
        String images = encode(post.getImages());
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("insert into content_post " +
                    "(kind,author_id,title,body,images,region) values (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, post.getKind());
            statement.setString(2, userId);
            statement.setString(3, post.getTitle());
            statement.setString(4, post.getBody());
            statement.setString(5, images);
            statement.setString(6, post.getRegion());
            return statement;
        }, key);
        int id = Objects.requireNonNull(key.getKey()).intValue();
        if (post.getJob() != null) saveJob(id, post.getJob());
        return getPost(id, userId);
    }

    @Transactional
    public ContentPost update(int id, ContentPost post, String userId) {
        ContentPost previous = ownedPost(id, userId);
        validatePost(post);
        if (!previous.getKind().equals(post.getKind())) throw invalid("不能更改内容类型");
        jdbc.update("update content_post set title=?,body=?,images=?,region=?,updated_at=now() where id=?",
                post.getTitle(), post.getBody(), encode(post.getImages()), post.getRegion(), id);
        if (post.getJob() != null) saveJob(id, post.getJob());
        return getPost(id, userId);
    }

    @Transactional
    public void setStatus(int id, String status, String userId) {
        status = requiredEnum(status, "状态", "PUBLISHED", "OFFLINE");
        ownedPost(id, userId);
        jdbc.update("update content_post set status=?,updated_at=now() where id=?", status, id);
    }

    @Transactional
    public void delete(int id, String userId) {
        ownedPost(id, userId);
        jdbc.update("update content_post set status='DELETED',updated_at=now() where id=?", id);
    }

    @Transactional
    public void react(int id, String userId, String type, boolean add) {
        requireUser(userId);
        type = requiredEnum(type, "互动类型", "LIKE", "FAVORITE");
        validateId(id);
        if (add) {
            publishedPost(id, userId);
            jdbc.update("insert into content_reaction(post_id,user_id,type) values(?,?,?) " +
                    "on duplicate key update post_id=values(post_id)", id, userId, type);
        } else {
            jdbc.update("delete from content_reaction where post_id=? and user_id=? and type=?", id, userId, type);
        }
    }

    public ContentPage<ContentComment> comments(int id, String viewerId, int page, int size) {
        validatePage(page, size);
        visiblePost(id, viewerId, false);
        long total = jdbc.queryForObject("select count(*) from content_comment where post_id=? and deleted=0", Long.class, id);
        List<ContentComment> comments = jdbc.query("select * from content_comment where post_id=? and deleted=0 " +
                        "order by created_at,id limit ? offset ?", commentMapper(), id, size, (page - 1) * size);
        enrichComments(comments);
        return new ContentPage<>(comments, total, page, size);
    }

    @Transactional
    public ContentComment comment(int id, String userId, String body, Integer replyCommentId) {
        requireUser(userId);
        body = text(body, "留言", 2000, true);
        ContentPost post = publishedPost(id, userId);
        int replyId = replyCommentId == null ? 0 : replyCommentId;
        if (replyId < 0) throw invalid("回复留言不存在");
        int parentId = 0;
        String recipient = post.getAuthorId();
        if (replyId > 0) {
            List<ContentComment> replies = jdbc.query("select * from content_comment where id=? and post_id=? and deleted=0 for update",
                    commentMapper(), replyId, id);
            if (replies.isEmpty()) throw invalid("只能回复当前内容中存在的留言");
            ContentComment reply = replies.get(0);
            parentId = reply.getParentId() == 0 ? reply.getId() : reply.getParentId();
            recipient = reply.getAuthorId();
        }
        final int rootId = parentId;
        final String recipientId = recipient;
        final String message = body;
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("insert into content_comment " +
                    "(post_id,author_id,parent_id,reply_comment_id,recipient_id,body,read_at) values(?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setInt(1, id);
            statement.setString(2, userId);
            statement.setInt(3, rootId);
            statement.setInt(4, replyId);
            statement.setString(5, recipientId);
            statement.setString(6, message);
            statement.setTimestamp(7, userId.equals(recipientId) ? new java.sql.Timestamp(System.currentTimeMillis()) : null);
            return statement;
        }, key);
        ContentComment comment = jdbc.queryForObject("select * from content_comment where id=?", commentMapper(), key.getKey());
        enrichComments(Collections.singletonList(comment));
        return comment;
    }

    @Transactional
    public void deleteComment(int id, String userId) {
        requireUser(userId);
        validateId(id);
        List<ContentComment> found = jdbc.query("select * from content_comment where id=? and deleted=0 for update", commentMapper(), id);
        if (found.isEmpty()) throw new ContentException(NOT_FOUND, "留言不存在");
        if (!userId.equals(found.get(0).getAuthorId())) throw new ContentException(FORBIDDEN, "只能删除自己的留言");
        jdbc.update("update content_comment set deleted=1 where id=?", id);
    }

    public Map<String, Object> notifications(String userId, int page, int size) {
        requireUser(userId);
        validatePage(page, size);
        String from = " from (" + notificationQuery() + ") n";
        Map<String, Object> counts = jdbc.queryForMap("select count(*) total,coalesce(sum(read_at is null),0) unread_count," +
                "coalesce(max(case when source='CONTENT' then id else 0 end),0) max_id," +
                "coalesce(max(case when source='GOODS' then id else 0 end),0) goods_max_id" + from, userId, userId);
        long total = ((Number) counts.get("total")).longValue();
        List<ContentComment> comments = jdbc.query("select n.*" + from +
                        " order by created_at desc,source,id desc limit ? offset ?", commentMapper(), userId, userId, size, (page - 1) * size);
        enrichComments(comments);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", comments);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("hasMore", (long) page * size < total);
        result.put("unreadCount", counts.get("unread_count"));
        result.put("maxId", counts.get("max_id"));
        result.put("goodsMaxId", counts.get("goods_max_id"));
        return result;
    }

    @Transactional
    public void readNotifications(String userId, List<Integer> ids, List<Integer> goodsIds) {
        requireUser(userId);
        ids = ids == null ? Collections.emptyList() : ids;
        goodsIds = goodsIds == null ? Collections.emptyList() : goodsIds;
        validateIds(ids, true);
        validateIds(goodsIds, true);
        if (ids.isEmpty() && goodsIds.isEmpty()) throw invalid("请选择需要标记的通知");
        if (!ids.isEmpty()) markRead(userId, ids, "content_comment", "recipient_id");
        if (!goodsIds.isEmpty()) markRead(userId, goodsIds, "goods_comment", "reply_user_id");
    }

    @Transactional
    public void readAllNotifications(String userId, Integer maxId, Integer goodsMaxId) {
        requireUser(userId);
        if ((maxId == null && goodsMaxId == null) || (maxId != null && maxId < 0) || (goodsMaxId != null && goodsMaxId < 0)) {
            throw invalid("缺少有效的通知截止编号");
        }
        if (maxId != null) jdbc.update("update content_comment set read_at=now() where recipient_id=? and id<=? and read_at is null", userId, maxId);
        if (goodsMaxId != null) jdbc.update("update goods_comment set read_at=now() where reply_user_id=? and id<=? and read_at is null", userId, goodsMaxId);
    }

    private void markRead(String userId, List<Integer> ids, String table, String recipientColumn) {
        List<Object> args = new ArrayList<>(ids);
        args.add(userId);
        jdbc.update("update " + table + " set read_at=now() where id in (" + marks(ids.size()) +
                ") and " + recipientColumn + "=? and read_at is null", args.toArray());
    }

    private String notificationQuery() {
        return "select 'CONTENT' source,c.id,c.post_id,null goods_id,c.author_id,c.parent_id,c.reply_comment_id," +
                "c.recipient_id,c.body,c.created_at,c.read_at,p.title post_title,p.kind post_kind " +
                "from content_comment c join content_post p on p.id=c.post_id where c.recipient_id=? " +
                "and c.author_id<>c.recipient_id and c.deleted=0 and p.status<>'DELETED' " +
                "union all select 'GOODS' source,c.id,null post_id,c.goods_id,c.user_id author_id,c.reply_comment_id parent_id," +
                "c.reply_comment_id,c.reply_user_id recipient_id,c.content body,c.create_time created_at,c.read_at,g.name post_title,'GOODS' post_kind " +
                "from goods_comment c join goods g on g.id=c.goods_id where c.reply_user_id=? " +
                "and c.user_id<>c.reply_user_id and c.is_delete=0 and g.is_delete=0";
    }

    private ContentPost visiblePost(int id, String viewerId, boolean lock) {
        validateId(id);
        List<ContentPost> posts = jdbc.query((lock ? "select " + POST_COLUMNS : POST_SELECT) +
                " from content_post p where p.id=?" + (lock ? " for update" : ""), postMapper(), id);
        if (posts.isEmpty() || "DELETED".equals(posts.get(0).getStatus())) throw new ContentException(NOT_FOUND, "内容不存在或已删除");
        ContentPost post = posts.get(0);
        if (!"PUBLISHED".equals(post.getStatus()) && !post.getAuthorId().equals(viewerId)) {
            throw new ContentException(NOT_FOUND, "内容已下架");
        }
        return post;
    }

    private ContentPost ownedPost(int id, String userId) {
        requireUser(userId);
        ContentPost post = visiblePost(id, userId, true);
        if (!userId.equals(post.getAuthorId())) throw new ContentException(FORBIDDEN, "只能修改自己发布的内容");
        return post;
    }

    private ContentPost publishedPost(int id, String viewerId) {
        ContentPost post = visiblePost(id, viewerId, true);
        if (!"PUBLISHED".equals(post.getStatus())) throw invalid("内容已下架，无法继续互动");
        return post;
    }

    private void saveJob(int id, RecruitmentJob job) {
        jdbc.update("insert into recruitment_job(post_id,work_type,industry,salary,salary_unit,settlement,address,headcount," +
                        "company,requirements,benefits,contact_name,contact_phone) values(?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                        "on duplicate key update work_type=values(work_type),industry=values(industry),salary=values(salary)," +
                        "salary_unit=values(salary_unit),settlement=values(settlement),address=values(address),headcount=values(headcount)," +
                        "company=values(company),requirements=values(requirements),benefits=values(benefits),contact_name=values(contact_name),contact_phone=values(contact_phone)",
                id, job.getWorkType(), job.getIndustry(), job.getSalary(), job.getSalaryUnit(), job.getSettlement(), job.getAddress(),
                job.getHeadcount(), job.getCompany(), job.getRequirements(), encode(job.getBenefits()), job.getContactName(), job.getContactPhone());
    }

    private void enrichPosts(List<ContentPost> posts, String viewerId) {
        if (posts.isEmpty()) return;
        List<Integer> ids = posts.stream().map(ContentPost::getId).collect(Collectors.toList());
        Map<Integer, RecruitmentJob> jobs = new HashMap<>();
        jdbc.query(JOB_SELECT + " where post_id in (" + marks(ids.size()) + ")", new BeanPropertyRowMapper<>(RecruitmentJob.class), ids.toArray())
                .forEach(job -> {
                    job.setBenefits(decode(job.getBenefitsJson()));
                    jobs.put(job.getPostId(), job);
                });
        Map<String, SimpleUser> authors = findUsers(posts.stream().map(ContentPost::getAuthorId).collect(Collectors.toList()));
        Map<Integer, Set<String>> reactions = new HashMap<>();
        if (viewerId != null) {
            List<Object> args = new ArrayList<>(ids);
            args.add(viewerId);
            jdbc.query("select post_id,type from content_reaction where post_id in (" + marks(ids.size()) + ") and user_id=?",
                    rs -> {
                        reactions.computeIfAbsent(rs.getInt("post_id"), ignored -> new HashSet<>()).add(rs.getString("type"));
                    }, args.toArray());
        }
        for (ContentPost post : posts) {
            post.setImages(decode(post.getImagesJson()));
            post.setAuthor(user(authors, post.getAuthorId()));
            post.setJob(jobs.get(post.getId()));
            Set<String> types = reactions.getOrDefault(post.getId(), Collections.emptySet());
            post.setLiked(types.contains("LIKE"));
            post.setFavorited(types.contains("FAVORITE"));
        }
    }

    private void enrichComments(List<ContentComment> comments) {
        List<String> ids = new ArrayList<>();
        for (ContentComment comment : comments) {
            ids.add(comment.getAuthorId());
            ids.add(comment.getRecipientId());
        }
        Map<String, SimpleUser> authors = findUsers(ids);
        for (ContentComment comment : comments) {
            if (comment.getSource() == null) comment.setSource("CONTENT");
            comment.setAuthor(user(authors, comment.getAuthorId()));
            comment.setReplyUser(user(authors, comment.getRecipientId()));
        }
    }

    private Map<String, SimpleUser> findUsers(List<String> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        try {
            Map<String, SimpleUser> result = users.getSimpleUserList(new ArrayList<>(new LinkedHashSet<>(ids)));
            return result == null ? Collections.emptyMap() : result;
        } catch (RuntimeException error) {
            log.warn("用户服务暂不可用，内容保留默认头像和名称", error);
            return Collections.emptyMap();
        }
    }

    private SimpleUser user(Map<String, SimpleUser> users, String id) {
        if (users.containsKey(id) && users.get(id) != null) return users.get(id);
        SimpleUser result = SimpleUser.unknownUser();
        result.setOpenId(id);
        result.setNickName("圈友");
        return result;
    }

    private BeanPropertyRowMapper<ContentPost> postMapper() {
        return new BeanPropertyRowMapper<>(ContentPost.class);
    }

    private BeanPropertyRowMapper<ContentComment> commentMapper() {
        return new BeanPropertyRowMapper<>(ContentComment.class);
    }

    private String encode(List<String> values) {
        try {
            return json.writeValueAsString(values);
        } catch (Exception error) {
            throw invalid("图片或福利格式错误");
        }
    }

    private List<String> decode(String value) {
        try {
            return value == null ? Collections.emptyList() : json.readValue(value, new TypeReference<List<String>>() {});
        } catch (Exception error) {
            throw new IllegalStateException("内容列表字段无法读取", error);
        }
    }

    static void validatePost(ContentPost post) {
        if (post == null) throw invalid("发布内容不能为空");
        post.setKind(requiredEnum(post.getKind(), "内容类型", "COMMUNITY", "RECRUITMENT"));
        post.setTitle(text(post.getTitle(), "标题", 100, true));
        post.setBody(text(post.getBody(), "正文", 10000, "COMMUNITY".equals(post.getKind())));
        post.setRegion(text(post.getRegion(), "地区", 100, false));
        List<String> images = post.getImages() == null ? new ArrayList<>() : new ArrayList<>(post.getImages());
        if (images.size() > 9) throw invalid("最多上传9张图片");
        for (int i = 0; i < images.size(); i++) {
            String image = text(images.get(i), "图片地址", 1000, true);
            try {
                URI uri = new URI(image);
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) ||
                        uri.getHost() == null || uri.getUserInfo() != null) throw invalid("图片必须使用有效的HTTP或HTTPS地址");
            } catch (java.net.URISyntaxException error) {
                throw invalid("图片地址格式错误");
            }
            images.set(i, image);
        }
        post.setImages(images);
        if ("COMMUNITY".equals(post.getKind())) {
            post.setJob(null);
            return;
        }
        RecruitmentJob job = post.getJob();
        if (job == null) throw invalid("招聘信息不能为空");
        job.setWorkType(requiredEnum(job.getWorkType(), "招聘类型", "FULL_TIME", "PART_TIME"));
        job.setIndustry(text(job.getIndustry(), "行业", 40, true));
        job.setSalaryUnit(requiredEnum(job.getSalaryUnit(), "薪资单位", "MONTH", "DAY", "HOUR", "ONCE", "NEGOTIABLE"));
        if ("NEGOTIABLE".equals(job.getSalaryUnit())) job.setSalary(BigDecimal.ZERO);
        if (job.getSalary() == null || job.getSalary().signum() < 0 || job.getSalary().compareTo(new BigDecimal("99999999.99")) > 0 ||
                job.getSalary().scale() > 2) throw invalid("薪资须为不超过两位小数的有效金额");
        job.setSettlement(requiredEnum(job.getSettlement(), "结薪方式", "MONTHLY", "WEEKLY", "DAILY", "ON_COMPLETION"));
        job.setAddress(text(job.getAddress(), "工作地址", 200, true));
        if (job.getHeadcount() == null) job.setHeadcount(1);
        if (job.getHeadcount() < 1 || job.getHeadcount() > 100000) throw invalid("招聘人数须在1至100000之间");
        job.setCompany(text(job.getCompany(), "招聘方", 100, true));
        job.setRequirements(text(job.getRequirements(), "招聘要求", 5000, true));
        List<String> benefits = job.getBenefits() == null ? new ArrayList<>() : new ArrayList<>(job.getBenefits());
        if (benefits.size() > 12) throw invalid("福利最多12项");
        for (int i = 0; i < benefits.size(); i++) benefits.set(i, text(benefits.get(i), "福利", 40, true));
        job.setBenefits(benefits);
        job.setContactName(text(job.getContactName(), "联系人", 40, true));
        job.setContactPhone(text(job.getContactPhone(), "联系电话", 30, true));
        if (!job.getContactPhone().matches("\\+?[0-9][0-9 -]{5,28}")) throw invalid("联系电话格式错误");
    }

    static void validatePage(int page, int size) {
        if (page < 1 || page > 1000000 || size < 1 || size > 50) throw invalid("页码须为正数，每页1至50条");
    }

    private static void validateIds(List<Integer> ids, boolean allowEmpty) {
        if (ids == null || (!allowEmpty && ids.isEmpty()) || ids.size() > 100) throw invalid("编号列表须包含1至100项");
        for (Integer id : ids) if (id == null || id < 1) throw invalid("编号必须为正整数");
    }

    private static void validateId(int id) {
        if (id < 1) throw invalid("编号必须为正整数");
    }

    private static String text(String value, String name, int max, boolean required) {
        value = value == null ? "" : value.trim();
        if ((required && value.isEmpty()) || value.length() > max || value.indexOf('\0') >= 0) throw invalid(name + "为空或超过长度限制");
        return value;
    }

    private static String optionalEnum(String value, String name, String... allowed) {
        return value == null || value.trim().isEmpty() ? "" : requiredEnum(value, name, allowed);
    }

    private static String requiredEnum(String value, String name, String... allowed) {
        value = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!Arrays.asList(allowed).contains(value)) throw invalid(name + "无效");
        return value;
    }

    private static String requireUser(String userId) {
        if (userId == null || userId.trim().isEmpty()) throw new ContentException(3003, "请先登录");
        return userId;
    }

    private static void filter(StringBuilder where, List<Object> args, String column, String value) {
        if (value != null && !value.isEmpty()) {
            where.append(" and ").append(column).append("=?");
            args.add(value);
        }
    }

    private static String marks(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private static ContentException invalid(String message) {
        return new ContentException(INVALID, message);
    }
}
