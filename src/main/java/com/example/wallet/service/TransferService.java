package com.example.wallet.service;

import com.example.wallet.dto.TransferRequest;
import com.example.wallet.dto.TransferResponse;
import com.example.wallet.entity.Transfer;
import com.example.wallet.entity.TransferStatus;
import com.example.wallet.entity.Wallet;
import com.example.wallet.exception.IdempotencyConflictException;
import com.example.wallet.exception.WalletNotFoundException;
import com.example.wallet.repository.TransferRepository;
import com.example.wallet.repository.WalletRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final Counter transfersCreated;
    private final Counter transfersDeclined;
    private final Counter idempotentReplays;

    public TransferService(
            WalletRepository walletRepository,
            TransferRepository transferRepository,
            MeterRegistry meterRegistry) {
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;

        this.transfersCreated = Counter.builder("wallet_transfer_created_total")
                .description("Total transfers created successfully")
                .register(meterRegistry);
        this.transfersDeclined = Counter.builder("wallet_transfer_declined_insufficient_funds_total")
                .description("Total transfers declined due to insufficient funds")
                .register(meterRegistry);
        this.idempotentReplays = Counter.builder("wallet_transfer_idempotent_replay_total")
                .description("Total idempotent replays of existing transfers")
                .register(meterRegistry);
    }

    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        // Validate request
        validateTransferRequest(request);

        // Step 1: Check idempotency
        Optional<Transfer> existing = transferRepository.findByIdempotencyKey(request.idempotencyKey());

        if (existing.isPresent()) {
            Transfer transfer = existing.get();

            // Same key + different body = conflict
            if (!sameRequest(transfer, request)) {
                log.warn("Idempotency key reused with different body: key={}, " +
                        "original=from:{},to:{},amount:{}, " +
                        "retry=from:{},to:{},amount:{}",
                        request.idempotencyKey(),
                        transfer.getFromWalletId(), transfer.getToWalletId(), transfer.getAmountPaise(),
                        request.from(), request.to(), request.amountPaise());
                throw new IdempotencyConflictException("Idempotency key reused with different request body");
            }

            // Same key + same body = return original result
            log.info("Idempotent replay: key={}", request.idempotencyKey());
            idempotentReplays.increment();
            return toResponse(transfer);
        }

        // Step 2: Lock both wallets in deterministic (sorted) order
        Long minWalletId = Math.min(request.from(), request.to());
        Long maxWalletId = Math.max(request.from(), request.to());

        Wallet wallet1 = walletRepository.findByIdForUpdate(minWalletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet " + minWalletId + " not found"));

        Wallet wallet2 = walletRepository.findByIdForUpdate(maxWalletId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet " + maxWalletId + " not found"));

        // Assign from/to based on request (not lock order)
        Wallet fromWallet = request.from().equals(wallet1.getId()) ? wallet1 : wallet2;
        Wallet toWallet = request.to().equals(wallet1.getId()) ? wallet1 : wallet2;

        // Step 3: Check balance while wallet is locked
        if (fromWallet.getBalancePaise() < request.amountPaise()) {
            log.info("Transfer declined: insufficient funds. wallet={}, balance={}, amount={}",
                    fromWallet.getId(), fromWallet.getBalancePaise(), request.amountPaise());

            Transfer declined = new Transfer();
            declined.setFromWalletId(request.from());
            declined.setToWalletId(request.to());
            declined.setAmountPaise(request.amountPaise());
            declined.setIdempotencyKey(request.idempotencyKey());
            declined.setStatus(TransferStatus.DECLINED);
            declined.setCreatedAt(LocalDateTime.now());

            transferRepository.save(declined);
            transfersDeclined.increment();

            log.info("Transfer declined: transferId={}, key={}", declined.getId(), declined.getIdempotencyKey());
            return toResponse(declined);
        }

        // Step 4: Debit sender, credit receiver
        fromWallet.setBalancePaise(fromWallet.getBalancePaise() - request.amountPaise());
        toWallet.setBalancePaise(toWallet.getBalancePaise() + request.amountPaise());

        log.info("Transfer debited: walletId={}, amount={}, newBalance={}",
                fromWallet.getId(), request.amountPaise(), fromWallet.getBalancePaise());
        log.info("Transfer credited: walletId={}, amount={}, newBalance={}",
                toWallet.getId(), request.amountPaise(), toWallet.getBalancePaise());

        // Step 5: Persist transfer record in same transaction
        Transfer transfer = new Transfer();
        transfer.setFromWalletId(request.from());
        transfer.setToWalletId(request.to());
        transfer.setAmountPaise(request.amountPaise());
        transfer.setIdempotencyKey(request.idempotencyKey());
        transfer.setStatus(TransferStatus.SUCCESS);
        transfer.setCreatedAt(LocalDateTime.now());

        transferRepository.save(transfer);
        transfersCreated.increment();

        log.info("Transfer created: transferId={}, from={}, to={}, amount={}, key={}",
                transfer.getId(), transfer.getFromWalletId(), transfer.getToWalletId(),
                transfer.getAmountPaise(), transfer.getIdempotencyKey());

        return toResponse(transfer);
    }

    private void validateTransferRequest(TransferRequest request) {
        if (request.from() == null || request.to() == null) {
            throw new IllegalArgumentException("from and to wallet IDs required");
        }
        if (request.from().equals(request.to())) {
            throw new IllegalArgumentException("Cannot transfer to the same wallet");
        }
        if (request.amountPaise() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("Idempotency key required");
        }
    }

    private boolean sameRequest(Transfer transfer, TransferRequest request) {
        return transfer.getFromWalletId().equals(request.from())
                && transfer.getToWalletId().equals(request.to())
                && transfer.getAmountPaise() == request.amountPaise();
    }

    private TransferResponse toResponse(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getStatus().toString(),
                transfer.getFromWalletId(),
                transfer.getToWalletId(),
                transfer.getAmountPaise()
        );
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransfer(Long transferId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new WalletNotFoundException("Transfer " + transferId + " not found"));
        return toResponse(transfer);
    }

}

