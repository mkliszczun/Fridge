CREATE TABLE application_guard_lock (id VARCHAR(255) PRIMARY KEY);
INSERT INTO application_guard_lock (id) VALUES ('auth'), ('ai');

CREATE TABLE auth_rate_bucket (
    id VARCHAR(255) PRIMARY KEY,
    attempts BIGINT NOT NULL CHECK (attempts >= 0),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_auth_bucket_expiry ON auth_rate_bucket(expires_at);

-- Deliberately independent of users: deleting accounts does not restore the global budget.
CREATE TABLE ai_global_daily_usage (
    usage_date DATE PRIMARY KEY,
    charged_micros BIGINT NOT NULL CHECK (charged_micros >= 0),
    warning_sent BOOLEAN NOT NULL DEFAULT FALSE
);
INSERT INTO ai_global_daily_usage (usage_date, charged_micros)
SELECT usage_date, SUM(charged_micros) FROM ai_daily_usage GROUP BY usage_date;
