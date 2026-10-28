package com.neuroequilibrium.nevertigo_qa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Table;

@Entity
@Table(name = "Patient")
public class Patient {

    @EmbeddedId
    private PatientId id;

    @Column(name = "ClinicID")
    private String ClinicID;

    @Column(name = "TechID")
    private Integer TechID;

    public PatientId getId() { return id; }
    public void setId(PatientId id) { this.id = id; }

    public String getClinicID() { return ClinicID; }
    public void setClinicID(String clinicID) { this.ClinicID = clinicID; }

    public Integer getTechID() { return TechID; }
    public void setTechID(Integer techID) { this.TechID = techID; }
}