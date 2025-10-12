package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.off.OffClient;
import io.github.mkliszczun.fridge.security.AppUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/api/off")
public class OffController {

    private final OffClient offClient;

    public OffController(OffClient offClient){
        this.offClient = offClient;
    }

    @GetMapping("/{ean}")
    public OffClient.OffResponse findByEan(@PathVariable String ean){
        OffClient.OffResponse response = offClient.getByEan(ean).block(Duration.ofSeconds(10));
        System.out.println(response.code());
        System.out.println(response.product());
        return response;
    }
}
