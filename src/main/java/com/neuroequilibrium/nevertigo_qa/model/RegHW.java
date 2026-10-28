package com.neuroequilibrium.nevertigo_qa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "RegHW")
@IdClass(RegHW.RegHWId.class)
public class RegHW {

    @Id
    @Column(name = "ClinicID")
    private String ClinicID;

    @Id
    @Column(name = "UID")
    private String UID;

    @Column(name = "DVA")
    private String DVA;

    public String getClinicID() { return ClinicID; }
    public void setClinicID(String clinicID) { this.ClinicID = clinicID; }

    public String getUID() { return UID; }
    public void setUID(String UID) { this.UID = UID; }

    public String getDVA() { return DVA; }
    public void setDVA(String DVA) { this.DVA = DVA; }

    public static class RegHWId implements Serializable {
        private String ClinicID;
        private String UID;

        public RegHWId() {}

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RegHWId)) return false;
            RegHWId that = (RegHWId) o;
            return Objects.equals(ClinicID, that.ClinicID) && Objects.equals(UID, that.UID);
        }

        @Override
        public int hashCode() { return Objects.hash(ClinicID, UID); }
    }
}