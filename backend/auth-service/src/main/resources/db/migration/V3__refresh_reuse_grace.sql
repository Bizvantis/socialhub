-- Refresh-token reuse grace window: remember WHEN a token was rotated, so a retry within a few
-- seconds (page unload, two tabs) is not mistaken for token theft.
ALTER TABLE refresh_tokens ADD COLUMN replaced_at DATETIME(3) NULL AFTER revoked;
