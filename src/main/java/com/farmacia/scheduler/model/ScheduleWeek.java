package com.farmacia.scheduler.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_week", uniqueConstraints = @UniqueConstraint(columnNames = {"iso_year", "iso_week"}))
@Getter @Setter @NoArgsConstructor
public class ScheduleWeek {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "iso_year", nullable = false)
    private Integer isoYear;

    @Column(name = "iso_week", nullable = false)
    private Integer isoWeek;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WeekStatus status;

    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
}