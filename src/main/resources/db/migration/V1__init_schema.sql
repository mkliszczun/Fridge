CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS default_expiration_days (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_type VARCHAR(50) NOT NULL UNIQUE,
    default_expiration_days INTEGER,
    expiration_days_after_opening INTEGER
);
