
package com.neuroequilibrium.nevertigo_qa.controller;

import com.neuroequilibrium.nevertigo_qa.model.ClinicInfo;
import com.neuroequilibrium.nevertigo_qa.repository.ClinicInfoRepository;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/clinics")
public class ClinicController {

    private final ClinicInfoRepository clinicInfoRepository;

    public ClinicController(ClinicInfoRepository clinicInfoRepository) {
        this.clinicInfoRepository = clinicInfoRepository;
    }

    @GetMapping
    public List<ClinicInfo> getAllClinics() {
        return clinicInfoRepository.findAll();
    }
}