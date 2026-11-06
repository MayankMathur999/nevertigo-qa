
package com.neuroequilibrium.mcp_backend;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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

        Integer totalVisits = jdbcTemplate.queryForObject(
                sql, Integer.class, start, end);

        return Map.of(
                "metric", "total_visits",
                "start", start,
                "end", end,
                "value", totalVisits
        );
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

        Integer newPatients = jdbcTemplate.queryForObject(
                sql, Integer.class, start, end);

        return Map.of(
                "metric", "new_patients",
                "start", start,
                "end", end,
                "value", newPatients
        );
    }

    // 3. Net Neuro Revenue (same calculation as Project 1)
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

        Long revenue = jdbcTemplate.queryForObject(
                sql, Long.class, start, end);

        return Map.of(
                "metric", "net_neuro_revenue",
                "start", start,
                "end", end,
                "value", revenue
        );
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
