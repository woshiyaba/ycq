package io.github.nnkwrik.goodsservice.model.po;

import io.github.nnkwrik.common.dto.SimpleUser;
import lombok.Data;

import java.util.Date;

@Data
public class ContentComment {
    private Integer id;
    private Integer postId;
    private Integer goodsId;
    private String source;
    private String authorId;
    private Integer parentId;
    private Integer replyCommentId;
    private String recipientId;
    private String body;
    private Date createdAt;
    private Date readAt;
    private SimpleUser author;
    private SimpleUser replyUser;
    private String postTitle;
    private String postKind;

    public Integer getCommentId() {
        return id;
    }
}
