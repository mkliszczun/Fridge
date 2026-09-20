ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;
-- Every existing account must confirm its address. Old JWTs and refresh tokens
-- must remain invalid even after that confirmation.
UPDATE users SET token_version = token_version + 1;
DELETE FROM refresh_token;

CREATE TABLE email_verification (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE CASCADE,
    token_version BIGINT NOT NULL,
    pending_username VARCHAR(64),
    pending_password_hash VARCHAR(255),
    email VARCHAR(254),
    code_hash VARCHAR(60),
    code_expires_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT email_verification_owner CHECK (
        (user_id IS NOT NULL AND pending_username IS NULL AND pending_password_hash IS NULL)
        OR (user_id IS NULL AND pending_username IS NOT NULL AND pending_password_hash IS NOT NULL)
    )
);
CREATE INDEX idx_email_verification_expiry ON email_verification(expires_at);
CREATE INDEX idx_email_verification_user ON email_verification(user_id);
INSERT INTO application_guard_lock(id) VALUES ('email');
