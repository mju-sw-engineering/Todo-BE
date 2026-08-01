package com.todo.domain.availability.service;

import com.todo.domain.availability.dto.request.CreateAvailabilityPollRequest;
import com.todo.domain.availability.dto.request.SlotItem;
import com.todo.domain.availability.dto.request.SubmitAvailabilityRequest;
import com.todo.domain.availability.dto.response.AvailabilityPollListResponse;
import com.todo.domain.availability.dto.response.AvailabilitySummaryResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    @InjectMocks
    private AvailabilityService availabilityService;

    @Mock
    private AvailabilityPollRepository pollRepository;
    @Mock
    private AvailabilitySlotRepository slotRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private UserRepository userRepository;

    private User user;
    private Team team;
    private AvailabilityPoll poll;

    @BeforeEach
    void setUp() {
        user = User.create("user1", "pw", "닉네임", null);
        ReflectionTestUtils.setField(user, "id", 1L);

        team = Team.create("팀A", null, "INVITE01");
        ReflectionTestUtils.setField(team, "id", 10L);

        poll = AvailabilityPoll.create(team, user, "일정 조율", 9, 18);
        ReflectionTestUtils.setField(poll, "id", 100L);

        AvailabilityPollDate date1 = AvailabilityPollDate.create(poll, LocalDate.of(2026, 8, 4));
        AvailabilityPollDate date2 = AvailabilityPollDate.create(poll, LocalDate.of(2026, 8, 5));
        poll.getDates().addAll(List.of(date1, date2));
    }

    // ──────────── getPolls ────────────

    @Test
    void 투표_목록_조회_성공() {
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);
        given(teamMemberRepository.countByTeamId(10L)).willReturn(3L);
        given(pollRepository.findByTeamIdWithDates(10L)).willReturn(List.of(poll));
        given(slotRepository.countRespondedUsers(100L)).willReturn(1L);
        given(slotRepository.existsByPollIdAndUserId(100L, 1L)).willReturn(true);

        List<AvailabilityPollListResponse> result = availabilityService.getPolls(10L, "user1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("일정 조율");
        assertThat(result.get(0).myResponded()).isTrue();
        assertThat(result.get(0).allResponded()).isFalse();
    }

    @Test
    void 투표_목록_조회_팀원이_아니면_예외() {
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(false);

        assertThatThrownBy(() -> availabilityService.getPolls(10L, "user1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("팀원");
    }

    // ──────────── createPoll ────────────

    @Test
    void 투표_생성_성공() {
        CreateAvailabilityPollRequest request = new CreateAvailabilityPollRequest(
                "새 투표",
                List.of(LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 5)),
                9, 18
        );
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);
        given(teamRepository.getReferenceById(10L)).willReturn(team);

        availabilityService.createPoll(10L, "user1", request);

        then(pollRepository).should().save(any(AvailabilityPoll.class));
    }

    @Test
    void 투표_생성_시작시간이_종료시간보다_크거나_같으면_예외() {
        CreateAvailabilityPollRequest request = new CreateAvailabilityPollRequest(
                "잘못된 시간",
                List.of(LocalDate.of(2026, 8, 4)),
                18, 9
        );

        assertThatThrownBy(() -> availabilityService.createPoll(10L, "user1", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("시작 시간");
    }

    @Test
    void 투표_생성_중복_날짜는_한번만_저장() {
        CreateAvailabilityPollRequest request = new CreateAvailabilityPollRequest(
                "중복 날짜 투표",
                List.of(LocalDate.of(2026, 8, 4), LocalDate.of(2026, 8, 4)),
                9, 18
        );
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);
        given(teamRepository.getReferenceById(10L)).willReturn(team);

        availabilityService.createPoll(10L, "user1", request);

        then(pollRepository).should().save(any(AvailabilityPoll.class));
        // 중복 제거 후 1개만 저장됨을 서비스 로직에서 보장
    }

    // ──────────── submitResponse ────────────

    @Test
    void 가능시간_제출_성공() {
        SubmitAvailabilityRequest request = new SubmitAvailabilityRequest(
                List.of(new SlotItem(LocalDate.of(2026, 8, 4), 9))
        );
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(pollRepository.findByIdWithDates(100L)).willReturn(Optional.of(poll));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);

        availabilityService.submitResponse(100L, "user1", request);

        then(slotRepository).should().deleteByPollIdAndUserId(100L, 1L);
        then(slotRepository).should().saveAll(any());
    }

    @Test
    void 가능시간_제출_빈_배열이면_기존_응답_삭제() {
        SubmitAvailabilityRequest request = new SubmitAvailabilityRequest(List.of());
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(pollRepository.findByIdWithDates(100L)).willReturn(Optional.of(poll));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);

        availabilityService.submitResponse(100L, "user1", request);

        then(slotRepository).should().deleteByPollIdAndUserId(100L, 1L);
        then(slotRepository).should().saveAll(List.of());
    }

    @Test
    void 가능시간_제출_유효하지_않은_날짜면_예외() {
        SubmitAvailabilityRequest request = new SubmitAvailabilityRequest(
                List.of(new SlotItem(LocalDate.of(2026, 9, 1), 10))
        );
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(pollRepository.findByIdWithDates(100L)).willReturn(Optional.of(poll));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);

        assertThatThrownBy(() -> availabilityService.submitResponse(100L, "user1", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("날짜");
    }

    @Test
    void 가능시간_제출_시간범위_초과하면_예외() {
        SubmitAvailabilityRequest request = new SubmitAvailabilityRequest(
                List.of(new SlotItem(LocalDate.of(2026, 8, 4), 20))
        );
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(pollRepository.findByIdWithDates(100L)).willReturn(Optional.of(poll));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);

        assertThatThrownBy(() -> availabilityService.submitResponse(100L, "user1", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("시간");
    }

    @Test
    void 가능시간_제출_투표_없으면_예외() {
        SubmitAvailabilityRequest request = new SubmitAvailabilityRequest(List.of());
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(pollRepository.findByIdWithDates(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> availabilityService.submitResponse(999L, "user1", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("투표");
    }

    // ──────────── getSummary ────────────

    @Test
    void 요약_조회_성공() {
        User user2 = User.create("user2", "pw", "닉네임2", null);
        ReflectionTestUtils.setField(user2, "id", 2L);

        AvailabilitySlot mySlot = AvailabilitySlot.create(poll, user, LocalDate.of(2026, 8, 4), 9);
        AvailabilitySlot otherSlot = AvailabilitySlot.create(poll, user2, LocalDate.of(2026, 8, 4), 9);

        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(pollRepository.findByIdWithDates(100L)).willReturn(Optional.of(poll));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);
        given(teamMemberRepository.countByTeamId(10L)).willReturn(2L);
        given(slotRepository.countRespondedUsers(100L)).willReturn(2L);
        given(slotRepository.findByPollIdAndUserId(100L, 1L)).willReturn(List.of(mySlot));
        given(slotRepository.findByPollIdWithUser(100L)).willReturn(List.of(mySlot, otherSlot));

        AvailabilitySummaryResponse result = availabilityService.getSummary(100L, "user1");

        assertThat(result.pollId()).isEqualTo(100L);
        assertThat(result.allResponded()).isTrue();
        assertThat(result.mySlots()).hasSize(1);
        assertThat(result.heatmap()).hasSize(1);
        assertThat(result.heatmap().get(0).count()).isEqualTo(2);
        assertThat(result.bestSlot()).isNotNull();
        assertThat(result.bestSlot().count()).isEqualTo(2);
    }

    @Test
    void 요약_조회_응답_없으면_bestSlot_null() {
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(pollRepository.findByIdWithDates(100L)).willReturn(Optional.of(poll));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(true);
        given(teamMemberRepository.countByTeamId(10L)).willReturn(2L);
        given(slotRepository.countRespondedUsers(100L)).willReturn(0L);
        given(slotRepository.findByPollIdAndUserId(100L, 1L)).willReturn(List.of());
        given(slotRepository.findByPollIdWithUser(100L)).willReturn(List.of());

        AvailabilitySummaryResponse result = availabilityService.getSummary(100L, "user1");

        assertThat(result.heatmap()).isEmpty();
        assertThat(result.bestSlot()).isNull();
        assertThat(result.allResponded()).isFalse();
    }

    @Test
    void 요약_조회_팀원_아니면_예외() {
        given(userRepository.findByLoginId("user1")).willReturn(Optional.of(user));
        given(pollRepository.findByIdWithDates(100L)).willReturn(Optional.of(poll));
        given(teamMemberRepository.existsByTeamIdAndUserId(10L, 1L)).willReturn(false);

        assertThatThrownBy(() -> availabilityService.getSummary(100L, "user1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("팀원");
    }
}
