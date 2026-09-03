package io.github.nnkwrik.goodsservice.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CosConfiguration {

    @Bean(destroyMethod = "shutdown")
    public COSClient cosClient(@Value("${tencent.cos.secret-id}") String secretId,
                               @Value("${tencent.cos.secret-key}") String secretKey,
                               @Value("${tencent.cos.region}") String region) {
        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        return new COSClient(credentials, clientConfig);
    }
}
