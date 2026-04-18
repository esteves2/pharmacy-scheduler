package com.farmacia.scheduler.holiday;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class HolidayCalendarTest {

    @ParameterizedTest
    @CsvSource({
            "2024, 3, 31",
            "2025, 4, 20",
            "2026, 4,  5",
            "2027, 3, 28"
    })
    void computeEaster_returnsCorrectDate(int year, int month, int day) {
        assertThat(HolidayCalendar.computeEaster(year))
                .isEqualTo(LocalDate.of(year, month, day));
    }

    @Test
    void forYear_2026_returns18SortedNoDuplicates() {
        List<HolidayDate> holidays = HolidayCalendar.forYear(2026);

        assertThat(holidays).hasSize(18);

        for (int i = 1; i < holidays.size(); i++) {
            assertThat(holidays.get(i).date())
                    .isAfter(holidays.get(i - 1).date());
        }

        Set<LocalDate> dates = holidays.stream().map(HolidayDate::date).collect(Collectors.toSet());
        assertThat(dates).hasSize(18);
    }

    @Test
    void forYear_2026_containsRegionalAndMunicipalHolidays() {
        List<HolidayDate> holidays = HolidayCalendar.forYear(2026);

        assertThat(holidays).anySatisfy(h -> {
            assertThat(h.date()).isEqualTo(LocalDate.of(2026, 1, 15));
            assertThat(h.name()).isEqualTo("Santo Amaro");
        });
        assertThat(holidays).anySatisfy(h -> {
            assertThat(h.date()).isEqualTo(LocalDate.of(2026, 4, 2));
            assertThat(h.name()).isEqualTo("Dia da Autonomia");
        });
        assertThat(holidays).anySatisfy(h -> {
            assertThat(h.date()).isEqualTo(LocalDate.of(2026, 7, 1));
            assertThat(h.name()).isEqualTo("Dia da Região");
        });
        assertThat(holidays).anySatisfy(h -> {
            assertThat(h.date()).isEqualTo(LocalDate.of(2026, 12, 26));
            assertThat(h.name()).isEqualTo("Primeira Oitava");
        });
    }

    @Test
    void forYear_2021_easterDerivedBeatsRegionalOnApril2() {
        // Easter 2021 = Apr 4 → Sexta-feira Santa = Apr 2, same as Dia da Autonomia (regional).
        // Easter-derived has higher precedence than regional → 17 entries, name = Sexta-feira Santa.
        List<HolidayDate> holidays = HolidayCalendar.forYear(2021);

        assertThat(holidays).hasSize(17);

        HolidayDate april2 = holidays.stream()
                .filter(h -> h.date().equals(LocalDate.of(2021, 4, 2)))
                .findFirst()
                .orElseThrow();
        assertThat(april2.name()).isEqualTo("Sexta-feira Santa");
    }

    @Test
    void forYear_2038_nationalBeatsEasterDerivedOnApril25() {
        // Easter 2038 = Apr 25 = Dia da Liberdade (national).
        // National has highest precedence → 17 entries, name = Dia da Liberdade.
        List<HolidayDate> holidays = HolidayCalendar.forYear(2038);

        assertThat(holidays).hasSize(17);

        HolidayDate april25 = holidays.stream()
                .filter(h -> h.date().equals(LocalDate.of(2038, 4, 25)))
                .findFirst()
                .orElseThrow();
        assertThat(april25.name()).isEqualTo("Dia da Liberdade");
    }
}
