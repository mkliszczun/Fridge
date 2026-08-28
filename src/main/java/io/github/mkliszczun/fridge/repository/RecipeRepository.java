package io.github.mkliszczun.fridge.repository;

import io.github.mkliszczun.fridge.recipe.Recipe;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecipeRepository extends JpaRepository<Recipe, UUID> {

    @EntityGraph(attributePaths = "ingredients")
    Optional<Recipe> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    @EntityGraph(attributePaths = "ingredients")
    List<Recipe> findAllByOwnerUserIdOrderByNameAsc(UUID ownerUserId);
}
