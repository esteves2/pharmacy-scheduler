package com.farmacia.scheduler.holiday;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Pure calculator of Portuguese public holidays observed at Farmácia Esperança
 * (Santa Cruz, Madeira). No Spring, no JPA, no I/O.
 *
 * <p>Per year: 14 fixed dates + 4 Easter-derived dates = 18 rows, minus any
 * collisions (roughly once per decade). Precedence on collision:
 * national fixed &gt; Easter-derived &gt; regional fixed &gt; municipal fixed.
 */
public final class HolidayCalendar {

    private HolidayCalendar() {}

    private static final List<FixedHoliday> NATIONAL = List.of(
            new FixedHoliday(MonthDay.of(1, 1),   "Ano Novo"),
            new FixedHoliday(MonthDay.of(4, 25),  "Dia da Liberdade"),
            new FixedHoliday(MonthDay.of(5, 1),   "Dia do Trabalhador"),
            new FixedHoliday(MonthDay.of(6, 10),  "Dia de Portugal"),
            new FixedHoliday(MonthDay.of(8, 15),  "Assunção de Nossa Senhora"),
            new FixedHoliday(MonthDay.of(10, 5),  "Implantação da República"),
            new FixedHoliday(MonthDay.of(11, 1),  "Todos os Santos"),
            new FixedHoliday(MonthDay.of(12, 1),  "Restauração da Independência"),
            new FixedHoliday(MonthDay.of(12, 8),  "Imaculada Conceição"),
            new FixedHoliday(MonthDay.of(12, 25), "Natal")
    );

    private static final List<FixedHoliday> REGIONAL = List.of(
            new FixedHoliday(MonthDay.of(4, 2),   "Dia da Autonomia"),
            new FixedHoliday(MonthDay.of(7, 1),   "Dia da Região"),
            new FixedHoliday(MonthDay.of(12, 26), "Primeira Oitava")
    );

    private static final List<FixedHoliday> MUNICIPAL = List.of(
            new FixedHoliday(MonthDay.of(1, 15), "Santo Amaro")
    );

    public static List<HolidayDate> forYear(int year) {
        LocalDate easter = computeEaster(year);

        LinkedHashMap<LocalDate, HolidayDate> byDate = new LinkedHashMap<>();

        for (FixedHoliday f : NATIONAL) {
            LocalDate d = f.monthDay().atYear(year);
            byDate.putIfAbsent(d, new HolidayDate(d, f.name()));
        }

        putIfAbsent(byDate, easter.minusDays(47), "Carnaval");
        putIfAbsent(byDate, easter.minusDays(2),  "Sexta-feira Santa");
        putIfAbsent(byDate, easter,               "Domingo de Páscoa");
        putIfAbsent(byDate, easter.plusDays(60),  "Corpo de Deus");

        for (FixedHoliday f : REGIONAL) {
            LocalDate d = f.monthDay().atYear(year);
            byDate.putIfAbsent(d, new HolidayDate(d, f.name()));
        }

        for (FixedHoliday f : MUNICIPAL) {
            LocalDate d = f.monthDay().atYear(year);
            byDate.putIfAbsent(d, new HolidayDate(d, f.name()));
        }

        List<HolidayDate> result = new ArrayList<>(byDate.values());
        result.sort(Comparator.comparing(HolidayDate::date));
        return result;
    }

    private static void putIfAbsent(LinkedHashMap<LocalDate, HolidayDate> map,
                                    LocalDate date, String name) {
        map.putIfAbsent(date, new HolidayDate(date, name));
    }

    /**
     * Computes Western (Gregorian) Easter Sunday via the Anonymous Gregorian
     * algorithm (Meeus/Jones/Butcher). Pure arithmetic, no table lookup.
     */
    static LocalDate computeEaster(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (((h + l) - (7 * m)) + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }

    private record FixedHoliday(MonthDay monthDay, String name) {}
}