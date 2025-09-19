package io.github.mkliszczun.fridge.off;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.List;

@Service
public class OffClient {
    private final WebClient webClient;

    public OffClient(WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("https://world.openfoodfacts.org/api/v2")
                .defaultHeader(HttpHeaders.USER_AGENT, "FridgeApp/0.2 (kontakt@twojadomena.pl)")
                .build();
    }

    public Mono<OffResponse> getByEan(String ean) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/product/{ean}.json")
                        .queryParam("fields", "code,product_name,brands,nutriments,categories_tags")
                        .build(ean))
                .retrieve()
                .bodyToMono(OffResponse.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static record OffResponse(int status, String code, OffProduct product) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static record OffProduct(
            @JsonProperty("product_name") String productName,
            String brands,
            OffNutriments nutriments,
            @JsonProperty("categories_tags") List<String> categoriesTags
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static record OffNutriments(
            @JsonProperty("energy-kcal_100g") Double energyKcal100g,
            @JsonProperty("proteins_100g") Double proteins100g,
            @JsonProperty("carbohydrates_100g") Double carbohydrates100g,
            @JsonProperty("fat_100g") Double fat100g
    ) {}
}
