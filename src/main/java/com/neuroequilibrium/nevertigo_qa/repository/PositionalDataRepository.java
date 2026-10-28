package com.neuroequilibrium.nevertigo_qa.repository;

import com.neuroequilibrium.nevertigo_qa.model.PositionalData;
import com.neuroequilibrium.nevertigo_qa.model.PatientId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface PositionalDataRepository extends JpaRepository<PositionalData, PatientId> {

    @Query(value =
        "SELECT COUNT(*) FROM ( " +
        "  SELECT pd.PatientID, pd.ApDate, " +
        "    GREATEST( " +
        "      IFNULL(ABS(CAST(NULLIF(TRIM(pd.DHLSPVHL), '') AS DECIMAL(10,2))), 0), " +
        "      IFNULL(ABS(CAST(NULLIF(TRIM(pd.DHLSPVHR), '') AS DECIMAL(10,2))), 0), " +
        "      IFNULL(ABS(CAST(NULLIF(TRIM(pd.LatLSPVHL), '') AS DECIMAL(10,2))), 0), " +
        "      IFNULL(ABS(CAST(NULLIF(TRIM(pd.LatLSPVHR), '') AS DECIMAL(10,2))), 0) " +
        "    ) AS max_abs_spv, " +
        "    ( (pd.DHLT IS NULL OR TRIM(pd.DHLT)='' OR UPPER(TRIM(pd.DHLT)) IN ('FALSE','NA')) " +
        "      AND (pd.DHRT IS NULL OR TRIM(pd.DHRT)='' OR UPPER(TRIM(pd.DHRT)) IN ('FALSE','NA')) " +
        "      AND (pd.LLT IS NULL OR TRIM(pd.LLT)='' OR UPPER(TRIM(pd.LLT)) IN ('FALSE','NA')) " +
        "    ) AS no_torsion " +
        "  FROM PositionalData pd " +
        "  INNER JOIN Patient v ON v.PatientID = pd.PatientID AND v.ApDate = pd.ApDate " +
        "  WHERE v.ClinicID LIKE 'VertigoClinic%' AND v.ApDate BETWEEN :start AND :end " +
        ") sub " +
        "WHERE max_abs_spv <= 6 AND no_torsion = 1",
        nativeQuery = true)
    long countLowSpvNoTorsion(@Param("start") LocalDate start, @Param("end") LocalDate end);
}