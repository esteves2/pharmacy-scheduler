package com.farmacia.scheduler.repository;

import com.farmacia.scheduler.model.ShiftAssignment;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface ShiftAssignmentRepository extends JpaRepository<@NonNull ShiftAssignment, @NonNull Long> {

    List<ShiftAssignment> findByScheduleWeekId(Long scheduleWeekId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ShiftAssignment s WHERE s.scheduleWeekId = :weekId")
    void deleteByScheduleWeekId(@Param("weekId") Long weekId);

    @Query("SELECT assignment FROM ShiftAssignment assignment WHERE assignment.date >= :from AND assignment.date <= :to")
    List<ShiftAssignment> findByDateRange(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<ShiftAssignment> findByScheduleWeekIdAndDateBefore(Long scheduleWeekId, LocalDate date);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM ShiftAssignment s WHERE s.scheduleWeekId = :weekId AND s.date >= :from")
    void deleteByScheduleWeekIdAndDateFrom(@Param("weekId") Long weekId, @Param("from") LocalDate from);
}