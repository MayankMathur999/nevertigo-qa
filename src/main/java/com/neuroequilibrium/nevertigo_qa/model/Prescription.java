package com.neuroequilibrium.nevertigo_qa.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "Prescription")
public class Prescription {

    @EmbeddedId
    private PatientId id;

    public PatientId getId() { return id; }
    public void setId(PatientId id) { this.id = id; }
}