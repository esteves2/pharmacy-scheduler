package com.farmacia.scheduler.api;

import com.farmacia.scheduler.api.dto.ValidationMessageResponse;
import com.farmacia.scheduler.service.exception.ScheduleAlreadyExistsException;
import com.farmacia.scheduler.service.exception.ScheduleAlreadyPublishedException;
import com.farmacia.scheduler.service.exception.ScheduleHasValidationErrorsException;
import com.farmacia.scheduler.service.exception.ScheduleNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ScheduleNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ScheduleNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(ScheduleAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleAlreadyExists(ScheduleAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(ScheduleAlreadyPublishedException.class)
    public ResponseEntity<Map<String, String>> handleAlreadyPublished(ScheduleAlreadyPublishedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(ScheduleHasValidationErrorsException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(ScheduleHasValidationErrorsException ex) {
        List<ValidationMessageResponse> errors = ex.getErrors().stream()
                .map(vm -> new ValidationMessageResponse(
                        vm.getSeverity().name(), vm.getDate(), vm.getHour(), vm.getMessage()))
                .collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", ex.getMessage());
        body.put("errors", errors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }
}
