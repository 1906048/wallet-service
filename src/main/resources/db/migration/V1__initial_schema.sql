CREATE TABLE wallet (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    balance_paise BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_wallet_user UNIQUE (user_id),
    CONSTRAINT ck_wallet_balance_non_negative CHECK (balance_paise >= 0)
);

CREATE INDEX idx_wallet_user_id ON wallet(user_id);

