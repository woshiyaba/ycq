package io.github.nnkwrik.goodsservice.service;

public class ContentException extends RuntimeException {
    private final int errno;

    public ContentException(int errno, String message) {
        super(message);
        this.errno = errno;
    }

    public int getErrno() {
        return errno;
    }
}
