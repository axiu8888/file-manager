package com.kiftd.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "account")
@Data
public class Account {

    @Id
    @Column(name = "account_id", length = 64)
    private String accountId;

    @Column(name = "account_name", nullable = false, unique = true, length = 128)
    private String accountName;

    @Column(name = "account_pwd", nullable = false)
    private String accountPwd;

    @Column(name = "account_auth", nullable = false, length = 512)
    private String accountAuth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

}
