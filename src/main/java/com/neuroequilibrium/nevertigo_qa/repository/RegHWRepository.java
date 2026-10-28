package com.neuroequilibrium.nevertigo_qa.repository;

import com.neuroequilibrium.nevertigo_qa.model.RegHW;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;



public interface RegHWRepository extends JpaRepository<RegHW, RegHW.RegHWId> {

    @Query(value = "SELECT GROUP_CONCAT(CONCAT(rh.UID, '=', rh.DVA) ORDER BY rh.UID SEPARATOR ', ') " +
                    "FROM RegHW rh WHERE rh.ClinicID = :clinicId",
           nativeQuery = true)
    String getDvaVersions(@Param("clinicId") String clinicId);
}