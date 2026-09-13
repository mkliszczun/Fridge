ALTER TABLE users
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN premium_until TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN password_reset_hash VARCHAR(255),
    ADD COLUMN password_reset_expires_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN password_reset_requested_at TIMESTAMP(6) WITH TIME ZONE;

CREATE UNIQUE INDEX idx_users_password_reset_hash ON users(password_reset_hash);

CREATE TABLE refresh_token (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_version BIGINT NOT NULL,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_refresh_token_user ON refresh_token(user_id);
CREATE INDEX idx_refresh_token_expiry ON refresh_token(expires_at);

CREATE TABLE ai_daily_usage (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    usage_date DATE NOT NULL,
    charged_micros BIGINT NOT NULL DEFAULT 0 CHECK (charged_micros >= 0),
    input_tokens BIGINT NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),
    cached_input_tokens BIGINT NOT NULL DEFAULT 0 CHECK (cached_input_tokens >= 0),
    cache_write_tokens BIGINT NOT NULL DEFAULT 0 CHECK (cache_write_tokens >= 0),
    output_tokens BIGINT NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),
    unsettled_requests BIGINT NOT NULL DEFAULT 0 CHECK (unsettled_requests >= 0),
    CONSTRAINT uk_ai_usage_user_date UNIQUE(user_id, usage_date)
);

-- Prevent in-flight requests from recreating orphan ownership after account deletion.
ALTER TABLE fridge_member ADD CONSTRAINT fk_fridge_member_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE fridge_item ADD CONSTRAINT fk_fridge_item_owner
    FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE SET NULL;
