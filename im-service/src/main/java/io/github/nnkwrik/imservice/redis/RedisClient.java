package io.github.nnkwrik.imservice.redis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Collections;

/**
 * redis client
 * 存储未读消息。key=ChatId，Value=List<WsMessage>
 *
 * @author nnkwrik
 * @date 18/12/06 15:08
 */
@Component
public class RedisClient {

    // ponytail: callers share this bean's monitor for queue read/modify/write in one IM instance;
    // use Redis atomic queues and durable message IDs before running multiple IM instances.

    @Autowired
    private RedisTemplate redisTemplate;


    public <T> T get(String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    public <T> List<T> multiGet(List<String> keys) {
        if (keys == null || keys.isEmpty()) return Collections.emptyList();
        List<T> values = redisTemplate.opsForValue().multiGet(keys);
        return values == null ? Collections.emptyList() : values;
    }

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void del(String key) {
        redisTemplate.delete(key);
    }


}
