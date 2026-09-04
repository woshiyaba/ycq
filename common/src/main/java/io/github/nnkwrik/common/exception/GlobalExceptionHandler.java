package io.github.nnkwrik.common.exception;

import io.github.nnkwrik.common.dto.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author nnkwrik
 * @date 18/11/24 12:54
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ResponseBody
    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleInvalidArgument(IllegalArgumentException e) {
        return Response.fail(Response.POST_INFO_INCOMPLETE, e.getMessage());
    }

    @ResponseBody
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MethodArgumentNotValidException.class})
    public Object handleInvalidRequest() {
        return Response.fail(Response.POST_INFO_INCOMPLETE, "请求参数不正确，请检查后重试");
    }

    @ResponseBody
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object handleMaxUploadSizeExceededException() {
        return Response.fail(Response.UPLOAD_FILE_INVALID, "上传文件大小超过限制");
    }

    @ResponseBody
    @ExceptionHandler(GlobalException.class)
    public Object handleJWTException(GlobalException e) {
        log.info("发生异常，errno = {},errmsg = {}", e.getErrno(), e.getErrmsg());
        return Response.fail(e.getErrno(), e.getErrmsg());
    }

    @ResponseBody
    @ExceptionHandler(JWTException.class)
    public Object handleJWTException(JWTException e) {
        log.info("发生JWTException，errno = {},errmsg = {}", e.getErrno(), e.getErrmsg());

        return Response.fail(e.getErrno(), e.getErrmsg());
    }
}
