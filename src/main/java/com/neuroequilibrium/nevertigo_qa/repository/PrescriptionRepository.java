package com.neuroequilibrium.nevertigo_qa.repository;

import com.neuroequilibrium.nevertigo_qa.model.Prescription;
import com.neuroequilibrium.nevertigo_qa.model.PatientId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface PrescriptionRepository extends JpaRepository<Prescription, PatientId> {

    @Query(value =
        "SELECT COALESCE(am.BrandName, mn.BrandName) " +
        "FROM Prescription pr " +
        "CROSS JOIN JSON_TABLE(pr.Presjson, '$.medicine[*]' COLUMNS ( " +
        "  medname VARCHAR(200) PATH '$.medname', saltname VARCHAR(200) PATH '$.saltname' " +
        ")) AS med " +
        "LEFT JOIN AllMedicine am ON am.MedName = med.medname " +
        "LEFT JOIN Medicine mn ON mn.MedName = med.saltname " +
        "WHERE pr.PatientID = :patientId AND pr.ApDate = :apDate AND med.medname = :medName",
        nativeQuery = true)
    String getMedicineCompany(@Param("patientId") String patientId, @Param("apDate") LocalDate apDate,
                               @Param("medName") String medName);
}