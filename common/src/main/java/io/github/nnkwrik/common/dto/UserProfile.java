package io.github.nnkwrik.common.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserProfile extends SimpleUser {
    private String bio;
    private Integer gender;
    private String region;
}
