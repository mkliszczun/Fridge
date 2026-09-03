CREATE TABLE shopping_list_item (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    fridge_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    manual_amount NUMERIC(19, 3),
    unit VARCHAR(64),
    is_quantified BOOLEAN NOT NULL DEFAULT FALSE,
    is_checked BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT shopping_list_item_pkey PRIMARY KEY (id),
    CONSTRAINT shopping_list_item_manual_amount_check
        CHECK (manual_amount IS NULL OR manual_amount > 0),
    CONSTRAINT fk_shopping_list_item_fridge
        FOREIGN KEY (fridge_id) REFERENCES fridge (id) ON DELETE CASCADE
);

CREATE INDEX idx_shopping_list_item_fridge
    ON shopping_list_item (fridge_id);

CREATE TABLE shopping_list_item_source (
    id UUID NOT NULL,
    shopping_list_item_id UUID NOT NULL,
    planned_meal_ingredient_id UUID NOT NULL,
    contribution_amount NUMERIC(19, 3),
    CONSTRAINT shopping_list_item_source_pkey PRIMARY KEY (id),
    CONSTRAINT uk_shopping_list_source_ingredient
        UNIQUE (planned_meal_ingredient_id),
    CONSTRAINT shopping_list_source_amount_check
        CHECK (contribution_amount IS NULL OR contribution_amount > 0),
    CONSTRAINT fk_shopping_list_source_item
        FOREIGN KEY (shopping_list_item_id)
        REFERENCES shopping_list_item (id) ON DELETE CASCADE
);

CREATE INDEX idx_shopping_list_source_item
    ON shopping_list_item_source (shopping_list_item_id);
