
package com.neuroequilibrium.mcp_backend;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class DatabaseTestController {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseTestController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 1. Total Visits
    @GetMapping("/metrics/visits")
    public Map<String, Object> getTotalVisits(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        validateDates(start, end);

        String sql = """
                SELECT COUNT(*)
                FROM patient
                WHERE ClinicID LIKE 'VertigoClinic%'
                AND ApDate BETWEEN ? AND ?
                """;

        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, start, end);
        return response("total_visits", start, end, value);
    }

    // 2. New Patients
    @GetMapping("/metrics/new-patients")
    public Map<String, Object> getNewPatients(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        validateDates(start, end);

        String sql = """
                SELECT COUNT(*)
                FROM `patient-info`
                WHERE ClinicID LIKE 'VertigoClinic%'
                AND ApDate BETWEEN ? AND ?
                """;

        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, start, end);
        return response("new_patients", start, end, value);
    }

    // 3. Net Neuro Revenue
    @GetMapping("/metrics/net-neuro-revenue")
    public Map<String, Object> getNetNeuroRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        validateDates(start, end);

        String sql = """
                SELECT CAST(ROUND(COALESCE(SUM(GREATEST(
                    (CASE
                        WHEN TRIM(COALESCE(b.NCost, '')) REGEXP '^-?[0-9]+([.][0-9]+)?$'
                        THEN CAST(TRIM(b.NCost) AS DECIMAL(12,2))
                        ELSE 0
                    END)
                    -
                    (CASE
                        WHEN TRIM(COALESCE(b.NDiscount, '')) REGEXP '^-?[0-9]+([.][0-9]+)?$'
                        THEN CAST(TRIM(b.NDiscount) AS DECIMAL(12,2))
                        ELSE 0
                    END), 0)), 0), 0) AS SIGNED)
                FROM patient p
                JOIN bill b ON b.PatientID = p.PatientID
                    AND b.ApDate = p.ApDate
                WHERE p.ClinicID LIKE 'VertigoClinic%'
                    AND p.ApDate BETWEEN ? AND ?
                """;

        Long value = jdbcTemplate.queryForObject(sql, Long.class, start, end);
        return response("net_neuro_revenue", start, end, value);
    }

    // 4. Patients With Visit
    @GetMapping("/metrics/patients-with-visit")
    public Map<String, Object> getPatientsWithVisit(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        validateDates(start, end);

        String sql = """
                SELECT COUNT(DISTINCT p.PatientID)
                FROM patient p
                WHERE p.ClinicID LIKE 'VertigoClinic%'
                AND p.ApDate BETWEEN ? AND ?
                """;

        Long value = jdbcTemplate.queryForObject(sql, Long.class, start, end);
        return response("patients_with_visit", start, end, value);
    }

    // 5. Follow-up Patients
    @GetMapping("/metrics/followup-patients")
    public Map<String, Object> getFollowupPatients(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        validateDates(start, end);

        String sql = """
                SELECT COUNT(DISTINCT p.PatientID)
                FROM patient p
                WHERE p.ClinicID LIKE 'VertigoClinic%'
                AND p.ApDate BETWEEN ? AND ?
                AND EXISTS (
                    SELECT 1
                    FROM patient p2
                    WHERE p2.PatientID = p.PatientID
                    AND p2.ApDate <> p.ApDate
                )
                """;

        Long value = jdbcTemplate.queryForObject(sql, Long.class, start, end);
        return response("followup_patients", start, end, value);
    }

    // 6. BPPV Patients
    @GetMapping("/metrics/bppv-patients")
    public Map<String, Object> getBppvPatients(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        validateDates(start, end);

        String sql = """
                SELECT COUNT(DISTINCT p.PatientID)
                FROM patient p
                JOIN prescription pr
                    ON pr.PatientID = p.PatientID
                    AND pr.ApDate = p.ApDate
                WHERE p.ClinicID LIKE 'VertigoClinic%'
                AND p.ApDate BETWEEN ? AND ?
                AND pr.Cimp IS NOT NULL
                AND TRIM(pr.Cimp) <> ''
                AND LOWER(pr.Cimp) LIKE '%bppv%'
                AND LOWER(pr.Cimp) NOT LIKE '%no bppv%'
                AND LOWER(pr.Cimp) NOT LIKE '%post-bppv residual dizziness%'
                AND LOWER(pr.Cimp) NOT LIKE '%post-traumatic bppv%'
                AND LOWER(pr.Cimp) NOT LIKE '%the positional tests do not indicate bppv%'
                """;

        Long value = jdbcTemplate.queryForObject(sql, Long.class, start, end);
        return response("bppv_patients", start, end, value);
    }

    // 7. BPPV Percentage
    @GetMapping("/metrics/bppv-percentage")
    public Map<String, Object> getBppvPercentage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        validateDates(start, end);

        String sql = """
                SELECT ROUND(
                    100.0 * COUNT(DISTINCT CASE
                        WHEN LOWER(pr.Cimp) LIKE '%bppv%'
                        AND LOWER(pr.Cimp) NOT LIKE '%no bppv%'
                        AND LOWER(pr.Cimp) NOT LIKE '%post-bppv residual dizziness%'
                        AND LOWER(pr.Cimp) NOT LIKE '%post-traumatic bppv%'
                        AND LOWER(pr.Cimp) NOT LIKE '%the positional tests do not indicate bppv%'
                        THEN p.PatientID
                    END)
                    / NULLIF(COUNT(DISTINCT p.PatientID), 0),
                    2
                )
                FROM patient p
                LEFT JOIN prescription pr
                    ON pr.PatientID = p.PatientID
                    AND pr.ApDate = p.ApDate
                WHERE p.ClinicID LIKE 'VertigoClinic%'
                AND p.ApDate BETWEEN ? AND ?
                """;

        Double value = jdbcTemplate.queryForObject(sql, Double.class, start, end);
        return response("bppv_percentage", start, end, value);
    }

    // 8. Revenue By Scheme
    @GetMapping("/metrics/revenue-by-scheme")
    public Map<String, Object> getRevenueByScheme(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        validateDates(start, end);

        String cghsSql = """
                SELECT COALESCE(SUM(GREATEST(
                    CAST(CASE
                        WHEN UPPER(TRIM(IFNULL(b.NCost,''))) IN ('','NA','N/A')
                        THEN '0' ELSE TRIM(b.NCost)
                    END AS DECIMAL(12,2))
                    -
                    CAST(CASE
                        WHEN UPPER(TRIM(IFNULL(b.NDiscount,''))) IN ('','NA','N/A')
                        THEN '0' ELSE TRIM(b.NDiscount)
                    END AS DECIMAL(12,2)),
                    0
                )), 0)
                FROM bill b
                INNER JOIN patient v
                    ON v.PatientID = b.PatientID
                    AND v.ApDate = b.ApDate
                INNER JOIN `patient-info` pi
                    ON pi.PatientID = b.PatientID
                WHERE v.ClinicID LIKE 'VertigoClinic%'
                AND v.ApDate BETWEEN ? AND ?
                AND pi.healthScheme = 'CGHS'
                """;

        String cashSql = """
                SELECT COALESCE(SUM(GREATEST(
                    CAST(CASE
                        WHEN UPPER(TRIM(IFNULL(b.NCost,''))) IN ('','NA','N/A')
                        THEN '0' ELSE TRIM(b.NCost)
                    END AS DECIMAL(12,2))
                    -
                    CAST(CASE
                        WHEN UPPER(TRIM(IFNULL(b.NDiscount,''))) IN ('','NA','N/A')
                        THEN '0' ELSE TRIM(b.NDiscount)
                    END AS DECIMAL(12,2)),
                    0
                )), 0)
                FROM bill b
                INNER JOIN patient v
                    ON v.PatientID = b.PatientID
                    AND v.ApDate = b.ApDate
                INNER JOIN `patient-info` pi
                    ON pi.PatientID = b.PatientID
                WHERE v.ClinicID LIKE 'VertigoClinic%'
                AND v.ApDate BETWEEN ? AND ?
                AND COALESCE(pi.healthScheme,'') <> 'CGHS'
                """;

        Long cghsRevenue = jdbcTemplate.queryForObject(
                cghsSql, Long.class, start, end);

        Long cashRevenue = jdbcTemplate.queryForObject(
                cashSql, Long.class, start, end);

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("CGHS", cghsRevenue);
        value.put("cash", cashRevenue);

        return response("revenue_by_scheme", start, end, value);
    }

    // 9. Revenue By Staff
    @GetMapping("/metrics/revenue-by-staff")
    public Map<String, Object> getRevenueByStaff(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        validateDates(start, end);

        String handlerSql = """
                SELECT COALESCE(SUM(GREATEST(
                    CAST(CASE
                        WHEN UPPER(TRIM(IFNULL(b.NCost,''))) IN ('','NA','N/A')
                        THEN '0' ELSE TRIM(b.NCost)
                    END AS DECIMAL(12,2))
                    -
                    CAST(CASE
                        WHEN UPPER(TRIM(IFNULL(b.NDiscount,''))) IN ('','NA','N/A')
                        THEN '0' ELSE TRIM(b.NDiscount)
                    END AS DECIMAL(12,2)),
                    0
                )), 0)
                FROM bill b
                INNER JOIN patient v
                    ON v.PatientID = b.PatientID
                    AND v.ApDate = b.ApDate
                INNER JOIN `clinic-info` ci
                    ON ci.ClinicID = v.ClinicID
                WHERE v.ClinicID LIKE 'VertigoClinic%'
                AND v.ApDate BETWEEN ? AND ?
                AND ci.Handler = ?
                """;

        String technicianSql = """
                SELECT COALESCE(SUM(GREATEST(
                    CAST(CASE
                        WHEN UPPER(TRIM(IFNULL(b.NCost,''))) IN ('','NA','N/A')
                        THEN '0' ELSE TRIM(b.NCost)
                    END AS DECIMAL(12,2))
                    -
                    CAST(CASE
                        WHEN UPPER(TRIM(IFNULL(b.NDiscount,''))) IN ('','NA','N/A')
                        THEN '0' ELSE TRIM(b.NDiscount)
                    END AS DECIMAL(12,2)),
                    0
                )), 0)
                FROM bill b
                INNER JOIN patient v
                    ON v.PatientID = b.PatientID
                    AND v.ApDate = b.ApDate
                INNER JOIN technician t
                    ON t.TechID = v.TechID
                WHERE v.ClinicID LIKE 'VertigoClinic%'
                AND v.ApDate BETWEEN ? AND ?
                AND t.Fname = ?
                AND t.Lname = ?
                """;

        Map<String, Object> value = new LinkedHashMap<>();

        value.put("Anita Rao", jdbcTemplate.queryForObject(
                handlerSql, Long.class, start, end, "Anita Rao"));

        value.put("Rahul Mehta", jdbcTemplate.queryForObject(
                handlerSql, Long.class, start, end, "Rahul Mehta"));

        value.put("Riya Shah", jdbcTemplate.queryForObject(
                technicianSql, Long.class, start, end, "Riya", "Shah"));

        value.put("Arjun Nair", jdbcTemplate.queryForObject(
                technicianSql, Long.class, start, end, "Arjun", "Nair"));

        value.put("Meera Das", jdbcTemplate.queryForObject(
                technicianSql, Long.class, start, end, "Meera", "Das"));

        return response("revenue_by_staff", start, end, value);
    }

    // 10. Analysis Amount
    @GetMapping("/metrics/analysis-amount")
    public Map<String, Object> getAnalysisAmount(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        validateDates(start, end);

        String sql = """
                SELECT COALESCE(SUM(a.Amount), 0)
                FROM analysisbill a
                JOIN patient p
                    ON p.PatientID = a.PatientID
                    AND p.ApDate = a.ApDate
                WHERE p.ClinicID LIKE 'VertigoClinic%'
                AND p.ApDate BETWEEN ? AND ?
                """;

        Double value = jdbcTemplate.queryForObject(sql, Double.class, start, end);
        return response("analysis_amount", start, end, value);
    }

    // 11. DVA Report Received
    @GetMapping("/metrics/dva-report-received")
    public Map<String, Object> getDvaReportReceived(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        validateDates(start, end);

        String sql = """
                SELECT COUNT(DISTINCT d.PatientID)
                FROM dva d
                JOIN patient p
                    ON p.PatientID = d.PatientID
                    AND p.ApDate = d.ApDate
                WHERE p.ClinicID LIKE 'VertigoClinic%'
                AND p.ApDate BETWEEN ? AND ?
                AND EXISTS (
                    SELECT 1
                    FROM reportrequest r
                    WHERE r.PatientID = d.PatientID
                    AND r.Test LIKE '%DVA%'
                )
                """;

        Long value = jdbcTemplate.queryForObject(sql, Long.class, start, end);
        return response("dva_report_received", start, end, value);
    }

    // 12. Medicine Lines
    @GetMapping("/metrics/medicine-line")
    public Map<String, Object> getMedicineLines(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        validateDates(start, end);

        String sql = """
                SELECT COUNT(*)
                FROM prescription pr
                JOIN patient p
                    ON p.PatientID = pr.PatientID
                    AND p.ApDate = pr.ApDate
                JOIN JSON_TABLE(
                    pr.Presjson,
                    '$.medicine[*]'
                    COLUMNS (
                        medname VARCHAR(255) PATH '$.medname'
                    )
                ) AS med
                WHERE p.ClinicID LIKE 'VertigoClinic%'
                AND p.ApDate BETWEEN ? AND ?
                AND med.medname IS NOT NULL
                AND TRIM(med.medname) <> ''
                """;

        Long value = jdbcTemplate.queryForObject(sql, Long.class, start, end);
        return response("medicine_line", start, end, value);
    }

    // 13. Low SPV Without Torsion
    @GetMapping("/metrics/low-spv-no-torsion-visits")
    public Map<String, Object> getLowSpvNoTorsionVisits(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        validateDates(start, end);

        String sql = """
                SELECT COUNT(*)
                FROM (
                    SELECT
                        pd.PatientID,
                        pd.ApDate,
                        GREATEST(
                            IFNULL(ABS(CAST(NULLIF(TRIM(pd.DHLSPVHL), '') AS DECIMAL(10,2))), 0),
                            IFNULL(ABS(CAST(NULLIF(TRIM(pd.DHLSPVHR), '') AS DECIMAL(10,2))), 0),
                            IFNULL(ABS(CAST(NULLIF(TRIM(pd.LatLSPVHL), '') AS DECIMAL(10,2))), 0),
                            IFNULL(ABS(CAST(NULLIF(TRIM(pd.LatLSPVHR), '') AS DECIMAL(10,2))), 0)
                        ) AS max_abs_spv,
                        (
                            (pd.DHLT IS NULL OR TRIM(pd.DHLT) = ''
                                OR UPPER(TRIM(pd.DHLT)) IN ('FALSE','NA'))
                            AND
                            (pd.DHRT IS NULL OR TRIM(pd.DHRT) = ''
                                OR UPPER(TRIM(pd.DHRT)) IN ('FALSE','NA'))
                            AND
                            (pd.LLT IS NULL OR TRIM(pd.LLT) = ''
                                OR UPPER(TRIM(pd.LLT)) IN ('FALSE','NA'))
                        ) AS no_torsion
                    FROM positionaldata pd
                    INNER JOIN patient v
                        ON v.PatientID = pd.PatientID
                        AND v.ApDate = pd.ApDate
                    WHERE v.ClinicID LIKE 'VertigoClinic%'
                    AND v.ApDate BETWEEN ? AND ?
                ) sub
                WHERE max_abs_spv <= 6
                AND no_torsion = 1
                """;

        Long value = jdbcTemplate.queryForObject(sql, Long.class, start, end);
        return response("low_spv_no_torsion_visits", start, end, value);
    }

    // Common response format
    private Map<String, Object> response(
            String metric,
            LocalDate start,
            LocalDate end,
            Object value) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("metric", metric);
        result.put("start", start);
        result.put("end", end);
        result.put("value", value);
        return result;
    }

    // Date validation
    private void validateDates(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) {
            throw new IllegalArgumentException(
                    "Start date cannot be after end date."
            );
        }
    }
}
