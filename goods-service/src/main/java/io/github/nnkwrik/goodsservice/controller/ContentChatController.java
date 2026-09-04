package io.github.nnkwrik.goodsservice.controller;

import fangxianyu.innerApi.im.ImClient;
import io.github.nnkwrik.common.dto.*;
import io.github.nnkwrik.common.token.injection.JWT;
import io.github.nnkwrik.goodsservice.model.po.ContentPost;
import io.github.nnkwrik.goodsservice.service.ContentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class ContentChatController {
    @Autowired private ContentService contents;
    @Autowired private ImClient chats;

    @PostMapping("/post/entries/{id}/chat")
    public Response chat(@PathVariable int id, @JWT(required=true) JWTUser user) {
        ContentPost post = contents.getPost(id,user.getOpenId());
        if (!"PUBLISHED".equals(post.getStatus())) throw new IllegalArgumentException("该内容已下架");
        if (user.getOpenId().equals(post.getAuthorId())) throw new IllegalArgumentException("不能与自己聊天");
        Response<Integer> result = chats.createContentChat(id,user.getOpenId(),post.getAuthorId());
        if (result.getErrno()!=0 || result.getData()==null) return Response.fail(5002,"聊天暂时不可用，请稍后重试");
        return Response.ok(Collections.singletonMap("chatId",result.getData()));
    }

    @GetMapping("/goods-service/simpleContentList")
    public Response<Map<Integer,SimpleContent>> summaries(@RequestParam List<Integer> postIds) {
        if (postIds.isEmpty() || postIds.size()>100) throw new IllegalArgumentException("内容数量不正确");
        Map<Integer,SimpleContent> result = new LinkedHashMap<>();
        for (ContentPost post:contents.getPostsByIds(postIds)) {
            SimpleContent item=new SimpleContent();
            item.setId(post.getId()); item.setKind(post.getKind()); item.setTitle(post.getTitle()); item.setStatus(post.getStatus());
            if (post.getImages()!=null && !post.getImages().isEmpty()) item.setPrimaryPicUrl(post.getImages().get(0));
            result.put(item.getId(),item);
        }
        return Response.ok(result);
    }
}
