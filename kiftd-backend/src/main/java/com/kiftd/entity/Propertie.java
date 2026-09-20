package com.kiftd.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "properties")
@Data
public class Propertie {

    @Id
    @Column(name = "propertie_key", length = 128)
    private String propertieKey;

    @Column(name = "propertie_value", columnDefinition = "TEXT")
    private String propertieValue;

}
