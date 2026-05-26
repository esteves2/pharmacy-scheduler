package com.farmacia.scheduler.api.dto;

import java.util.List;

public record WeekWriteRequest(List<AssignmentWriteRequest> assignments) {}
