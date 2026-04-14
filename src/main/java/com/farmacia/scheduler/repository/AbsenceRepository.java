package com.farmacia.scheduler.repository;

import com.farmacia.scheduler.model.EmployeeAbsence;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface AbsenceRepository extends JpaRepository< @NonNull EmployeeAbsence, @NonNull Long> {

    @Query("SELECT absence FROM EmployeeAbsence absence WHERE absence.startDate <= :to AND absence.endDate >= :from")
    List<EmployeeAbsence> findOverlapping(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<EmployeeAbsence> findByEmployeeId(Long employeeId);
}