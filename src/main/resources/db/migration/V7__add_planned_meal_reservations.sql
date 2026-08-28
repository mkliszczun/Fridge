CREATE TABLE planned_meal_reservation (
    id UUID NOT NULL,
    planned_meal_ingredient_id UUID NOT NULL,
    fridge_item_id UUID NOT NULL,
    amount NUMERIC(38, 2) NOT NULL,
    CONSTRAINT planned_meal_reservation_pkey PRIMARY KEY (id),
    CONSTRAINT planned_meal_reservation_amount_check CHECK (amount > 0),
    CONSTRAINT uk_planned_meal_reservation_ingredient_item
        UNIQUE (planned_meal_ingredient_id, fridge_item_id),
    CONSTRAINT fk_planned_meal_reservation_ingredient
        FOREIGN KEY (planned_meal_ingredient_id)
        REFERENCES planned_meal_ingredient (id) ON DELETE CASCADE,
    CONSTRAINT fk_planned_meal_reservation_item
        FOREIGN KEY (fridge_item_id)
        REFERENCES fridge_item (id) ON DELETE CASCADE
);

CREATE INDEX idx_planned_meal_reservation_ingredient
    ON planned_meal_reservation (planned_meal_ingredient_id);

CREATE INDEX idx_planned_meal_reservation_item
    ON planned_meal_reservation (fridge_item_id);
