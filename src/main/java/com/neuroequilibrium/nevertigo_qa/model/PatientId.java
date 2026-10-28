package com.neuroequilibrium.nevertigo_qa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

@Embeddable
public class PatientId implements Serializable {

    @Column(name = "PatientID")
    private String PatientID;

    @Column(name = "ApDate")
    private LocalDate ApDate;

    public String getPatientID() { return PatientID; }
    public void setPatientID(String patientID) { this.PatientID = patientID; }

    public LocalDate getApDate() { return ApDate; }
    public void setApDate(LocalDate apDate) { this.ApDate = apDate; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PatientId)) return false;
        PatientId that = (PatientId) o;
        return Objects.equals(PatientID, that.PatientID) && Objects.equals(ApDate, that.ApDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(PatientID, ApDate);
    }
}