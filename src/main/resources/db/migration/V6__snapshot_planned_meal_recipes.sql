ALTER TABLE planned_meal
    ADD COLUMN recipe_name VARCHAR(255),
    ADD COLUMN recipe_description TEXT,
    ADD COLUMN recipe_instructions TEXT,
    ADD COLUMN recipe_servings INTEGER;

UPDATE planned_meal planned
SET recipe_name = recipe.name,
    recipe_description = recipe.description,
    recipe_instructions = recipe.instructions,
    recipe_servings = recipe.servings
FROM recipe
WHERE planned.recipe_id = recipe.id;

ALTER TABLE planned_meal
    ALTER COLUMN recipe_name SET NOT NULL,
    ALTER COLUMN recipe_instructions SET NOT NULL,
    ALTER COLUMN recipe_servings SET NOT NULL,
    ADD CONSTRAINT planned_meal_recipe_servings_check CHECK (recipe_servings > 0);

CREATE TABLE planned_meal_ingredient (
    id UUID NOT NULL,
    planned_meal_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 3),
    unit VARCHAR(64),
    is_optional BOOLEAN NOT NULL DEFAULT FALSE,
    note TEXT,
    display_order INTEGER NOT NULL,
    CONSTRAINT planned_meal_ingredient_pkey PRIMARY KEY (id),
    CONSTRAINT planned_meal_ingredient_amount_check CHECK (amount IS NULL OR amount > 0),
    CONSTRAINT fk_planned_meal_ingredient_meal
        FOREIGN KEY (planned_meal_id) REFERENCES planned_meal (id) ON DELETE CASCADE
);

INSERT INTO planned_meal_ingredient (
    id,
    planned_meal_id,
    name,
    amount,
    unit,
    is_optional,
    note,
    display_order
)
SELECT gen_random_uuid(),
       planned.id,
       ingredient.name,
       ingredient.amount,
       ingredient.unit,
       ingredient.is_optional,
       ingredient.note,
       ingredient.display_order
FROM planned_meal planned
JOIN recipe_ingredient ingredient ON ingredient.recipe_id = planned.recipe_id;

ALTER TABLE planned_meal DROP CONSTRAINT fk_planned_meal_recipe;
ALTER TABLE planned_meal RENAME COLUMN recipe_id TO source_recipe_id;
ALTER TABLE planned_meal ALTER COLUMN source_recipe_id DROP NOT NULL;
ALTER TABLE planned_meal
    ADD CONSTRAINT fk_planned_meal_source_recipe
        FOREIGN KEY (source_recipe_id) REFERENCES recipe (id) ON DELETE SET NULL;

ALTER INDEX idx_planned_meal_recipe RENAME TO idx_planned_meal_source_recipe;

CREATE INDEX idx_planned_meal_ingredient_meal
    ON planned_meal_ingredient (planned_meal_id);
