package fangxianyu.innerApi.user;

import io.github.nnkwrik.common.dto.Response;
import io.github.nnkwrik.common.dto.SimpleUser;
import io.github.nnkwrik.common.dto.UserProfile;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * user-service的内部api
 *
 * @author nnkwrik
 * @date 18/11/23 18:06
 */
@FeignClient(name = "user-service")
@RequestMapping("/user-service")
public interface UserClient {

    @GetMapping("/profile/{id}")
    Response<UserProfile> getProfile(@PathVariable("id") String id);

    @PutMapping("/profile")
    Response<UserProfile> updateProfile(@RequestHeader("Authorization") String token, @RequestBody UserProfile profile);

    /**
     * 获取用户openId的相关信息
     *
     * @param openId
     * @return
     */
    @GetMapping("/simpleUser/{openId}")
    Response<SimpleUser> getSimpleUser(@PathVariable("openId") String openId);

    /**
     * 获取用户openIdList的相关信息
     *
     * @param openIdList
     * @return
     */
    @GetMapping("/simpleUserList")
    Response<Map<String, SimpleUser>> getSimpleUserList(@RequestParam List<String> openIdList);
}
