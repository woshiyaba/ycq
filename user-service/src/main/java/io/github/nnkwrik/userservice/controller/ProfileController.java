package io.github.nnkwrik.userservice.controller;

import io.github.nnkwrik.common.dto.JWTUser;
import io.github.nnkwrik.common.dto.Response;
import io.github.nnkwrik.common.dto.UserProfile;
import io.github.nnkwrik.common.token.injection.JWT;
import io.github.nnkwrik.userservice.dao.ProfileMapper;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;

@RestController
@RequestMapping("/user-service/profile")
public class ProfileController {
    private final ProfileMapper profiles;

    public ProfileController(ProfileMapper profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/{id}")
    public Response<UserProfile> get(@PathVariable String id) {
        UserProfile profile = profiles.get(id);
        if (profile == null) return Response.fail(Response.USER_IS_NOT_EXIST, "用户不存在");
        return Response.ok(profile);
    }

    @PutMapping
    public Response<UserProfile> update(@JWT(required = true) JWTUser user, @RequestBody UserProfile profile) {
        validate(profile);
        if (profiles.get(user.getOpenId()) == null) throw new IllegalArgumentException("用户信息尚未同步，请稍后重试");
        profile.setOpenId(user.getOpenId());
        profiles.save(profile);
        return get(user.getOpenId());
    }

    static void validate(UserProfile p) {
        if (p == null) throw new IllegalArgumentException("请填写个人资料");
        if (p.getNickName() == null || p.getNickName().trim().isEmpty() || p.getNickName().trim().length() > 40 ||
                p.getNickName().chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("昵称需为1至40字");
        p.setNickName(p.getNickName().trim());
        p.setAvatarUrl(p.getAvatarUrl() == null ? "" : p.getAvatarUrl().trim());
        if (p.getAvatarUrl().length() > 2048) throw new IllegalArgumentException("头像地址过长，请重新上传");
        if (!p.getAvatarUrl().isEmpty()) {
            try {
                URI uri = new URI(p.getAvatarUrl());
                if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) ||
                        uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException("请选择有效的HTTP或HTTPS头像");
            } catch (URISyntaxException error) {
                throw new IllegalArgumentException("头像地址格式错误，请重新上传");
            }
        }
        p.setBio(p.getBio() == null ? "" : p.getBio().trim());
        p.setRegion(p.getRegion() == null ? "" : p.getRegion().trim());
        if (p.getGender() == null) p.setGender(0);
        if (p.getBio().length() > 300 || p.getBio().indexOf('\0') >= 0) throw new IllegalArgumentException("个人简介最多300字");
        if (p.getRegion().length() > 100 || p.getRegion().indexOf('\0') >= 0) throw new IllegalArgumentException("所在地区最多100字");
        if (p.getGender() < 0 || p.getGender() > 2) throw new IllegalArgumentException("请选择有效性别");
    }
}
