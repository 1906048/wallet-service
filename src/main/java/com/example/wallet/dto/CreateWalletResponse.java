package com.example.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateWalletResponse(
    @JsonProperty("id")
    Long id,

    @JsonProperty("balance_paise")
    long balancePaise
) {
}

