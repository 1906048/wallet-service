package com.example.wallet.controller;

import com.example.wallet.dto.TransferRequest;
import com.example.wallet.dto.TransferResponse;
import com.example.wallet.service.TransferService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(@RequestBody TransferRequest request) {
        TransferResponse response = transferService.transfer(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> get(@PathVariable Long id) {
        TransferResponse response = transferService.getTransfer(id);
        return ResponseEntity.ok(response);
    }

}

