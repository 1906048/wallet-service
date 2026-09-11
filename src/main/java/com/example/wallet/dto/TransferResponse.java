package com.example.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TransferResponse(
    @JsonProperty("transferId")
    Long transferId,

    @JsonProperty("status")
    String status,

    @JsonProperty("from")
    Long from,

    @JsonProperty("to")
    Long to,

    @JsonProperty("amount_paise")
    long amountPaise
) {
}

