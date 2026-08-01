package com.todo.domain.availability.service;

import com.todo.domain.availability.dto.request.CreateAvailabilityPollRequest;
import com.todo.domain.availability.dto.request.SlotItem;
import com.todo.domain.availability.dto.request.SubmitAvailabilityRequest;
import com.todo.domain.availability.dto.response.AvailabilityPollListResponse;
import com.todo.domain.availability.dto.response.AvailabilitySlotItem;
import com.todo.domain.availability.dto.response.AvailabilitySummaryResponse;
import com.todo.domain.availability.dto.response.BestSlotResponse;
import com.todo.domain.availability.dto.response.HeatmapSlotResponse;
import com.todo.domain.availability.entity.AvailabilityPoll;
import com.todo.domain.availability.entity.AvailabilityPollDate;
import com.todo.domain.availability.entity.AvailabilitySlot;
import com.todo.domain.availability.repository.AvailabilityPollRepository;
import com.todo.domain.availability.repository.AvailabilitySlotRepository;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailabilityService {

    private final AvailabilityPollRepository pollRepository;
    private final AvailabilitySlotRepository slotRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    public List<AvailabilityPollListResponse> getPolls(Long teamId, String loginId) {
        User user = findUser(loginId);
        validateTeamMember(teamId, user.getId());

        long totalMemberCount = teamMemberRepository.countByTeamId(teamId);
        List<AvailabilityPoll> polls = pollRepository.findByTeamIdWithDates(teamId);

        if (polls.isEmpty()) {
            return List.of();
        }

        List<Long> pollIds = polls.stream().map(AvailabilityPoll::getId).toList();

        Map<Long, Long> respondedCountMap = new HashMap<>();
        slotRepository.countRespondedUsersByPollIds(pollIds)
                .forEach(row -> respondedCountMap.put((Long) row[0], (Long) row[1]));

        Set<Long> myRespondedPollIds = new HashSet<>(
                slotRepository.findPollIdsRespondedByUser(pollIds, user.getId()));

        return polls.stream()
                .map(poll -> {
                    long respondedCount = respondedCountMap.getOrDefault(poll.getId(), 0L);
                    boolean myResponded = myRespondedPollIds.contains(poll.getId());
                    return AvailabilityPollListResponse.of(poll, totalMemberCount, respondedCount, myResponded);
                })
                .toList();
    }

    @Transactional
    public void createPoll(Long teamId, String loginId, CreateAvailabilityPollRequest request) {
        if (request.startHour() >= request.endHour()) {
            throw new BusinessException("시작 시간은 종료 시간보다 작아야 합니다.", HttpStatus.BAD_REQUEST);
        }

        User user = findUser(loginId);
        validateTeamMember(teamId, user.getId());
        Team team = teamRepository.getReferenceById(teamId);

        AvailabilityPoll poll = AvailabilityPoll.create(team, user, request.title(), request.startHour(), request.endHour());

        Set<LocalDate> uniqueDates = request.dateOptions().stream().collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        uniqueDates.forEach(date -> poll.getDates().add(AvailabilityPollDate.create(poll, date)));

        pollRepository.save(poll);
    }

    @Transactional
    public void submitResponse(Long pollId, String loginId, SubmitAvailabilityRequest request) {
        User user = findUser(loginId);
        AvailabilityPoll poll = findPollWithDates(pollId);
        validateTeamMember(poll.getTeam().getId(), user.getId());

        Set<LocalDate> validDates = poll.getDates().stream()
                .map(AvailabilityPollDate::getDate)
                .collect(Collectors.toSet());

        for (SlotItem slot : request.slots()) {
            if (!validDates.contains(slot.date())) {
                throw new BusinessException("투표에 포함되지 않은 날짜입니다: " + slot.date(), HttpStatus.BAD_REQUEST);
            }
            if (slot.hour() < poll.getStartHour() || slot.hour() >= poll.getEndHour()) {
                throw new BusinessException("유효하지 않은 시간입니다: " + slot.hour(), HttpStatus.BAD_REQUEST);
            }
        }

        slotRepository.deleteByPollIdAndUserId(pollId, user.getId());

        List<AvailabilitySlot> newSlots = request.slots().stream()
                .map(s -> AvailabilitySlot.create(poll, user, s.date(), s.hour()))
                .toList();
        slotRepository.saveAll(newSlots);
    }

    public AvailabilitySummaryResponse getSummary(Long pollId, String loginId) {
        User user = findUser(loginId);
        AvailabilityPoll poll = findPollWithDates(pollId);
        validateTeamMember(poll.getTeam().getId(), user.getId());

        long totalMemberCount = teamMemberRepository.countByTeamId(poll.getTeam().getId());
        long respondedCount = slotRepository.countRespondedUsers(pollId);

        List<LocalDate> dateOptions = poll.getDates().stream()
                .map(AvailabilityPollDate::getDate)
                .sorted()
                .toList();

        List<AvailabilitySlot> mySlotEntities = slotRepository.findByPollIdAndUserId(pollId, user.getId());
        List<AvailabilitySlotItem> mySlots = mySlotEntities.stream()
                .map(s -> new AvailabilitySlotItem(s.getDate(), s.getHour()))
                .toList();

        List<AvailabilitySlot> allSlots = slotRepository.findByPollIdWithUser(pollId);

        // (date, hour) → 닉네임 목록
        record SlotKey(LocalDate date, int hour) {}
        Map<SlotKey, List<String>> heatmapMap = new LinkedHashMap<>();
        for (AvailabilitySlot slot : allSlots) {
            SlotKey key = new SlotKey(slot.getDate(), slot.getHour());
            heatmapMap.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(slot.getUser().getNickname());
        }

        List<HeatmapSlotResponse> heatmap = heatmapMap.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<SlotKey, List<String>> e) -> e.getKey().date())
                        .thenComparingInt(e -> e.getKey().hour()))
                .map(e -> new HeatmapSlotResponse(
                        e.getKey().date(),
                        e.getKey().hour(),
                        e.getValue().size(),
                        e.getValue()
                ))
                .toList();

        BestSlotResponse bestSlot = heatmap.stream()
                .max(Comparator.comparingInt(HeatmapSlotResponse::count)
                        .thenComparing(Comparator.comparing(HeatmapSlotResponse::date).reversed())
                        .thenComparing(Comparator.comparingInt(HeatmapSlotResponse::hour).reversed()))
                .map(h -> new BestSlotResponse(h.date(), h.hour(), h.count(), h.members()))
                .orElse(null);

        return new AvailabilitySummaryResponse(
                pollId,
                poll.getTitle(),
                dateOptions,
                poll.getStartHour(),
                poll.getEndHour(),
                totalMemberCount,
                respondedCount,
                totalMemberCount > 0 && respondedCount >= totalMemberCount,
                mySlots,
                heatmap,
                bestSlot
        );
    }

    private User findUser(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }

    private AvailabilityPoll findPollWithDates(Long pollId) {
        return pollRepository.findByIdWithDates(pollId)
                .orElseThrow(() -> new BusinessException("투표를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));
    }

    private void validateTeamMember(Long teamId, Long userId) {
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw new BusinessException("팀원이 아닙니다.", HttpStatus.FORBIDDEN);
        }
    }
}
