package io.github.nnkwrik.goodsservice.controller;

import io.github.nnkwrik.common.dto.Response;
import io.github.nnkwrik.goodsservice.service.ContentException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ContentExceptionHandler {
    @ExceptionHandler(ContentException.class)
    public Response handle(ContentException error) {
        return Response.fail(error.getErrno(), error.getMessage());
    }
}
