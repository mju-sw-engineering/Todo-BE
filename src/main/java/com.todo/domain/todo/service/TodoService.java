package com.todo.domain.todo.service;

import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.request.EvaluateTodoRequest;
import com.todo.domain.todo.dto.request.SubmitTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.dto.response.ParticipantDetailResponse;
import com.todo.domain.todo.dto.response.TodoDetailResponse;
import com.todo.domain.todo.dto.response.TodoPeriodReportResponse;
import com.todo.domain.todo.dto.response.TodoParticipantStatusResponse;
import com.todo.domain.todo.dto.response.TodoReportActionCandidateResponse;
import com.todo.domain.todo.dto.response.TodoReportDailyStatResponse;
import com.todo.domain.todo.dto.response.TodoReportPeriodResponse;
import com.todo.domain.todo.dto.response.TodoReportSummaryResponse;
import com.todo.domain.todo.dto.response.TodoSummaryResponse;
import com.todo.domain.todo.entity.ParticipantStatus;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoParticipant;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.entity.TodoVote;
import com.todo.domain.todo.entity.VoteType;
import com.todo.domain.todo.repository.TodoParticipantDetail;
import com.todo.domain.todo.repository.TodoParticipantRepository;
import com.todo.domain.todo.repository.TodoParticipantSummary;
import com.todo.domain.todo.repository.TodoVoteRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int ACTION_CANDIDATE_LIMIT = 5;

    private final TodoRepository todoRepository;
    private final TodoParticipantRepository todoParticipantRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final TodoVoteRepository todoVoteRepository;

    @Transactional
    public CreateTodoResponse createTodo(String loginId, Long teamId, CreateTodoRequest request) {
        User creator = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 팀입니다.", HttpStatus.NOT_FOUND));

        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, creator.getId())) {
            throw new BusinessException("팀에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        Todo todo = todoRepository.save(Todo.create(
                team,
                creator,
                request.title(),
                request.description(),
                toKstLocalDateTime(request.deadline())
        ));

        List<Long> assigneeIds = request.assigneeIds();
        for (Long assigneeId : assigneeIds) {
            User assignee = userRepository.findById(assigneeId)
                    .orElseThrow(() -> new BusinessException("존재하지 않는 사용자입니다: " + assigneeId, HttpStatus.NOT_FOUND));
            if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, assigneeId)) {
                throw new BusinessException("팀 멤버가 아닌 사용자는 배정할 수 없습니다: " + assigneeId, HttpStatus.BAD_REQUEST);
            }
            todoParticipantRepository.save(TodoParticipant.create(todo, assignee));
        }

        return CreateTodoResponse.from(todo, assigneeIds);
    }


    @Transactional
    public List<TodoSummaryResponse> getTodoList(Long teamId, String loginId, String filter) {
        User user = validateTeamMember(teamId, loginId);
        markExpiredTodosAsFail();
        List<Todo> todos = findTodosByFilter(teamId, filter);

        return toSummaryResponses(todos, user.getId());
    }

    @Transactional
    public List<TodoSummaryResponse> getTodayTodoList(Long teamId, String loginId) {
        User user = validateTeamMember(teamId, loginId);
        markExpiredTodosAsFail();
        LocalDate today = LocalDate.now(KST);
        List<Todo> todos = todoRepository.findByTeamIdAndDeadlineBetweenWithCreator(
                teamId,
                today.atStartOfDay(),
                today.plusDays(1).atStartOfDay()
        );

        return toSummaryResponses(todos, user.getId());
    }

    @Transactional
    public List<TodoSummaryResponse> getTodoHistory(Long teamId, String loginId, String date) {
        User user = validateTeamMember(teamId, loginId);
        LocalDate targetDate = parseDate(date);
        markExpiredTodosAsFail();
        List<Todo> todos = todoRepository.findByTeamIdAndDeadlineBetweenWithCreator(
                teamId,
                targetDate.atStartOfDay(),
                targetDate.plusDays(1).atStartOfDay()
        );

        return toSummaryResponses(todos, user.getId());
    }

    @Transactional
    public TodoPeriodReportResponse getTodoPeriodReport(
            Long teamId,
            String loginId,
            String startDate,
            String endDate
    ) {
        User user = validateTeamMember(teamId, loginId);
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        if (start.isAfter(end)) {
            throw new BusinessException("startDate는 endDate보다 늦을 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        markExpiredTodosAsFail();
        List<Todo> todos = todoRepository.findByTeamIdAndDeadlineBetweenWithCreator(
                teamId,
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay()
        );
        List<TodoSummaryResponse> todoSummaries = toSummaryResponses(todos, user.getId());
        List<TodoReportDailyStatResponse> dailyStats = buildDailyStats(start, end, todoSummaries);
        TodoReportSummaryResponse summary = buildPeriodSummary(dailyStats);

        return new TodoPeriodReportResponse(
                new TodoReportPeriodResponse(start, end, dailyStats.size()),
                summary,
                findWeakestDay(dailyStats),
                dailyStats,
                buildActionCandidates(todoSummaries)
        );
    }

    @Transactional
    public TodoDetailResponse getTodoDetail(Long todoId, String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        markExpiredTodosAsFail();
        Todo todo = todoRepository.findByIdWithCreatorAndTeam(todoId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 투두입니다.", HttpStatus.NOT_FOUND));

        if (!teamMemberRepository.existsByTeamIdAndUserId(todo.getTeam().getId(), user.getId())) {
            throw new BusinessException("해당 투두의 상세 정보 조회 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        List<TodoParticipantDetail> participants = todoParticipantRepository.findDetailByTodoId(todoId);
        long total = participants.size();
        long success = participants.stream()
                .filter(p -> p.getStatus() == ParticipantStatus.SUCCESS)
                .count();

        List<ParticipantDetailResponse> participantResponses = participants.stream()
                .map(p -> new ParticipantDetailResponse(
                        p.getUserId(),
                        p.getNickname(),
                        fileService.resolveImageUrl(p.getProfileImageUrl()),
                        fileService.resolveImageUrl(p.getProofImageKey()),
                        mapStatus(p.getStatus())
                ))
                .toList();

        return new TodoDetailResponse(
                todo.getId(),
                todo.getTitle(),
                toKstOffset(todo.getDeadline()),
                todo.getCreator().getNickname(),
                todo.getStatus(),
                success + " / " + total,
                participantResponses
        );
    }

    @Transactional
    public void evaluateTodo(Long todoId, String loginId, EvaluateTodoRequest request) {
        User voter = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        markExpiredTodosAsFail();
        Todo todo = todoRepository.findByIdWithCreatorAndTeam(todoId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 투두입니다.", HttpStatus.NOT_FOUND));

        if (!teamMemberRepository.existsByTeamIdAndUserId(todo.getTeam().getId(), voter.getId())) {
            throw new BusinessException("해당 팀의 멤버가 아닙니다.", HttpStatus.FORBIDDEN);
        }

        if (voter.getId().equals(request.targetUserId())) {
            throw new BusinessException("본인의 과업에는 투표할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        TodoParticipant targetParticipant = todoParticipantRepository
                .findByTodoIdAndUserIdWithLock(todoId, request.targetUserId())
                .orElseThrow(() -> new BusinessException("해당 투두의 배정자가 아닙니다.", HttpStatus.NOT_FOUND));

        if (targetParticipant.getStatus() != ParticipantStatus.PENDING) {
            throw new BusinessException("평가 대기 상태인 인증만 투표할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }

        if (todoVoteRepository.existsByTodoParticipantIdAndVoterId(targetParticipant.getId(), voter.getId())) {
            throw new BusinessException("이미 투표하셨습니다.", HttpStatus.CONFLICT);
        }

        todoVoteRepository.save(TodoVote.create(targetParticipant, voter, request.voteType()));
        targetParticipant.addVote(request.voteType());

        long totalParticipants = todoParticipantRepository.countByTodoId(todoId);

        if (request.voteType() == VoteType.POSITIVE
                && targetParticipant.getPositiveCount() * 2 >= totalParticipants) {
            targetParticipant.markAsSuccess();
            todo.getTeam().incrementSuccessCount();

            long successCount = todoParticipantRepository.countByTodoIdAndStatus(todoId, ParticipantStatus.SUCCESS);
            if (successCount == totalParticipants) {
                todo.markAsSuccess();
            }
        } else if (request.voteType() == VoteType.NEGATIVE
                && targetParticipant.getNegativeCount() > totalParticipants / 2) {
            targetParticipant.markAsFailByVote();
        }
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void submitTodo(Long todoId, String loginId, SubmitTodoRequest request) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        markExpiredTodosAsFail();
        TodoParticipant participant = todoParticipantRepository
                .findByTodoIdAndUserIdWithTodo(todoId, user.getId())
                .orElseThrow(() -> new BusinessException("해당 투두의 배정자가 아닙니다.", HttpStatus.FORBIDDEN));

        if (LocalDateTime.now(KST).isAfter(participant.getTodo().getDeadline())) {
            participant.markAsFail();
            participant.getTodo().markAsFail();
            throw new BusinessException("마감 시간이 지났습니다.", HttpStatus.BAD_REQUEST);
        }

        participant.submit(request.proofImageKey());
    }

    private void markExpiredTodosAsFail() {
        todoRepository.markExpiredTodosAsFail(LocalDateTime.now(KST));
    }

    private User validateTeamMember(Long teamId, String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        if (!teamRepository.existsById(teamId)) {
            throw new BusinessException("존재하지 않는 팀입니다.", HttpStatus.NOT_FOUND);
        }
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, user.getId())) {
            throw new BusinessException("팀에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        return user;
    }

    private List<Todo> findTodosByFilter(Long teamId, String filter) {
        if (filter == null || filter.isBlank()) {
            return todoRepository.findByTeamIdWithCreator(teamId);
        }

        return switch (filter) {
            case "IN_PROGRESS" -> todoRepository.findByTeamIdAndStatusWithCreator(teamId, TodoStatus.IN_PROGRESS);
            case "ENDED" -> todoRepository.findByTeamIdAndStatusInWithCreator(
                    teamId,
                    List.of(TodoStatus.SUCCESS, TodoStatus.FAIL)
            );
            default -> throw new BusinessException("알 수 없는 투두 필터입니다.", HttpStatus.BAD_REQUEST);
        };
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            throw new BusinessException("date 파라미터는 필수입니다.", HttpStatus.BAD_REQUEST);
        }

        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new BusinessException("date 형식은 yyyy-MM-dd 이어야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private List<TodoSummaryResponse> toSummaryResponses(List<Todo> todos, Long userId) {
        if (todos.isEmpty()) {
            return List.of();
        }

        List<Long> todoIds = todos.stream().map(Todo::getId).toList();
        Map<Long, List<TodoParticipantSummary>> participantsByTodoId = todoParticipantRepository
                .findSummaryByTodoIdIn(todoIds)
                .stream()
                .collect(Collectors.groupingBy(TodoParticipantSummary::getTodoId));

        return todos.stream()
                .map(todo -> toSummaryResponse(todo, participantsByTodoId.getOrDefault(todo.getId(), List.of()), userId))
                .toList();
    }

    private TodoSummaryResponse toSummaryResponse(
            Todo todo,
            List<TodoParticipantSummary> participants,
            Long userId
    ) {
        long total = participants.size();
        long success = participants.stream()
                .filter(p -> p.getStatus() == ParticipantStatus.SUCCESS)
                .count();
        String myStatus = participants.stream()
                .filter(p -> p.getUserId().equals(userId))
                .findFirst()
                .map(p -> mapStatus(p.getStatus()))
                .orElse(null);
        List<TodoParticipantStatusResponse> participantResponses = participants.stream()
                .map(p -> new TodoParticipantStatusResponse(
                        p.getUserId(),
                        p.getNickname(),
                        mapStatus(p.getStatus())
                ))
                .toList();

        return new TodoSummaryResponse(
                todo.getId(),
                todo.getTitle(),
                toKstOffset(todo.getDeadline()),
                todo.getCreator().getNickname(),
                todo.getStatus(),
                success + " / " + total,
                myStatus,
                calculateProgressRate(success, total),
                participantResponses
        );
    }

    private int calculateProgressRate(long achievementCount, long participantCount) {
        if (participantCount == 0) {
            return 0;
        }

        return (int) (achievementCount * 100 / participantCount);
    }

    private List<TodoReportDailyStatResponse> buildDailyStats(
            LocalDate start,
            LocalDate end,
            List<TodoSummaryResponse> todoSummaries
    ) {
        Map<LocalDate, List<TodoSummaryResponse>> todosByDate = todoSummaries.stream()
                .collect(Collectors.groupingBy(todo -> todo.deadline().toLocalDate()));
        List<TodoReportDailyStatResponse> dailyStats = new ArrayList<>();

        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            List<TodoSummaryResponse> dailyTodos = todosByDate.getOrDefault(date, List.of());
            int totalCount = dailyTodos.size();
            int successCount = countByStatus(dailyTodos, TodoStatus.SUCCESS);
            int failCount = countByStatus(dailyTodos, TodoStatus.FAIL);
            int inProgressCount = countByStatus(dailyTodos, TodoStatus.IN_PROGRESS);

            dailyStats.add(new TodoReportDailyStatResponse(
                    date,
                    totalCount,
                    successCount,
                    failCount,
                    inProgressCount,
                    calculateAchievementRate(successCount, totalCount)
            ));
        }

        return dailyStats;
    }

    private TodoReportSummaryResponse buildPeriodSummary(List<TodoReportDailyStatResponse> dailyStats) {
        int totalCount = dailyStats.stream().mapToInt(TodoReportDailyStatResponse::totalTodoCount).sum();
        int successCount = dailyStats.stream().mapToInt(TodoReportDailyStatResponse::successCount).sum();
        int failCount = dailyStats.stream().mapToInt(TodoReportDailyStatResponse::failCount).sum();
        int inProgressCount = dailyStats.stream().mapToInt(TodoReportDailyStatResponse::inProgressCount).sum();

        return new TodoReportSummaryResponse(
                totalCount,
                successCount,
                failCount,
                inProgressCount,
                calculateAchievementRate(successCount, totalCount)
        );
    }

    private TodoReportDailyStatResponse findWeakestDay(List<TodoReportDailyStatResponse> dailyStats) {
        return dailyStats.stream()
                .filter(day -> day.totalTodoCount() > 0)
                .min(Comparator
                        .comparing((TodoReportDailyStatResponse day) -> sortAchievementRate(day.achievementRate()))
                        .thenComparing(TodoReportDailyStatResponse::successCount)
                        .thenComparing(Comparator.comparing(TodoReportDailyStatResponse::failCount).reversed()))
                .orElse(null);
    }

    private List<TodoReportActionCandidateResponse> buildActionCandidates(
            List<TodoSummaryResponse> todoSummaries
    ) {
        List<TodoSummaryResponse> sortedCandidates = todoSummaries.stream()
                .filter(todo -> todo.status() != TodoStatus.SUCCESS && todo.status() != TodoStatus.FAIL)
                .sorted(Comparator
                        .comparingInt(TodoSummaryResponse::progressRate)
                        .thenComparing(Comparator.comparingInt(this::getUnverifiedCount).reversed())
                        .thenComparing(TodoSummaryResponse::deadline))
                .limit(ACTION_CANDIDATE_LIMIT)
                .toList();
        List<TodoReportActionCandidateResponse> responses = new ArrayList<>();

        for (int index = 0; index < sortedCandidates.size(); index++) {
            TodoSummaryResponse todo = sortedCandidates.get(index);
            int participantCount = getParticipantCount(todo);
            int achievementCount = getAchievementCount(todo);
            responses.add(new TodoReportActionCandidateResponse(
                    index + 1,
                    todo.todoId(),
                    todo.title(),
                    todo.deadline(),
                    todo.status(),
                    achievementCount,
                    participantCount,
                    Math.max(participantCount - achievementCount, 0),
                    todo.progressRate()
            ));
        }

        return responses;
    }

    private int countByStatus(List<TodoSummaryResponse> todos, TodoStatus status) {
        return (int) todos.stream()
                .filter(todo -> todo.status() == status)
                .count();
    }

    private Integer calculateAchievementRate(int successCount, int totalCount) {
        if (totalCount == 0) {
            return null;
        }

        return successCount * 100 / totalCount;
    }

    private int sortAchievementRate(Integer achievementRate) {
        if (achievementRate == null) {
            return 101;
        }

        return achievementRate;
    }

    private int getAchievementCount(TodoSummaryResponse todo) {
        return parseAchievementCountPart(todo.achievementCount(), 0);
    }

    private int getParticipantCount(TodoSummaryResponse todo) {
        int parsedCount = parseAchievementCountPart(todo.achievementCount(), 1);
        if (parsedCount > 0) {
            return parsedCount;
        }
        if (todo.participants() == null) {
            return 0;
        }

        return todo.participants().size();
    }

    private int getUnverifiedCount(TodoSummaryResponse todo) {
        return Math.max(getParticipantCount(todo) - getAchievementCount(todo), 0);
    }

    private int parseAchievementCountPart(String achievementCount, int partIndex) {
        if (achievementCount == null || achievementCount.isBlank()) {
            return 0;
        }

        String[] parts = achievementCount.split("/");
        if (parts.length <= partIndex) {
            return 0;
        }

        try {
            return Integer.parseInt(parts[partIndex].trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String mapStatus(ParticipantStatus status) {
        return switch (status) {
            case SUCCESS -> "완료";
            case PENDING -> "평가 대기중";
            case IN_PROGRESS -> "미완료";
            case FAIL -> "실패";
        };
    }

    private OffsetDateTime toKstOffset(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        return dateTime.atOffset(ZoneOffset.ofHours(9));
    }

    private LocalDateTime toKstLocalDateTime(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        return dateTime.withOffsetSameInstant(ZoneOffset.ofHours(9)).toLocalDateTime();
    }
}
