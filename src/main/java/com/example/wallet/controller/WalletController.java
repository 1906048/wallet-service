package com.example.wallet.controller;

import com.example.wallet.dto.CreateWalletResponse;
import com.example.wallet.service.WalletService;
import com.example.wallet.util.AuthUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    public ResponseEntity<CreateWalletResponse> createOrGet() {
        String userId = AuthUtil.getCurrentUserId();
        CreateWalletResponse response = walletService.getOrCreateWallet(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CreateWalletResponse> get(@PathVariable Long id) {
        CreateWalletResponse response = walletService.getWallet(id);
        return ResponseEntity.ok(response);
    }

}

