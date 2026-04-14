package com.farmacia.scheduler.repository;

import com.farmacia.scheduler.model.ShiftAssignment;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface ShiftAssignmentRepository extends JpaRepository<@NonNull ShiftAssignment, @NonNull Long> {

    List<ShiftAssignment> findByScheduleWeekId(Long scheduleWeekId);

    void deleteByScheduleWeekId(Long scheduleWeekId);

    @Query("SELECT assignment FROM ShiftAssignment assignment WHERE assignment.date >= :from AND assignment.date <= :to")
    List<ShiftAssignment> findByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);
}