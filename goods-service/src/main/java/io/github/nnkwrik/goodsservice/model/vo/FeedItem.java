package io.github.nnkwrik.goodsservice.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.nnkwrik.common.dto.SimpleUser;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
public class FeedItem {
    private Integer id;
    private String kind;
    private String title;
    private String description;
    private String primaryPicUrl;
    private List<String> images;
    private String region;
    private BigDecimal price;
    private boolean freeShipping;
    private long followerCount;
    private SimpleUser author;
    private Date createdAt;
    @JsonIgnore
    private String authorId;
    @JsonIgnore
    private String imagesJson;
}
