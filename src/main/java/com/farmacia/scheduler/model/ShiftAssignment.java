package com.farmacia.scheduler.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "shift_assignment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"schedule_week_id", "employee_id", "date"}))
@Getter @Setter @NoArgsConstructor
public class ShiftAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_week_id", nullable = false)
    private Long scheduleWeekId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "start_time", nullable = false)
    private String startTime;

    @Column(name = "end_time", nullable = false)
    private String endTime;

    @Column(name = "break_start")
    private String breakStart;

    @Column(name = "break_end")
    private String breakEnd;
}