package com.kiftd.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "properties")
public class Propertie {

    @Id
    @Column(name = "propertie_key", length = 128)
    private String propertieKey;

    @Column(name = "propertie_value", columnDefinition = "TEXT")
    private String propertieValue;

    public String getPropertieKey() { return propertieKey; }
    public void setPropertieKey(String propertieKey) { this.propertieKey = propertieKey; }
    public String getPropertieValue() { return propertieValue; }
    public void setPropertieValue(String propertieValue) { this.propertieValue = propertieValue; }
}
