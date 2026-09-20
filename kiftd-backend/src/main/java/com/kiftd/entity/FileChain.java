package com.kiftd.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "file_chain")
@Data
public class FileChain {

    @Id
    @Column(name = "chain_key", length = 128)
    private String chainKey;

    @Column(name = "file_id", nullable = false, length = 64)
    private String fileId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expire_at")
    private Instant expireAt;

}
