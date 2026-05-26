package com.farmacia.scheduler.api;

import com.farmacia.scheduler.api.dto.HolidayRequest;
import com.farmacia.scheduler.api.dto.HolidayResponse;
import com.farmacia.scheduler.model.PublicHoliday;
import com.farmacia.scheduler.repository.HolidayRepository;
import com.farmacia.scheduler.service.HolidayGeneratorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/holidays")
public class HolidayController {

    private final HolidayRepository holidayRepository;
    private final HolidayGeneratorService holidayGeneratorService;

    public HolidayController(HolidayRepository holidayRepository, HolidayGeneratorService holidayGeneratorService) {
        this.holidayRepository = holidayRepository;
        this.holidayGeneratorService = holidayGeneratorService;
    }

    @GetMapping
    public List<HolidayResponse> list(@RequestParam int year) {
        holidayGeneratorService.ensureGenerated(year);
        return holidayRepository
                .findBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    public ResponseEntity<HolidayResponse> create(@RequestBody HolidayRequest request) {
        PublicHoliday holiday = fromRequest(new PublicHoliday(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(holidayRepository.save(holiday)));
    }

    @PutMapping("/{id}")
    public HolidayResponse update(@PathVariable Long id, @RequestBody HolidayRequest request) {
        PublicHoliday holiday = holidayRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Holiday %d not found".formatted(id)));
        return toResponse(holidayRepository.save(fromRequest(holiday, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!holidayRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Holiday %d not found".formatted(id));
        }
        holidayRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private PublicHoliday fromRequest(PublicHoliday holiday, HolidayRequest request) {
        holiday.setDate(request.date());
        holiday.setName(request.name());
        return holiday;
    }

    private HolidayResponse toResponse(PublicHoliday h) {
        return new HolidayResponse(h.getId(), h.getDate(), h.getName());
    }
}
