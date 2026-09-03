package io.github.nnkwrik.goodsservice.controller;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.ObjectMetadata;
import io.github.nnkwrik.common.dto.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {

    static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private final COSClient cosClient;
    private final String bucketName;

    @Autowired
    public UploadController(COSClient cosClient,
                            @Value("${tencent.cos.bucket}") String bucketName) {
        this.cosClient = cosClient;
        this.bucketName = bucketName;
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response uploadImage(@RequestParam(value = "file", required = false) MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_SIZE) {
            return invalidImage();
        }

        try {
            byte[] content = file.getBytes();
            String extension = detectExtension(content);
            if (extension == null) {
                return invalidImage();
            }

            String contentType = "jpg".equals(extension) ? MediaType.IMAGE_JPEG_VALUE : MediaType.IMAGE_PNG_VALUE;
            String key = "images/" + UUID.randomUUID() + "." + extension;
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(content.length);
            metadata.setContentType(contentType);

            cosClient.putObject(bucketName, key, new ByteArrayInputStream(content), metadata);
            return Response.ok(cosClient.getObjectUrl(bucketName, key).toString());
        } catch (CosServiceException e) {
            log.error("腾讯云 COS 上传失败，requestId={}", e.getRequestId(), e);
        } catch (IOException | CosClientException e) {
            log.error("腾讯云 COS 上传失败", e);
        }
        return Response.fail(Response.UPLOAD_FAILED, "图片上传失败");
    }

    private static Response invalidImage() {
        return Response.fail(Response.UPLOAD_FILE_INVALID, "仅支持不超过5MB的 JPEG/PNG 图片");
    }

    private static String detectExtension(byte[] content) {
        if (content.length >= 3 && content[0] == (byte) 0xff &&
                content[1] == (byte) 0xd8 && content[2] == (byte) 0xff) {
            return "jpg";
        }
        if (content.length >= PNG_SIGNATURE.length &&
                Arrays.equals(PNG_SIGNATURE, Arrays.copyOf(content, PNG_SIGNATURE.length))) {
            return "png";
        }
        return null;
    }
}
