package io.github.nnkwrik.goodsservice.controller;

import io.github.nnkwrik.common.dto.JWTUser;
import io.github.nnkwrik.common.dto.Response;
import io.github.nnkwrik.common.token.injection.JWT;
import io.github.nnkwrik.goodsservice.model.po.ContentComment;
import io.github.nnkwrik.goodsservice.model.po.ContentPost;
import io.github.nnkwrik.goodsservice.service.ContentService;
import lombok.Data;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestController
@RequestMapping("/post")
public class ContentController {
    private final ContentService content;

    public ContentController(ContentService content) {
        this.content = content;
    }

    @GetMapping("/entries")
    public Response entries(@RequestParam(required = false) String kind,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false) String workType,
                            @RequestParam(required = false) String industry,
                            @RequestParam(required = false) String settlement,
                            @RequestParam(defaultValue = "latest") String sort,
                            @RequestParam(required = false) String authorId,
                            @RequestParam(defaultValue = "false") boolean following,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "10") int size,
                            @JWT JWTUser user) {
        return Response.ok(content.list(kind, keyword, workType, industry, settlement, sort, authorId,
                following, false, false, page, size, userId(user)));
    }

    @GetMapping("/entries/{id}")
    public Response detail(@PathVariable int id, @JWT JWTUser user) {
        return Response.ok(content.getPost(id, userId(user)));
    }

    @PostMapping("/entries")
    public Response create(@RequestBody ContentPost post, @JWT(required = true) JWTUser user) {
        return Response.ok(content.create(post, userId(user)));
    }

    @PutMapping("/entries/{id}")
    public Response update(@PathVariable int id, @RequestBody ContentPost post, @JWT(required = true) JWTUser user) {
        return Response.ok(content.update(id, post, userId(user)));
    }

    @PutMapping("/entries/{id}/status")
    public Response status(@PathVariable int id, @RequestBody ContentAction action, @JWT(required = true) JWTUser user) {
        content.setStatus(id, action.getStatus(), userId(user));
        return Response.ok();
    }

    @DeleteMapping("/entries/{id}")
    public Response delete(@PathVariable int id, @JWT(required = true) JWTUser user) {
        content.delete(id, userId(user));
        return Response.ok();
    }

    @GetMapping("/mine")
    public Response mine(@RequestParam(required = false) String kind,
                         @RequestParam(defaultValue = "1") int page,
                         @RequestParam(defaultValue = "10") int size,
                         @JWT(required = true) JWTUser user) {
        return Response.ok(content.list(kind, null, null, null, null, "LATEST", null,
                false, true, false, page, size, userId(user)));
    }

    @GetMapping("/favorites")
    public Response favorites(@RequestParam(required = false) String kind,
                              @RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "10") int size,
                              @JWT(required = true) JWTUser user) {
        return Response.ok(content.list(kind, null, null, null, null, "LATEST", null,
                false, false, true, page, size, userId(user)));
    }

    @PutMapping("/entries/{id}/{reaction:like|favorite}")
    public Response react(@PathVariable int id, @PathVariable String reaction, @JWT(required = true) JWTUser user) {
        content.react(id, userId(user), reaction, true);
        return Response.ok();
    }

    @DeleteMapping("/entries/{id}/{reaction:like|favorite}")
    public Response unreact(@PathVariable int id, @PathVariable String reaction, @JWT(required = true) JWTUser user) {
        content.react(id, userId(user), reaction, false);
        return Response.ok();
    }

    @GetMapping("/entries/{id}/comments")
    public Response comments(@PathVariable int id, @RequestParam(defaultValue = "1") int page,
                             @RequestParam(defaultValue = "10") int size, @JWT JWTUser user) {
        return Response.ok(content.comments(id, userId(user), page, size));
    }

    @PostMapping("/entries/{id}/comments")
    public Response comment(@PathVariable int id, @RequestBody ContentComment comment, @JWT(required = true) JWTUser user) {
        return Response.ok(content.comment(id, userId(user), comment.getBody(), comment.getReplyCommentId()));
    }

    @DeleteMapping("/comments/{id}")
    public Response deleteComment(@PathVariable int id, @JWT(required = true) JWTUser user) {
        content.deleteComment(id, userId(user));
        return Response.ok();
    }

    @GetMapping("/notifications")
    public Response notifications(@RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "10") int size, @JWT(required = true) JWTUser user) {
        return Response.ok(content.notifications(userId(user), page, size));
    }

    @PostMapping("/notifications/read")
    public Response read(@RequestBody ContentAction action, @JWT(required = true) JWTUser user) {
        content.readNotifications(userId(user), action.getIds(), action.getGoodsIds());
        return Response.ok();
    }

    @PostMapping("/notifications/read-all")
    public Response readAll(@RequestBody ContentAction action, @JWT(required = true) JWTUser user) {
        content.readAllNotifications(userId(user), action.getMaxId(), action.getGoodsMaxId());
        return Response.ok();
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public Response invalidRequest(Exception error) {
        return Response.fail(ContentService.INVALID, "请求参数格式错误");
    }

    private String userId(JWTUser user) {
        return user == null ? null : user.getOpenId();
    }

    @Data
    public static class ContentAction {
        private String status;
        private List<Integer> ids;
        private List<Integer> goodsIds;
        private Integer maxId;
        private Integer goodsMaxId;
    }
}
