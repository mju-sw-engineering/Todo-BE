package com.todo.domain.availability.entity;

import com.todo.domain.user.entity.User;
import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "availability_slots",
        uniqueConstraints = @UniqueConstraint(columnNames = {"poll_id", "user_id", "date", "hour"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailabilitySlot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poll_id", nullable = false)
    private AvailabilityPoll poll;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private int hour;

    public static AvailabilitySlot create(AvailabilityPoll poll, User user, LocalDate date, int hour) {
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.poll = poll;
        slot.user = user;
        slot.date = date;
        slot.hour = hour;
        return slot;
    }
}
