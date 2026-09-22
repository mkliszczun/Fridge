ALTER TABLE product
    ADD COLUMN default_expiration_days INTEGER;

ALTER TABLE product
    ADD CONSTRAINT product_default_expiration_days_check
        CHECK (default_expiration_days IS NULL OR default_expiration_days BETWEEN 0 AND 3650);
