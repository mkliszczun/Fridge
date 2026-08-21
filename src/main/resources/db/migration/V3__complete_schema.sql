CREATE TABLE IF NOT EXISTS users (
    id UUID NOT NULL,
    account_non_expired BOOLEAN NOT NULL,
    account_non_locked BOOLEAN NOT NULL,
    credentials_non_expired BOOLEAN NOT NULL,
    email VARCHAR(254) NOT NULL,
    enabled BOOLEAN NOT NULL,
    password VARCHAR(255) NOT NULL,
    username VARCHAR(64) NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT uk6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email),
    CONSTRAINT ukr43af9ap4edm43mmtq01oddj6 UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID NOT NULL,
    role VARCHAR(32) NOT NULL,
    CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role),
    CONSTRAINT user_roles_role_check CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT fkhfh9dx7w3ubf1co1vdev94g3f
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS fridge (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT fridge_pkey PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS product (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    brand VARCHAR(255),
    carbs100 NUMERIC(38, 2),
    default_unit VARCHAR(255) NOT NULL,
    ean VARCHAR(32),
    fat100 NUMERIC(38, 2),
    kcal100 NUMERIC(38, 2),
    name VARCHAR(255) NOT NULL,
    product_type VARCHAR(255) NOT NULL,
    protein100 NUMERIC(38, 2),
    shelf_life_after_opening_days INTEGER,
    CONSTRAINT product_pkey PRIMARY KEY (id),
    CONSTRAINT idx_product_ean UNIQUE (ean),
    CONSTRAINT product_default_unit_check
        CHECK (default_unit IN ('GRAM', 'MILLILITER', 'PIECE')),
    CONSTRAINT product_product_type_check
        CHECK (product_type IN (
            'DAIRY', 'MEAT', 'FISH', 'VEGETABLE', 'FRUIT',
            'BAKERY', 'DRY', 'BEVERAGE', 'OTHER'
        ))
);

CREATE TABLE IF NOT EXISTS fridge_member (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    is_default BOOLEAN NOT NULL,
    role_in_fridge VARCHAR(255) NOT NULL,
    user_id UUID NOT NULL,
    fridge_id UUID NOT NULL,
    CONSTRAINT fridge_member_pkey PRIMARY KEY (id),
    CONSTRAINT uk_fridge_user UNIQUE (fridge_id, user_id),
    CONSTRAINT fridge_member_role_in_fridge_check
        CHECK (role_in_fridge IN ('OWNER', 'MEMBER')),
    CONSTRAINT fkr3jkkvqrga7pmdxqq1r93t95w
        FOREIGN KEY (fridge_id) REFERENCES fridge (id)
);

CREATE TABLE IF NOT EXISTS fridge_item (
    id UUID NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    amount NUMERIC(38, 2) NOT NULL,
    archived_at TIMESTAMP(6) WITH TIME ZONE,
    best_before_date DATE,
    custom_name VARCHAR(255),
    effective_expire_at DATE,
    open_date DATE,
    owner_user_id UUID,
    state VARCHAR(255) NOT NULL,
    unit VARCHAR(255) NOT NULL,
    fridge_id UUID NOT NULL,
    product_id UUID,
    CONSTRAINT fridge_item_pkey PRIMARY KEY (id),
    CONSTRAINT fridge_item_state_check
        CHECK (state IN ('SEALED', 'OPEN', 'CONSUMED', 'DISCARDED')),
    CONSTRAINT fridge_item_unit_check
        CHECK (unit IN ('GRAM', 'MILLILITER', 'PIECE')),
    CONSTRAINT fkrknsep7dvsx604gjja516s83m
        FOREIGN KEY (fridge_id) REFERENCES fridge (id),
    CONSTRAINT fk77yvxcpx5rpdmwo1odbv4waf7
        FOREIGN KEY (product_id) REFERENCES product (id)
);

CREATE INDEX IF NOT EXISTS idx_fridge_member_fridge
    ON fridge_member (fridge_id);

CREATE INDEX IF NOT EXISTS idx_fridge_member_user
    ON fridge_member (user_id);

CREATE INDEX IF NOT EXISTS idx_item_fridge
    ON fridge_item (fridge_id);

CREATE INDEX IF NOT EXISTS idx_item_fridge_state
    ON fridge_item (fridge_id, state);

CREATE INDEX IF NOT EXISTS idx_item_expire
    ON fridge_item (fridge_id, effective_expire_at);
