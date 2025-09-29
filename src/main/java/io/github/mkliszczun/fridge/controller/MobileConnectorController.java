package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.dto.EanIntegationRequest;
import io.github.mkliszczun.fridge.dto.EanIntegrationResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/integration/connect")
public class MobileConnectorController {

    @PostMapping
    public EanIntegrationResponse scanProduct(@RequestBody EanIntegationRequest req){
        if (req.ean() != null) {
            return new EanIntegrationResponse(req.ean() + "- sent code");
        }
            return new EanIntegrationResponse("Server responded, body not found");

    }

    @GetMapping
    public String autoResponse(){
        return "get wywolane";
    }
}
