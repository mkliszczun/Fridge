CREATE TABLE recipe (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    owner_user_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    instructions TEXT NOT NULL,
    servings INTEGER NOT NULL,
    CONSTRAINT recipe_pkey PRIMARY KEY (id),
    CONSTRAINT recipe_servings_check CHECK (servings > 0),
    CONSTRAINT fk_recipe_owner
        FOREIGN KEY (owner_user_id) REFERENCES users (id)
);

CREATE INDEX idx_recipe_owner ON recipe (owner_user_id);

CREATE TABLE recipe_ingredient (
    id UUID NOT NULL,
    recipe_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    amount NUMERIC(19, 3),
    unit VARCHAR(64),
    is_optional BOOLEAN NOT NULL DEFAULT FALSE,
    note TEXT,
    display_order INTEGER NOT NULL,
    CONSTRAINT recipe_ingredient_pkey PRIMARY KEY (id),
    CONSTRAINT recipe_ingredient_amount_check CHECK (amount IS NULL OR amount > 0),
    CONSTRAINT fk_recipe_ingredient_recipe
        FOREIGN KEY (recipe_id) REFERENCES recipe (id) ON DELETE CASCADE
);

CREATE INDEX idx_recipe_ingredient_recipe ON recipe_ingredient (recipe_id);
