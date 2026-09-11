package com.example.wallet.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "wallet",
    uniqueConstraints = @UniqueConstraint(name = "uk_wallet_user", columnNames = "user_id")
)
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private String userId;

    @Column(name = "balance_paise", nullable = false)
    private long balancePaise;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Constructors
    public Wallet() {
    }

    public Wallet(String userId, long balancePaise, LocalDateTime createdAt) {
        this.userId = userId;
        this.balancePaise = balancePaise;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public long getBalancePaise() {
        return balancePaise;
    }

    public void setBalancePaise(long balancePaise) {
        this.balancePaise = balancePaise;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}


