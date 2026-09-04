package io.github.nnkwrik.goodsservice.model.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class Order {
    private Long id;
    private String orderNo;
    private Integer goodsId;
    private String goodsName;
    private String goodsImage;
    private String buyerId;
    private String sellerId;
    private BigDecimal amount;
    private BigDecimal postage;
    private String deliveryMethod;
    private String status;
    private String trackingNo;
    private Date createdAt;
    private Date paidAt;
    private Date shippedAt;
    private Date completedAt;
    @JsonIgnore private String requestId;
    @JsonIgnore private String addressName;
    @JsonIgnore private String addressPhone;
    @JsonIgnore private String addressRegion;
    @JsonIgnore private String addressDetail;
    @JsonIgnore private Integer reviewRating;
    @JsonIgnore private String reviewContent;
    @JsonIgnore private Date reviewCreatedAt;

    public Address getAddress() {
        if (addressName == null) return null;
        Address address = new Address();
        address.setName(addressName);
        address.setPhone(addressPhone);
        address.setRegion(addressRegion);
        address.setDetail(addressDetail);
        return address;
    }

    public Review getReview() {
        if (reviewRating == null) return null;
        Review review = new Review();
        review.setRating(reviewRating);
        review.setContent(reviewContent);
        review.setCreatedAt(reviewCreatedAt);
        return review;
    }

    @Data
    public static class Address {
        private String name;
        private String phone;
        private String region;
        private String detail;
    }

    @Data
    public static class Create {
        private Integer goodsId;
        private String deliveryMethod;
        private Address address;
        private String requestId;
    }

    @Data
    public static class Review {
        private Integer rating;
        private String content;
        private Date createdAt;
    }
}
