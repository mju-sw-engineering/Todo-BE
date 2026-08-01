package com.todo.domain.availability.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "availability_poll_dates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailabilityPollDate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id", nullable = false)
    private AvailabilityPoll poll;

    @Column(nullable = false)
    private LocalDate date;

    public static AvailabilityPollDate create(AvailabilityPoll poll, LocalDate date) {
        AvailabilityPollDate option = new AvailabilityPollDate();
        option.poll = poll;
        option.date = date;
        return option;
    }
}
