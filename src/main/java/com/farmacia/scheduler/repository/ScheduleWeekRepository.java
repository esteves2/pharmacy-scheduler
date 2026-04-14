package com.farmacia.scheduler.repository;

import com.farmacia.scheduler.model.ScheduleWeek;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ScheduleWeekRepository extends JpaRepository<ScheduleWeek, Long> {

    Optional<ScheduleWeek> findByIsoYearAndIsoWeek(Integer isoYear, Integer isoWeek);
}