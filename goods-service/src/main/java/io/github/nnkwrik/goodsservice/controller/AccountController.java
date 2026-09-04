package io.github.nnkwrik.goodsservice.controller;

import io.github.nnkwrik.common.dto.JWTUser;
import io.github.nnkwrik.common.dto.Response;
import io.github.nnkwrik.common.dto.UserProfile;
import io.github.nnkwrik.common.token.injection.JWT;
import io.github.nnkwrik.goodsservice.service.AccountService;
import io.github.nnkwrik.goodsservice.service.AccountService.Address;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/goodsUser")
public class AccountController {
    @Autowired private AccountService accounts;

    @GetMapping("/profile")
    public Response profile(@JWT(required=true) JWTUser user) { return Response.ok(accounts.profile(user.getOpenId(), user.getOpenId())); }

    @GetMapping("/profile/{id}")
    public Response profile(@PathVariable String id, @JWT JWTUser user) { return Response.ok(accounts.profile(id, user == null ? null : user.getOpenId())); }

    @PutMapping("/profile")
    public Response profile(@JWT(required=true) JWTUser user, @RequestHeader("Authorization") String token, @RequestBody UserProfile profile) {
        return Response.ok(accounts.updateProfile(user.getOpenId(),token,profile));
    }

    @PutMapping("/follow/{id}")
    public Response follow(@JWT(required=true) JWTUser user,@PathVariable String id) { accounts.follow(user.getOpenId(),id,true); return Response.ok(); }
    @DeleteMapping("/follow/{id}")
    public Response unfollow(@JWT(required=true) JWTUser user,@PathVariable String id) { accounts.follow(user.getOpenId(),id,false); return Response.ok(); }
    @GetMapping("/following")
    public Response following(@JWT(required=true) JWTUser user,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return Response.ok(accounts.following(user.getOpenId(),page,size));
    }

    @GetMapping("/addresses")
    public Response addresses(@JWT(required=true) JWTUser user) { return Response.ok(accounts.addresses(user.getOpenId())); }
    @PostMapping("/addresses")
    public Response addAddress(@JWT(required=true) JWTUser user,@RequestBody Address address) { return Response.ok(accounts.saveAddress(user.getOpenId(),null,address)); }
    @PutMapping("/addresses/{id}")
    public Response updateAddress(@JWT(required=true) JWTUser user,@PathVariable int id,@RequestBody Address address) { return Response.ok(accounts.saveAddress(user.getOpenId(),id,address)); }
    @DeleteMapping("/addresses/{id}")
    public Response deleteAddress(@JWT(required=true) JWTUser user,@PathVariable int id) { accounts.deleteAddress(user.getOpenId(),id); return Response.ok(); }

    @GetMapping("/history")
    public Response history(@JWT(required=true) JWTUser user,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) { return Response.ok(accounts.history(user.getOpenId(),page,size)); }
    @PostMapping("/history")
    public Response visit(@JWT(required=true) JWTUser user,@RequestBody Map<String,Object> body) {
        String kind = String.valueOf(body.getOrDefault("kind","GOODS"));
        Object id = body.containsKey("targetId") ? body.get("targetId") : body.get("goodsId");
        if (!(id instanceof Number)) throw new IllegalArgumentException("请选择浏览内容");
        accounts.recordHistory(user.getOpenId(),kind,((Number)id).intValue()); return Response.ok();
    }
    @PostMapping("/history/clear")
    public Response clearHistory(@JWT(required=true) JWTUser user) { accounts.clearHistory(user.getOpenId()); return Response.ok(); }

    @GetMapping("/goods")
    public Response goods(@JWT(required=true) JWTUser user,@RequestParam(defaultValue="ALL") String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return Response.ok(accounts.goods(user.getOpenId(),status,page,size));
    }
}
