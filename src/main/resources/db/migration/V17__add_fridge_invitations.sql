CREATE TABLE fridge_invitation (
    id UUID PRIMARY KEY,
    fridge_id UUID NOT NULL REFERENCES fridge(id) ON DELETE CASCADE,
    invited_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    invited_by_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    expires_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_fridge_invitation_recipient UNIQUE (fridge_id, invited_user_id),
    CONSTRAINT fridge_invitation_not_self CHECK (invited_user_id <> invited_by_user_id)
);

CREATE INDEX idx_fridge_invitation_recipient_expiry ON fridge_invitation(invited_user_id, expires_at);
CREATE INDEX idx_fridge_invitation_sender ON fridge_invitation(invited_by_user_id);
