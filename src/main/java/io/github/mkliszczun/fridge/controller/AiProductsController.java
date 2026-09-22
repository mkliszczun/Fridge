package io.github.mkliszczun.fridge.controller;

import io.github.mkliszczun.fridge.dto.AiProductGenerateRequest;
import io.github.mkliszczun.fridge.dto.AiProductProposalResponse;
import io.github.mkliszczun.fridge.dto.ErrorResponse;
import io.github.mkliszczun.fridge.service.AiProductService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestController
@RequestMapping("/api/ai/products")
public class AiProductsController {
    private final AiProductService service;

    public AiProductsController(AiProductService service) { this.service = service; }

    @PostMapping("/generate")
    public AiProductProposalResponse generate(@Valid @RequestBody AiProductGenerateRequest request) {
        return service.generate(request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> invalidRequest() {
        return ResponseEntity.badRequest().body(ErrorResponse.of("Invalid product proposal request"));
    }
}
