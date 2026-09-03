package io.github.nnkwrik.goodsservice.controller;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.ObjectMetadata;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.net.URL;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@RunWith(MockitoJUnitRunner.class)
public class UploadControllerTests {

    private static final String BUCKET = "test-1250000000";
    private static final String IMAGE_URL = "https://test-1250000000.cos.ap-guangzhou.myqcloud.com/images/test.jpg";

    @Mock
    private COSClient cosClient;

    private MockMvc mockMvc;

    @Before
    public void setUp() throws Exception {
        when(cosClient.getObjectUrl(eq(BUCKET), anyString())).thenReturn(new URL(IMAGE_URL));
        mockMvc = standaloneSetup(new UploadController(cosClient, BUCKET)).build();
    }

    @Test
    public void uploadsJpegAndPngWithoutAuthentication() throws Exception {
        mockMvc.perform(multipart("/upload/image")
                .file(new MockMultipartFile("file", "photo.bin", "application/octet-stream",
                        new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errno").value(0))
                .andExpect(jsonPath("$.data").value(IMAGE_URL));

        mockMvc.perform(multipart("/upload/image")
                .file(new MockMultipartFile("file", "photo.bin", "application/octet-stream",
                        new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errno").value(0));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ObjectMetadata> metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);
        verify(cosClient, times(2)).putObject(eq(BUCKET), keyCaptor.capture(),
                any(InputStream.class), metadataCaptor.capture());

        List<String> keys = keyCaptor.getAllValues();
        List<ObjectMetadata> metadata = metadataCaptor.getAllValues();
        assertTrue(keys.get(0).startsWith("images/"));
        assertTrue(keys.get(0).endsWith(".jpg"));
        assertEquals("image/jpeg", metadata.get(0).getContentType());
        assertTrue(keys.get(1).endsWith(".png"));
        assertEquals("image/png", metadata.get(1).getContentType());
    }

    @Test
    public void rejectsMissingEmptyInvalidAndOversizedFiles() throws Exception {
        mockMvc.perform(multipart("/upload/image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errno").value(4006));
        mockMvc.perform(multipart("/upload/image")
                .file(new MockMultipartFile("file", new byte[0])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errno").value(4006));
        mockMvc.perform(multipart("/upload/image")
                .file(new MockMultipartFile("file", "file.txt", "text/plain", new byte[]{1, 2, 3})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errno").value(4006));

        byte[] oversized = new byte[(int) UploadController.MAX_IMAGE_SIZE + 1];
        oversized[0] = (byte) 0xff;
        oversized[1] = (byte) 0xd8;
        oversized[2] = (byte) 0xff;
        mockMvc.perform(multipart("/upload/image")
                .file(new MockMultipartFile("file", "large.jpg", "image/jpeg", oversized)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errno").value(4006));

        verifyZeroInteractions(cosClient);
    }

    @Test
    public void reportsCosUploadFailure() throws Exception {
        doThrow(new CosClientException("failed")).when(cosClient)
                .putObject(eq(BUCKET), anyString(), any(InputStream.class), any(ObjectMetadata.class));

        mockMvc.perform(multipart("/upload/image")
                .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg",
                        new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errno").value(4007));

        verify(cosClient, never()).getObjectUrl(eq(BUCKET), anyString());
    }
}
