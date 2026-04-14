package com.farmacia.scheduler.repository;

import com.farmacia.scheduler.model.PublicHoliday;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface HolidayRepository extends JpaRepository<@NonNull PublicHoliday,@NonNull Long> {

    @Query("SELECT holiday FROM PublicHoliday holiday WHERE holiday.date >= :start AND holiday.date <= :end ORDER BY holiday.date")
    List<PublicHoliday> findByYear(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT holiday FROM PublicHoliday holiday WHERE holiday.date >= :from AND holiday.date <= :to ORDER BY holiday.date")
    List<PublicHoliday> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}