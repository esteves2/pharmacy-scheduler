package com.farmacia.scheduler.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter @Setter @NoArgsConstructor
public class WeekWriteRequest {
    private List<AssignmentWriteRequest> assignments;
}
