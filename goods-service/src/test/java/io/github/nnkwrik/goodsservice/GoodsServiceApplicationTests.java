package io.github.nnkwrik.goodsservice;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
        "tencent.cos.secret-id=test-secret-id",
        "tencent.cos.secret-key=test-secret-key",
        "tencent.cos.bucket=test-1250000000",
        "tencent.cos.region=ap-guangzhou"
})
public class GoodsServiceApplicationTests {

    @Test
    public void contextLoads() {
    }

}
