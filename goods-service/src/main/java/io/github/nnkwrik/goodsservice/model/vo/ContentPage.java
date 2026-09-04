package io.github.nnkwrik.goodsservice.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class ContentPage<T> {
    private final List<T> items;
    private final long total;
    private final int page;
    private final int size;
    private final boolean hasMore;

    public ContentPage(List<T> items, long total, int page, int size) {
        this.items = items;
        this.total = total;
        this.page = page;
        this.size = size;
        this.hasMore = (long) page * size < total;
    }
}
