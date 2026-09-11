CREATE TABLE transfer (
    id BIGSERIAL PRIMARY KEY,
    from_wallet_id BIGINT NOT NULL,
    to_wallet_id BIGINT NOT NULL,
    amount_paise BIGINT NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_transfer_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_transfer_amount_positive CHECK (amount_paise > 0),
    CONSTRAINT ck_transfer_different_wallets CHECK (from_wallet_id <> to_wallet_id),

    CONSTRAINT fk_transfer_from_wallet FOREIGN KEY (from_wallet_id)
        REFERENCES wallet(id) ON DELETE RESTRICT ON UPDATE RESTRICT,

    CONSTRAINT fk_transfer_to_wallet FOREIGN KEY (to_wallet_id)
        REFERENCES wallet(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);

CREATE INDEX idx_transfer_from_wallet_id ON transfer(from_wallet_id);
CREATE INDEX idx_transfer_to_wallet_id ON transfer(to_wallet_id);
CREATE INDEX idx_transfer_idempotency_key ON transfer(idempotency_key);

