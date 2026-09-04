package io.github.nnkwrik.common.dto;

import lombok.Data;

@Data
public class SimpleContent {
    private Integer id;
    private String kind;
    private String title;
    private String primaryPicUrl;
    private String status;
}
