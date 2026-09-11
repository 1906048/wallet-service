package com.example.wallet.service;

import com.example.wallet.dto.CreateWalletResponse;
import com.example.wallet.entity.Wallet;
import com.example.wallet.exception.WalletNotFoundException;
import com.example.wallet.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional
    public CreateWalletResponse getOrCreateWallet(String userId) {
        return walletRepository.findByUserId(userId)
                .map(wallet -> {
                    log.info("Wallet already exists for user: {}, walletId: {}", userId, wallet.getId());
                    return new CreateWalletResponse(wallet.getId(), wallet.getBalancePaise());
                })
                .orElseGet(() -> {
                    Wallet wallet = new Wallet();
                    wallet.setUserId(userId);
                    wallet.setBalancePaise(0);
                    wallet.setCreatedAt(LocalDateTime.now());

                    try {
                        Wallet saved = walletRepository.saveAndFlush(wallet);
                        log.info("Wallet created for user: {}, walletId: {}", userId, saved.getId());
                        return new CreateWalletResponse(saved.getId(), saved.getBalancePaise());
                    } catch (DataIntegrityViolationException e) {
                        log.info("Concurrent wallet creation detected for user: {}, retrying...", userId);
                        // Another concurrent request created it
                        return walletRepository.findByUserId(userId)
                                .map(w -> {
                                    log.info("Retrieved wallet created by concurrent request: {}", w.getId());
                                    return new CreateWalletResponse(w.getId(), w.getBalancePaise());
                                })
                                .orElseThrow(() -> {
                                    log.error("Failed to retrieve wallet after concurrent creation for user: {}", userId);
                                    return e;
                                });
                    }
                });
    }

    @Transactional(readOnly = true)
    public CreateWalletResponse getWallet(Long walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> {
                    log.warn("Wallet not found: {}", walletId);
                    return new WalletNotFoundException("Wallet " + walletId + " not found");
                });
        return new CreateWalletResponse(wallet.getId(), wallet.getBalancePaise());
    }

}

