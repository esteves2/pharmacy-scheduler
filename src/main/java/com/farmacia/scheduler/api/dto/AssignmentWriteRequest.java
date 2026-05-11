package com.farmacia.scheduler.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor
public class AssignmentWriteRequest {
    private Long id;
    private Long employeeId;
    private LocalDate date;
    private String startTime;
    private String endTime;
    private String breakStart;
    private String breakEnd;
}
