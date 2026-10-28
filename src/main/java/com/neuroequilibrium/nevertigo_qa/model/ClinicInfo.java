package com.neuroequilibrium.nevertigo_qa.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "Clinic-Info")
public class ClinicInfo {

    @Id
    @Column(name = "ClinicID")
    private String ClinicID;

    @Column(name = "Fname")
    private String Fname;

    @Column(name = "Lname")
    private String Lname;

    @Column(name = "isActive")
    private Integer isActive;

    @Column(name = "isExportClinic")
    private Integer isExportClinic;

    @Column(name = "Handler")
    private String Handler;

    public String getClinicID() { return ClinicID; }
    public void setClinicID(String clinicID) { this.ClinicID = clinicID; }

    public String getFname() { return Fname; }
    public void setFname(String fname) { this.Fname = fname; }

    public String getLname() { return Lname; }
    public void setLname(String lname) { this.Lname = lname; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    public Integer getIsExportClinic() { return isExportClinic; }
    public void setIsExportClinic(Integer isExportClinic) { this.isExportClinic = isExportClinic; }

    public String getHandler() { return Handler; }
    public void setHandler(String handler) { this.Handler = handler; }
}