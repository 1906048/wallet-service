package com.example.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TransferRequest(
    @JsonProperty("from")
    Long from,

    @JsonProperty("to")
    Long to,

    @JsonProperty("amount_paise")
    long amountPaise,

    @JsonProperty("idempotency_key")
    String idempotencyKey
) {
}

