package com.neuroequilibrium.nevertigo_qa.model;


import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Table;

@Entity
@Table(name = "PositionalData")
public class PositionalData {

    @EmbeddedId
    private PatientId id;

    public PatientId getId() { return id; }
    public void setId(PatientId id) { this.id = id; }
}