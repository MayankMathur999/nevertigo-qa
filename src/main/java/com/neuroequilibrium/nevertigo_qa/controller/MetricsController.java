package com.neuroequilibrium.nevertigo_qa.controller;

import com.neuroequilibrium.nevertigo_qa.repository.RegHWRepository;
import com.neuroequilibrium.nevertigo_qa.repository.PrescriptionRepository;
import com.neuroequilibrium.nevertigo_qa.repository.PatientRepository;
import com.neuroequilibrium.nevertigo_qa.repository.PositionalDataRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
@RestController
@RequestMapping("/metrics")
public class MetricsController {

    private final PatientRepository patientRepository;
    private final PositionalDataRepository positionalDataRepository;
    private final RegHWRepository regHWRepository;
    private final PrescriptionRepository prescriptionRepository;

    public MetricsController(PatientRepository patientRepository,
                              PositionalDataRepository positionalDataRepository,
                              RegHWRepository regHWRepository,
                              PrescriptionRepository prescriptionRepository) {
        this.patientRepository = patientRepository;
        this.positionalDataRepository = positionalDataRepository;
        this.regHWRepository = regHWRepository;
        this.prescriptionRepository = prescriptionRepository;
    }

    @GetMapping("/visits")
    public Map<String, Object> getVisits(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        long count = patientRepository.countVisits(start, end);
        return Map.of("metric", "visits", "start", start, "end", end, "value", count);
    }

    @GetMapping("/new-patients")
    public Map<String, Object> getNewPatients(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        long count = patientRepository.countNewPatients(start, end);

        return Map.of(
                "metric", "new_patients",
                "start", start,
                "end", end,
                "value", count
        );
    }

    @GetMapping("/followup-patients")
    public Map<String, Object> getFollowupPatients(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        long count = patientRepository.countFollowupPatients(start, end);

        return Map.of(
                "metric", "followup_patients",
                "start", start,
                "end", end,
                "value", count
        );
    }

    @GetMapping("/bppv-patients")
    public Map<String, Object> getBppvPatients(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        long count = patientRepository.countBppvPatients(start, end);

        return Map.of(
                "metric", "bppv_patients",
                "start", start,
                "end", end,
                "value", count
        );
    }

    @GetMapping("/bppv-percentage")
    public Map<String, Object> getBppvPercentage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        double percentage = patientRepository.countBppvPercentage(start, end);

        return Map.of(
                "metric", "bppv_percentage",
                "start", start,
                "end", end,
                "value", percentage
        );
    }

    @GetMapping("/net-neuro-revenue")
    public Map<String, Object> getNetNeuroRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        long revenue = patientRepository.sumNetNeuroRevenue(start, end);

        return Map.of(
                "metric", "net_neuro_revenue",
                "start", start,
                "end", end,
                "value", revenue
        );
    }

    @GetMapping("/analysis-amount")
    public Map<String, Object> getAnalysisAmount(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        double amount = patientRepository.sumAnalysisAmount(start, end);

        return Map.of(
                "metric", "analysis_amount",
                "start", start,
                "end", end,
                "value", amount
        );
    }

    @GetMapping("/dva-report-received")
    public Map<String, Object> getDvaReportReceived(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        long count = patientRepository.countDvaReportReceived(start, end);

        return Map.of(
                "metric", "dva_report_received",
                "start", start,
                "end", end,
                "value", count
        );
    }

    @GetMapping("/medicine-line")
    public Map<String, Object> getMedicineLines(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        long count = patientRepository.countMedicineLines(start, end);

        return Map.of(
                "metric", "medicine_line",
                "start", start,
                "end", end,
                "value", count
        );
    }

    @GetMapping("/max-abs-positional-spv")
    public Double maxAbsPositionalSpv(
            @RequestParam String start,
            @RequestParam String end) {
        return patientRepository.maxAbsPositionalSpv(
                LocalDate.parse(start),
                LocalDate.parse(end)
        );
    }

    @GetMapping("/low-spv-no-torsion-visits")
    public Map<String, Object> getLowSpvNoTorsion(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        long count = positionalDataRepository.countLowSpvNoTorsion(start, end);

        return Map.of(
                "metric", "low_spv_no_torsion_visits",
                "start", start,
                "end", end,
                "value", count
        );
    }

    @GetMapping("/patients-with-visit")
    public Map<String, Object> getPatientsWithVisit(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        long count = patientRepository.countDistinctPatients(start, end);
        return Map.of("metric", "patients_with_visit", "start", start, "end", end, "value", count);
    }

    @GetMapping("/revenue-by-scheme")
    public Map<String, Object> getRevenueByScheme(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        long cghs = patientRepository.sumCghsRevenue(start, end);
        long cash = patientRepository.sumCashRevenue(start, end);

        return Map.of("metric", "revenue_by_scheme", "start", start, "end", end,
                      "cghs", cghs, "cash", cash);
    }

    @GetMapping("/revenue-by-staff")
    public Map<String, Object> getRevenueByStaff(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        long anita = patientRepository.sumRevenueByHandler(start, end, "Anita Rao");
        long rahul = patientRepository.sumRevenueByHandler(start, end, "Rahul Mehta");
        long riya = patientRepository.sumRevenueByTechnician(start, end, "Riya", "Shah");
        long arjun = patientRepository.sumRevenueByTechnician(start, end, "Arjun", "Nair");
        long meera = patientRepository.sumRevenueByTechnician(start, end, "Meera", "Das");

        return Map.of("metric", "revenue_by_staff", "start", start, "end", end,
                      "anita_rao", anita, "rahul_mehta", rahul,
                      "riya_shah", riya, "arjun_nair", arjun, "meera_das", meera);
    }

    @GetMapping("/mumbai-dva-versions")
    public Map<String, Object> getMumbaiDvaVersions() {
        String versions = regHWRepository.getDvaVersions("VertigoClinic001");
        return Map.of("metric", "mumbai_dva_versions", "value", versions);
    }

    @GetMapping("/omar-may-details")
    public Map<String, Object> getOmarMayDetails() {
        LocalDate date = LocalDate.of(2026, 5, 22);
        String diagnosis = patientRepository.getDiagnosis("P1006", date);
        String doctor = patientRepository.getDoctor("P1006", date);
        return Map.of("metric", "omar_may_details", "diagnosis", diagnosis, "doctor", doctor);
    }

    @GetMapping("/medicine-company")
    public Map<String, Object> getMedicineCompany(@RequestParam String patientId,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate apDate,
                                                   @RequestParam String medName) {
        String company = prescriptionRepository.getMedicineCompany(patientId, apDate, medName);
        return Map.of("metric", "medicine_company", "medicine", medName, "company", company);
    }

    @GetMapping("/clinic-count")
public Map<String, Object> clinicCount() {

    long value = patientRepository.countClinics();

    return Map.of(
            "metric", "clinic_count",
            "value", value
    );
}

@GetMapping("/visits-by-clinic")
public Map<String, Object> getVisitsByClinic(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

    List<Object[]> results = patientRepository.countVisitsByClinic(start, end);

    Map<String, Object> response = new LinkedHashMap<>();

    for (Object[] row : results) {
        response.put((String) row[0], row[1]);
    }

    return response;
}

@GetMapping("/revenue-by-clinic")
public Map<String, Object> getRevenueByClinic(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

    List<Object[]> results = patientRepository.sumRevenueByClinic(start, end);

    Map<String, Object> response = new LinkedHashMap<>();

    for (Object[] row : results) {
        response.put((String) row[0], row[1]);
    }

    return response;
}
@GetMapping("/revenue-by-service")
public Map<String, Object> getRevenueByService(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

    List<Object[]> results = patientRepository.sumRevenueByService(start, end);

    Map<String, Object> response = new LinkedHashMap<>();

    for (Object[] row : results) {
        response.put((String) row[0], row[1]);
    }

    return response;
}
}