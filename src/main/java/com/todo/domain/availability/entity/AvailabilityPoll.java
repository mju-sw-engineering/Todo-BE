package com.todo.domain.availability.entity;

import com.todo.domain.team.entity.Team;
import com.todo.domain.user.entity.User;
import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "availability_polls")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AvailabilityPoll extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private int startHour;

    @Column(nullable = false)
    private int endHour;

    @OneToMany(mappedBy = "poll", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AvailabilityPollDate> dates = new ArrayList<>();

    public static AvailabilityPoll create(Team team, User createdBy, String title, int startHour, int endHour) {
        AvailabilityPoll poll = new AvailabilityPoll();
        poll.team = team;
        poll.createdBy = createdBy;
        poll.title = title;
        poll.startHour = startHour;
        poll.endHour = endHour;
        return poll;
    }
}
