package com.farmacia.scheduler.service;

import com.farmacia.scheduler.holiday.HolidayCalendar;
import com.farmacia.scheduler.holiday.HolidayDate;
import com.farmacia.scheduler.model.PublicHoliday;
import com.farmacia.scheduler.repository.HolidayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class HolidayGeneratorService {

    private final HolidayRepository holidayRepository;

    public HolidayGeneratorService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    /**
     * Ensures the given year has holiday rows in the database.
     * If the year already has any rows (from Flyway seed or prior generation),
     * does nothing — user edits are preserved.
     */
    @Transactional
    public void ensureGenerated(int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        if (holidayRepository.existsByDateBetween(start, end)) {
            return;
        }

        for (HolidayDate h : HolidayCalendar.forYear(year)) {
            PublicHoliday entity = new PublicHoliday();
            entity.setDate(h.date());
            entity.setName(h.name());
            holidayRepository.save(entity);
        }
    }
}