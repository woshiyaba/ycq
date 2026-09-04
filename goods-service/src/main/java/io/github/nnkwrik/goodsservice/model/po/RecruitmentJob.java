package io.github.nnkwrik.goodsservice.model.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RecruitmentJob {
    private Integer postId;
    private String workType;
    private String industry;
    private BigDecimal salary;
    private String salaryUnit;
    private String settlement;
    private String address;
    private Integer headcount;
    private String company;
    private String requirements;
    private List<String> benefits;
    @JsonIgnore
    private String benefitsJson;
    private String contactName;
    private String contactPhone;
}
