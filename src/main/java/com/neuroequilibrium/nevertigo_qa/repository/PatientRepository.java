
package com.neuroequilibrium.nevertigo_qa.repository;

import com.neuroequilibrium.nevertigo_qa.model.Patient;
import com.neuroequilibrium.nevertigo_qa.model.PatientId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PatientRepository extends JpaRepository<Patient, PatientId> {

    @Query(value = "SELECT COUNT(*) FROM Patient p " +
            "WHERE p.ClinicID LIKE 'VertigoClinic%' " +
            "AND p.ApDate BETWEEN :start AND :end",
            nativeQuery = true)
    long countVisits(@Param("start") LocalDate start,
                     @Param("end") LocalDate end);

    @Query(value = "SELECT COUNT(*) FROM `Patient-Info` pi " +
            "WHERE pi.ClinicID LIKE 'VertigoClinic%' " +
            "AND pi.ApDate BETWEEN :start AND :end",
            nativeQuery = true)
    long countNewPatients(@Param("start") LocalDate start,
                          @Param("end") LocalDate end);

    @Query(value = "SELECT COUNT(DISTINCT p.PatientID) FROM Patient p " +
            "WHERE p.ClinicID LIKE 'VertigoClinic%' " +
            "AND p.ApDate BETWEEN :start AND :end " +
            "AND EXISTS (SELECT 1 FROM Patient p2 " +
            "WHERE p2.PatientID = p.PatientID " +
            "AND p2.ApDate <> p.ApDate)",
            nativeQuery = true)
    long countFollowupPatients(@Param("start") LocalDate start,
                               @Param("end") LocalDate end);

    @Query(value = "SELECT COUNT(DISTINCT p.PatientID) " +
            "FROM Patient p " +
            "JOIN Prescription pr ON pr.PatientID = p.PatientID " +
            "AND pr.ApDate = p.ApDate " +
            "WHERE p.ClinicID LIKE 'VertigoClinic%' " +
            "AND p.ApDate BETWEEN :start AND :end " +
            "AND pr.Cimp IS NOT NULL " +
            "AND TRIM(pr.Cimp) <> '' " +
            "AND LOWER(pr.Cimp) LIKE '%bppv%' " +
            "AND LOWER(pr.Cimp) NOT LIKE '%no bppv%' " +
            "AND LOWER(pr.Cimp) NOT LIKE '%post-bppv residual dizziness%' " +
            "AND LOWER(pr.Cimp) NOT LIKE '%post-traumatic bppv%' " +
            "AND LOWER(pr.Cimp) NOT LIKE '%the positional tests do not indicate bppv%'",
            nativeQuery = true)
    long countBppvPatients(@Param("start") LocalDate start,
                           @Param("end") LocalDate end);

    @Query(value = "SELECT ROUND(" +
            "100.0 * COUNT(DISTINCT CASE " +
            "WHEN LOWER(pr.Cimp) LIKE '%bppv%' " +
            "AND LOWER(pr.Cimp) NOT LIKE '%no bppv%' " +
            "AND LOWER(pr.Cimp) NOT LIKE '%post-bppv residual dizziness%' " +
            "AND LOWER(pr.Cimp) NOT LIKE '%post-traumatic bppv%' " +
            "AND LOWER(pr.Cimp) NOT LIKE '%the positional tests do not indicate bppv%' " +
            "THEN p.PatientID END) / " +
            "NULLIF(COUNT(DISTINCT p.PatientID), 0), 2) " +
            "FROM Patient p " +
            "LEFT JOIN Prescription pr ON pr.PatientID = p.PatientID " +
            "AND pr.ApDate = p.ApDate " +
            "WHERE p.ClinicID LIKE 'VertigoClinic%' " +
            "AND p.ApDate BETWEEN :start AND :end",
            nativeQuery = true)
    double countBppvPercentage(@Param("start") LocalDate start,
                               @Param("end") LocalDate end);

    @Query(value = "SELECT CAST(ROUND(COALESCE(SUM(GREATEST(" +
            "(CASE WHEN TRIM(COALESCE(b.NCost, '')) REGEXP '^-?[0-9]+([.][0-9]+)?$' " +
            "THEN CAST(TRIM(b.NCost) AS DECIMAL(12,2)) ELSE 0 END) - " +
            "(CASE WHEN TRIM(COALESCE(b.NDiscount, '')) REGEXP '^-?[0-9]+([.][0-9]+)?$' " +
            "THEN CAST(TRIM(b.NDiscount) AS DECIMAL(12,2)) ELSE 0 END), 0)), 0), 0) AS SIGNED) " +
            "FROM Patient p " +
            "JOIN Bill b ON b.PatientID = p.PatientID " +
            "AND b.ApDate = p.ApDate " +
            "WHERE p.ClinicID LIKE 'VertigoClinic%' " +
            "AND p.ApDate BETWEEN :start AND :end",
            nativeQuery = true)
    long sumNetNeuroRevenue(@Param("start") LocalDate start,
                            @Param("end") LocalDate end);

    @Query(value = "SELECT COALESCE(SUM(a.Amount), 0) " +
            "FROM AnalysisBill a " +
            "JOIN Patient p ON p.PatientID = a.PatientID " +
            "AND p.ApDate = a.ApDate " +
            "WHERE p.ClinicID LIKE 'VertigoClinic%' " +
            "AND p.ApDate BETWEEN :start AND :end",
            nativeQuery = true)
    double sumAnalysisAmount(@Param("start") LocalDate start,
                             @Param("end") LocalDate end);

    @Query(value = "SELECT COUNT(DISTINCT d.PatientID) " +
            "FROM DVA d " +
            "JOIN Patient p ON p.PatientID = d.PatientID " +
            "AND p.ApDate = d.ApDate " +
            "WHERE p.ClinicID LIKE 'VertigoClinic%' " +
            "AND p.ApDate BETWEEN :start AND :end " +
            "AND EXISTS (SELECT 1 FROM ReportRequest r " +
            "WHERE r.PatientID = d.PatientID " +
            "AND r.Test LIKE '%DVA%')",
            nativeQuery = true)
    long countDvaReportReceived(@Param("start") LocalDate start,
                                @Param("end") LocalDate end);

    @Query(value = "SELECT COUNT(*) " +
            "FROM Prescription pr " +
            "JOIN Patient p ON p.PatientID = pr.PatientID " +
            "AND p.ApDate = pr.ApDate " +
            "JOIN JSON_TABLE(" +
            "pr.Presjson, '$.medicine[*]' " +
            "COLUMNS (medname VARCHAR(255) PATH '$.medname')" +
            ") AS med " +
            "WHERE p.ClinicID LIKE 'VertigoClinic%' " +
            "AND p.ApDate BETWEEN :start AND :end " +
            "AND med.medname IS NOT NULL " +
            "AND TRIM(med.medname) <> ''",
            nativeQuery = true)
    long countMedicineLines(@Param("start") LocalDate start,
                            @Param("end") LocalDate end);


                            @Query(value = "SELECT MAX(GREATEST(" +
        "ABS(COALESCE(NULLIF(pd.DHLSPVHL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.DHLSPVHR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.DHLSPVVL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.DHLSPVVR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.DHRSPVHL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.DHRSPVHR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.DHRSPVVL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.DHRSPVVR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.LatLSPVHL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.LatLSPVHR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.LatLSPVVL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.LatLSPVVR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.LatRSPVHL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.LatRSPVHR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.LatRSPVVL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.LatRSPVVR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.SupSPVHL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.SupSPVHR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.SupSPVVL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.SupSPVVR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.PPSPVHL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.PPSPVHR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.PPSPVVL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.PPSPVVR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.HHSPVHL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.HHSPVHR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.HHSPVVL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.HHSPVVR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.LeanSPVHL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.LeanSPVHR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.LeanSPVVL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.LeanSPVVR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.BowSPVHL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.BowSPVHR, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.BowSPVVL, ''), 0)), " +
        "ABS(COALESCE(NULLIF(pd.BowSPVVR, ''), 0))" +
        ")) " +
        "FROM PositionalData pd " +
        "JOIN Patient p ON p.PatientID = pd.PatientID " +
        "AND p.ApDate = pd.ApDate " +
        "WHERE p.ClinicID LIKE 'VertigoClinic%' " +
        "AND p.ApDate BETWEEN :start AND :end",
        nativeQuery = true)
Double maxAbsPositionalSpv(@Param("start") LocalDate start,
                           @Param("end") LocalDate end);

                           @Query(value = "SELECT COUNT(DISTINCT v.PatientID) FROM Patient v " +
                "WHERE v.ClinicID LIKE 'VertigoClinic%' " +
                "AND v.ApDate BETWEEN :start AND :end",
       nativeQuery = true)
long countDistinctPatients(@Param("start") LocalDate start, @Param("end") LocalDate end);
@Query(value =
    "SELECT COALESCE(SUM(GREATEST( " +
    "  CAST(CASE WHEN UPPER(TRIM(IFNULL(b.NCost,''))) IN ('','NA','N/A') THEN '0' ELSE TRIM(b.NCost) END AS DECIMAL(12,2)) " +
    "  - CAST(CASE WHEN UPPER(TRIM(IFNULL(b.NDiscount,''))) IN ('','NA','N/A') THEN '0' ELSE TRIM(b.NDiscount) END AS DECIMAL(12,2)), 0)), 0) " +
    "FROM Bill b " +
    "INNER JOIN Patient v ON v.PatientID = b.PatientID AND v.ApDate = b.ApDate " +
    "INNER JOIN `Patient-Info` pi ON pi.PatientID = b.PatientID " +
    "WHERE v.ClinicID LIKE 'VertigoClinic%' AND v.ApDate BETWEEN :start AND :end " +
    "AND pi.healthScheme = 'CGHS'",
    nativeQuery = true)
long sumCghsRevenue(@Param("start") LocalDate start, @Param("end") LocalDate end);

@Query(value =
    "SELECT COALESCE(SUM(GREATEST( " +
    "  CAST(CASE WHEN UPPER(TRIM(IFNULL(b.NCost,''))) IN ('','NA','N/A') THEN '0' ELSE TRIM(b.NCost) END AS DECIMAL(12,2)) " +
    "  - CAST(CASE WHEN UPPER(TRIM(IFNULL(b.NDiscount,''))) IN ('','NA','N/A') THEN '0' ELSE TRIM(b.NDiscount) END AS DECIMAL(12,2)), 0)), 0) " +
    "FROM Bill b " +
    "INNER JOIN Patient v ON v.PatientID = b.PatientID AND v.ApDate = b.ApDate " +
    "INNER JOIN `Patient-Info` pi ON pi.PatientID = b.PatientID " +
    "WHERE v.ClinicID LIKE 'VertigoClinic%' AND v.ApDate BETWEEN :start AND :end " +
    "AND COALESCE(pi.healthScheme,'') <> 'CGHS'",
    nativeQuery = true)
long sumCashRevenue(@Param("start") LocalDate start, @Param("end") LocalDate end);

@Query(value =
    "SELECT COALESCE(SUM(GREATEST( " +
    "  CAST(CASE WHEN UPPER(TRIM(IFNULL(b.NCost,''))) IN ('','NA','N/A') THEN '0' ELSE TRIM(b.NCost) END AS DECIMAL(12,2)) " +
    "  - CAST(CASE WHEN UPPER(TRIM(IFNULL(b.NDiscount,''))) IN ('','NA','N/A') THEN '0' ELSE TRIM(b.NDiscount) END AS DECIMAL(12,2)), 0)), 0) " +
    "FROM Bill b " +
    "INNER JOIN Patient v ON v.PatientID = b.PatientID AND v.ApDate = b.ApDate " +
    "INNER JOIN `Clinic-Info` ci ON ci.ClinicID = v.ClinicID " +
    "WHERE v.ClinicID LIKE 'VertigoClinic%' AND v.ApDate BETWEEN :start AND :end " +
    "AND ci.Handler = :handler",
    nativeQuery = true)
long sumRevenueByHandler(@Param("start") LocalDate start, @Param("end") LocalDate end, @Param("handler") String handler);

@Query(value =
    "SELECT COALESCE(SUM(GREATEST( " +
    "  CAST(CASE WHEN UPPER(TRIM(IFNULL(b.NCost,''))) IN ('','NA','N/A') THEN '0' ELSE TRIM(b.NCost) END AS DECIMAL(12,2)) " +
    "  - CAST(CASE WHEN UPPER(TRIM(IFNULL(b.NDiscount,''))) IN ('','NA','N/A') THEN '0' ELSE TRIM(b.NDiscount) END AS DECIMAL(12,2)), 0)), 0) " +
    "FROM Bill b " +
    "INNER JOIN Patient v ON v.PatientID = b.PatientID AND v.ApDate = b.ApDate " +
    "INNER JOIN Technician t ON t.TechID = v.TechID " +
    "WHERE v.ClinicID LIKE 'VertigoClinic%' AND v.ApDate BETWEEN :start AND :end " +
    "AND t.Fname = :fname AND t.Lname = :lname",
    nativeQuery = true)
long sumRevenueByTechnician(@Param("start") LocalDate start, @Param("end") LocalDate end,
                             @Param("fname") String fname, @Param("lname") String lname);
                             @Query(value =
    "SELECT COALESCE(NULLIF(pr.Cimp,''), NULLIF(p.Impression,''), " +
    "  NULLIF(JSON_UNQUOTE(JSON_EXTRACT(pr.Presjson, '$.clinicRehabImpression')), ''), NULLIF(pr.RehabImpression,'')) " +
    "FROM Prescription pr " +
    "INNER JOIN Patient p ON p.PatientID = pr.PatientID AND p.ApDate = pr.ApDate " +
    "WHERE pr.PatientID = :patientId AND pr.ApDate = :apDate",
    nativeQuery = true)
String getDiagnosis(@Param("patientId") String patientId, @Param("apDate") LocalDate apDate);

@Query(value =
    "SELECT IFNULL(CONCAT(di.Fname, ' ', di.Lname), 'NONE') " +
    "FROM Patient p " +
    "INNER JOIN `Patient-Info` pi ON pi.PatientID = p.PatientID " +
    "LEFT JOIN `Doctor-Info` di ON di.Email = pi.DoctorID AND di.ClinicID = p.ClinicID " +
    "WHERE p.PatientID = :patientId AND p.ApDate = :apDate",
    nativeQuery = true)
String getDoctor(@Param("patientId") String patientId, @Param("apDate") LocalDate apDate);

@Query(value =
        "SELECT COUNT(*) FROM `Clinic-Info` " +
        "WHERE ClinicID LIKE 'VertigoClinic%'",
        nativeQuery = true)
long countClinics();

@Query(value =
        "SELECT ci.city, COUNT(*) AS visits " +
        "FROM Patient p " +
        "JOIN `Clinic-Info` ci ON ci.ClinicID = p.ClinicID " +
        "WHERE p.ClinicID LIKE 'VertigoClinic%' " +
        "AND p.ApDate BETWEEN :start AND :end " +
        "GROUP BY ci.city " +
        "ORDER BY ci.city",
        nativeQuery = true)
List<Object[]> countVisitsByClinic(@Param("start") LocalDate start,
                                    @Param("end") LocalDate end);

                                    @Query(value =
        "SELECT ci.city, " +
        "COALESCE(SUM(GREATEST( " +
        "CAST(CASE WHEN TRIM(IFNULL(b.NCost,'')) IN ('','NA','N/A') " +
        "THEN '0' ELSE TRIM(b.NCost) END AS DECIMAL(12,2)) - " +
        "CAST(CASE WHEN TRIM(IFNULL(b.NDiscount,'')) IN ('','NA','N/A') " +
        "THEN '0' ELSE TRIM(b.NDiscount) END AS DECIMAL(12,2)), 0)), 0) " +
        "FROM Bill b " +
        "JOIN Patient p ON p.PatientID = b.PatientID " +
        "AND p.ApDate = b.ApDate " +
        "JOIN `Clinic-Info` ci ON ci.ClinicID = p.ClinicID " +
        "WHERE p.ClinicID LIKE 'VertigoClinic%' " +
        "AND p.ApDate BETWEEN :start AND :end " +
        "GROUP BY ci.city " +
        "ORDER BY ci.city",
        nativeQuery = true)
List<Object[]> sumRevenueByClinic(@Param("start") LocalDate start,
                                   @Param("end") LocalDate end);

                                   @Query(value =
        "SELECT b.Test, " +
        "COALESCE(SUM(GREATEST( " +
        "CAST(CASE WHEN TRIM(IFNULL(b.NCost,'')) IN ('','NA','N/A') " +
        "THEN '0' ELSE TRIM(b.NCost) END AS DECIMAL(12,2)) - " +
        "CAST(CASE WHEN TRIM(IFNULL(b.NDiscount,'')) IN ('','NA','N/A') " +
        "THEN '0' ELSE TRIM(b.NDiscount) END AS DECIMAL(12,2)), 0)), 0) " +
        "FROM Bill b " +
        "JOIN Patient p ON p.PatientID = b.PatientID " +
        "AND p.ApDate = b.ApDate " +
        "WHERE p.ClinicID LIKE 'VertigoClinic%' " +
        "AND p.ApDate BETWEEN :start AND :end " +
        "GROUP BY b.Test " +
        "ORDER BY b.Test",
        nativeQuery = true)
List<Object[]> sumRevenueByService(@Param("start") LocalDate start,
                                   @Param("end") LocalDate end);
}