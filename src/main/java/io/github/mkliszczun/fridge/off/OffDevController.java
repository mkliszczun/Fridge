package io.github.mkliszczun.fridge.off;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/dev/off")
@RequiredArgsConstructor
public class OffDevController {
    private final OffClient client;

    @GetMapping("/{ean}")
    public Mono<OffClient.OffResponse> byEan(@PathVariable String ean) {
        return client.getByEan(ean);
    }
}
