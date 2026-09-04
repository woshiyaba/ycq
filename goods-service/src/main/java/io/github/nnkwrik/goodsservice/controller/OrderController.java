package io.github.nnkwrik.goodsservice.controller;

import io.github.nnkwrik.common.dto.JWTUser;
import io.github.nnkwrik.common.dto.Response;
import io.github.nnkwrik.common.token.injection.JWT;
import io.github.nnkwrik.goodsservice.model.po.Order;
import io.github.nnkwrik.goodsservice.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/goods/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public Response create(@JWT(required = true) JWTUser user, @RequestBody Order.Create request) {
        return Response.ok(service.create(user.getOpenId(), request));
    }

    @GetMapping
    public Response list(@JWT(required = true) JWTUser user,
                         @RequestParam(defaultValue = "buyer") String role,
                         @RequestParam(required = false) String status,
                         @RequestParam(defaultValue = "1") int page,
                         @RequestParam(defaultValue = "10") int size) {
        return Response.ok(service.list(user.getOpenId(), role, status, page, size));
    }

    @GetMapping("/{id}")
    public Response detail(@JWT(required = true) JWTUser user, @PathVariable long id) {
        return Response.ok(service.detail(id, user.getOpenId()));
    }

    @PostMapping("/{id}/pay")
    public Response pay(@JWT(required = true) JWTUser user, @PathVariable long id) {
        return Response.ok(service.pay(id, user.getOpenId()));
    }

    @PostMapping("/{id}/cancel")
    public Response cancel(@JWT(required = true) JWTUser user, @PathVariable long id) {
        return Response.ok(service.cancel(id, user.getOpenId()));
    }

    @PostMapping("/{id}/ship")
    public Response ship(@JWT(required = true) JWTUser user, @PathVariable long id,
                         @RequestBody(required = false) Map<String, String> body) {
        return Response.ok(service.ship(id, user.getOpenId(), body == null ? null : body.get("trackingNo")));
    }

    @PostMapping("/{id}/receive")
    public Response receive(@JWT(required = true) JWTUser user, @PathVariable long id) {
        return Response.ok(service.receive(id, user.getOpenId()));
    }

    @PostMapping("/{id}/review")
    public Response review(@JWT(required = true) JWTUser user, @PathVariable long id, @RequestBody Order.Review review) {
        return Response.ok(service.review(id, user.getOpenId(), review));
    }
}
