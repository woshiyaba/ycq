package io.github.nnkwrik.goodsservice.model.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.nnkwrik.common.dto.SimpleUser;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class ContentPost {
    private Integer id;
    private String kind;
    private String authorId;
    private String title;
    private String body;
    private List<String> images;
    @JsonIgnore
    private String imagesJson;
    private String region;
    private String status;
    private Date createdAt;
    private Date updatedAt;
    private SimpleUser author;
    private RecruitmentJob job;
    private boolean liked;
    private boolean favorited;
    private int likeCount;
    private int commentCount;
}
