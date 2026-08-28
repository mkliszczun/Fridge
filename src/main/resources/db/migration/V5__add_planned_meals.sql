CREATE TABLE planned_meal (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    fridge_id UUID NOT NULL,
    recipe_id UUID NOT NULL,
    planned_date DATE NOT NULL,
    servings INTEGER NOT NULL,
    created_by_user_id UUID NOT NULL,
    CONSTRAINT planned_meal_pkey PRIMARY KEY (id),
    CONSTRAINT planned_meal_servings_check CHECK (servings > 0),
    CONSTRAINT fk_planned_meal_fridge
        FOREIGN KEY (fridge_id) REFERENCES fridge (id) ON DELETE CASCADE,
    CONSTRAINT fk_planned_meal_recipe
        FOREIGN KEY (recipe_id) REFERENCES recipe (id),
    CONSTRAINT fk_planned_meal_creator
        FOREIGN KEY (created_by_user_id) REFERENCES users (id)
);

CREATE INDEX idx_planned_meal_fridge_date ON planned_meal (fridge_id, planned_date);
CREATE INDEX idx_planned_meal_recipe ON planned_meal (recipe_id);
