package com.todo.domain.todo.service;

import com.todo.domain.notification.entity.NotificationType;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.request.ReactTodoRequest;
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
import com.todo.domain.todo.dto.response.TodoReactionResponse;
import com.todo.domain.todo.dto.response.TodoSummaryResponse;
import com.todo.domain.todo.entity.ParticipantStatus;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoParticipant;
import com.todo.domain.todo.entity.TodoReaction;
import com.todo.domain.todo.entity.TodoReactionType;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.repository.TodoParticipantDetail;
import com.todo.domain.todo.repository.TodoParticipantRepository;
import com.todo.domain.todo.repository.TodoParticipantSummary;
import com.todo.domain.todo.repository.TodoReactionCount;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
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
    private final TodoReactionRepository todoReactionRepository;
    private final TransactionTemplate transactionTemplate;
    private final NotificationService notificationService;

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

        sendTodoCreatedNotifications(todo, creator);

        return CreateTodoResponse.from(todo, assigneeIds);
    }

    private void sendTodoCreatedNotifications(Todo todo, User creator) {
        List<TeamMember> receivers = teamMemberRepository.findByTeamIdExcludingUser(
                todo.getTeam().getId(), creator.getId()
        );
        String title = "새로운 투두가 생성되었습니다.";
        String content = creator.getNickname() + "님이 '" + todo.getTitle() + "'을(를) 만들었습니다.";
        notificationService.sendAll(
                receivers.stream().map(TeamMember::getUser).toList(),
                NotificationType.TODO_CREATED,
                title,
                content,
                todo.getId()
        );
    }


    public List<TodoSummaryResponse> getTodoList(Long teamId, String loginId, String filter, String date) {
        User user = validateTeamMember(teamId, loginId);

        boolean hasFilter = filter != null && !filter.isBlank();
        boolean hasDate = date != null && !date.isBlank();
        if (hasFilter && hasDate) {
            throw new BusinessException("filter와 date는 함께 사용할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        List<Todo> todos;
        if (hasDate) {
            LocalDate targetDate = parseDate(date);
            todos = todoRepository.findByTeamIdAndDeadlineBetweenWithCreator(
                    teamId,
                    targetDate.atStartOfDay(),
                    targetDate.plusDays(1).atStartOfDay()
            );
        } else {
            todos = findTodosByFilter(teamId, filter);
        }

        return toSummaryResponses(todos, user.getId(), LocalDateTime.now(KST));
    }

    /**
     * @deprecated GET /todos?date= 통합 API로 대체됨. 웹/iOS 마이그레이션 완료 후 제거 예정.
     */
    @Deprecated
    public List<TodoSummaryResponse> getTodayTodoList(Long teamId, String loginId) {
        return getTodoList(teamId, loginId, null, LocalDate.now(KST).toString());
    }

    /**
     * @deprecated GET /todos?date= 통합 API로 대체됨. 웹/iOS 마이그레이션 완료 후 제거 예정.
     */
    @Deprecated
    public List<TodoSummaryResponse> getTodoHistory(Long teamId, String loginId, String date) {
        if (date == null || date.isBlank()) {
            throw new BusinessException("date 파라미터는 필수입니다.", HttpStatus.BAD_REQUEST);
        }
        return getTodoList(teamId, loginId, null, date);
    }

    public TodoPeriodReportResponse getTodoPeriodReport(
            Long teamId,
            String loginId,
            String startDate,
            String endDate
    ) {
        User user = validateTeamMember(teamId, loginId);
        LocalDate start = parseRequiredDate("startDate", startDate);
        LocalDate end = parseRequiredDate("endDate", endDate);
        if (start.isAfter(end)) {
            throw new BusinessException("startDate는 endDate보다 늦을 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        List<Todo> todos = todoRepository.findByTeamIdAndDeadlineBetweenWithCreator(
                teamId,
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay()
        );
        List<TodoSummaryResponse> todoSummaries = toSummaryResponses(todos, user.getId(), LocalDateTime.now(KST));
        List<TodoReportDailyStatResponse> dailyStats = buildDailyStats(start, end, todoSummaries);
        TodoReportSummaryResponse summary = buildPeriodSummary(dailyStats);

        return TodoPeriodReportResponse.from(
                TodoReportPeriodResponse.from(start, end, dailyStats.size()),
                summary,
                findWeakestDay(dailyStats),
                dailyStats,
                buildActionCandidates(todoSummaries)
        );
    }

    public TodoDetailResponse getTodoDetail(Long todoId, String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        Todo todo = todoRepository.findByIdWithCreatorAndTeam(todoId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 투두입니다.", HttpStatus.NOT_FOUND));

        if (!teamMemberRepository.existsByTeamIdAndUserId(todo.getTeam().getId(), user.getId())) {
            throw new BusinessException("해당 투두의 상세 정보 조회 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        List<TodoParticipantDetail> participants = todoParticipantRepository.findDetailByTodoId(todoId);
        Map<Long, Map<TodoReactionType, Long>> reactionCountsByParticipantId = getReactionCountsByParticipantId(participants);
        Map<Long, TodoReactionType> myReactionsByParticipantId = getMyReactionsByParticipantId(participants, user.getId());
        long total = participants.size();
        long success = participants.stream()
                .filter(p -> p.getStatus() == ParticipantStatus.SUCCESS)
                .count();

        List<ParticipantDetailResponse> participantResponses = participants.stream()
                .map(p -> new ParticipantDetailResponse(
                        p.getTodoParticipantId(),
                        p.getUserId(),
                        p.getNickname(),
                        fileService.resolveImageUrl(p.getProfileImageUrl()),
                        fileService.resolveImageUrl(p.getProofImageKey()),
                        fileService.resolveImageUrl(p.getProofThumbnailKey()),
                        mapStatus(p.getStatus()),
                        buildReactionResponses(reactionCountsByParticipantId.getOrDefault(
                                p.getTodoParticipantId(),
                                Map.of()
                        )),
                        myReactionsByParticipantId.get(p.getTodoParticipantId())
                ))
                .toList();

        return new TodoDetailResponse(
                todo.getId(),
                todo.getTitle(),
                toKstOffset(todo.getDeadline()),
                todo.getCreator().getNickname(),
                resolveDisplayStatus(todo.getStatus(), todo.getDeadline(), LocalDateTime.now(KST)),
                success + " / " + total,
                participantResponses
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void submitTodo(Long todoId, String loginId, SubmitTodoRequest request) {
        SubmitTodoCheck check = transactionTemplate.execute(status -> checkSubmitTodo(todoId, loginId));
        check.throwIfFailed();

        String proofThumbnailKey = fileService.createProofThumbnail(request.proofImageKey());

        SubmitTodoCheck result;
        try {
            result = transactionTemplate.execute(status ->
                    submitTodoInTransaction(todoId, check.userId(), request.proofImageKey(), proofThumbnailKey)
            );
        } catch (RuntimeException e) {
            cleanupProofThumbnail(proofThumbnailKey, e);
            throw e;
        }
        if (result.hasFailed()) {
            cleanupProofThumbnail(proofThumbnailKey, result.failure());
        }
        result.throwIfFailed();
    }

    private void cleanupProofThumbnail(String proofThumbnailKey, RuntimeException primaryFailure) {
        try {
            fileService.deleteObject(proofThumbnailKey);
        } catch (RuntimeException deleteException) {
            primaryFailure.addSuppressed(deleteException);
        }
    }

    private SubmitTodoCheck checkSubmitTodo(Long todoId, String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        markExpiredTodosAsFail();
        TodoParticipant participant = todoParticipantRepository
                .findByTodoIdAndUserIdWithTodo(todoId, user.getId())
                .orElseThrow(() -> new BusinessException("해당 투두의 배정자가 아닙니다.", HttpStatus.FORBIDDEN));

        if (LocalDateTime.now(KST).isAfter(participant.getTodo().getDeadline())) {
            participant.markAsFail();
            participant.getTodo().markAsFail();
            return SubmitTodoCheck.failed(user.getId(), "마감 시간이 지났습니다.", HttpStatus.BAD_REQUEST);
        }

        return SubmitTodoCheck.success(user.getId());
    }

    private SubmitTodoCheck submitTodoInTransaction(
            Long todoId,
            Long userId,
            String proofImageKey,
            String proofThumbnailKey
    ) {
        markExpiredTodosAsFail();
        // 더블클릭·동시 제출을 직렬화하기 위해 참가자 행에 비관적 락을 건다.
        TodoParticipant participant = todoParticipantRepository
                .findByTodoIdAndUserIdWithLock(todoId, userId)
                .orElseThrow(() -> new BusinessException("해당 투두의 배정자가 아닙니다.", HttpStatus.FORBIDDEN));
        Todo todo = participant.getTodo();

        if (LocalDateTime.now(KST).isAfter(todo.getDeadline())) {
            participant.markAsFail();
            todo.markAsFail();
            return SubmitTodoCheck.failed(userId, "마감 시간이 지났습니다.", HttpStatus.BAD_REQUEST);
        }

        participant.submit(proofImageKey, proofThumbnailKey);
        // 카운터 갱신은 팀 행을 잠그는 원자적 UPDATE로 처리해 동시 제출 시 유실을 막는다.
        teamRepository.incrementSuccessCount(todo.getTeam().getId());

        long totalParticipants = todoParticipantRepository.countByTodoId(todoId);
        long successCount = todoParticipantRepository.countByTodoIdAndStatus(todoId, ParticipantStatus.SUCCESS);
        if (successCount == totalParticipants) {
            todo.markAsSuccess();
        }

        return SubmitTodoCheck.success(userId);
    }

    @Transactional
    public TodoReactionResponse reactTodoParticipant(Long participantId, String loginId, ReactTodoRequest request) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));
        TodoParticipant participant = todoParticipantRepository.findByIdWithTodoAndTeam(participantId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 투두 참여자입니다.", HttpStatus.NOT_FOUND));

        Long teamId = participant.getTodo().getTeam().getId();
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, user.getId())) {
            throw new BusinessException("팀에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }
        if (participant.getProofImageKey() == null || participant.getProofImageKey().isBlank()) {
            throw new BusinessException("인증 사진이 제출된 투두에만 반응할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }

        TodoReactionType reactionType = request.type();
        todoReactionRepository.findByTodoParticipantIdAndUserId(participantId, user.getId())
                .ifPresentOrElse(
                        reaction -> updateOrDeleteReaction(reaction, reactionType),
                        () -> todoReactionRepository.save(TodoReaction.create(participant, user, reactionType))
                );

        long count = todoReactionRepository.countByTodoParticipantIdAndReactionType(participantId, reactionType);
        return TodoReactionResponse.from(reactionType, count);
    }

    private void markExpiredTodosAsFail() {
        todoRepository.markExpiredTodosAsFail(LocalDateTime.now(KST));
    }

    private record SubmitTodoCheck(Long userId, BusinessException failure) {
        static SubmitTodoCheck success(Long userId) {
            return new SubmitTodoCheck(userId, null);
        }

        static SubmitTodoCheck failed(Long userId, String message, HttpStatus status) {
            return new SubmitTodoCheck(userId, new BusinessException(message, status));
        }

        boolean hasFailed() {
            return failure != null;
        }

        void throwIfFailed() {
            if (failure != null) {
                throw failure;
            }
        }
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

    private LocalDate parseRequiredDate(String parameterName, String date) {
        if (date == null || date.isBlank()) {
            throw new BusinessException(parameterName + " 파라미터는 필수입니다.", HttpStatus.BAD_REQUEST);
        }

        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new BusinessException(parameterName + " 형식은 yyyy-MM-dd 이어야 합니다.", HttpStatus.BAD_REQUEST);
        }
    }

    private List<TodoSummaryResponse> toSummaryResponses(List<Todo> todos, Long userId, LocalDateTime now) {
        if (todos.isEmpty()) {
            return List.of();
        }

        List<Long> todoIds = todos.stream().map(Todo::getId).toList();
        Map<Long, List<TodoParticipantSummary>> participantsByTodoId = todoParticipantRepository
                .findSummaryByTodoIdIn(todoIds)
                .stream()
                .collect(Collectors.groupingBy(TodoParticipantSummary::getTodoId));

        return todos.stream()
                .map(todo -> toSummaryResponse(todo, participantsByTodoId.getOrDefault(todo.getId(), List.of()), userId, now))
                .toList();
    }

    private Map<Long, Map<TodoReactionType, Long>> getReactionCountsByParticipantId(
            List<TodoParticipantDetail> participants
    ) {
        List<Long> participantIds = participants.stream()
                .map(TodoParticipantDetail::getTodoParticipantId)
                .toList();
        if (participantIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Map<TodoReactionType, Long>> result = new HashMap<>();
        for (TodoReactionCount reactionCount : todoReactionRepository.countByTodoParticipantIds(participantIds)) {
            result.computeIfAbsent(reactionCount.getTodoParticipantId(), ignored -> new EnumMap<>(TodoReactionType.class))
                    .put(reactionCount.getReactionType(), reactionCount.getReactionCount());
        }

        return result;
    }

    private Map<Long, TodoReactionType> getMyReactionsByParticipantId(
            List<TodoParticipantDetail> participants,
            Long userId
    ) {
        List<Long> participantIds = participants.stream()
                .map(TodoParticipantDetail::getTodoParticipantId)
                .toList();
        if (participantIds.isEmpty()) {
            return Map.of();
        }

        return todoReactionRepository.findByTodoParticipantIdInAndUserId(participantIds, userId)
                .stream()
                .collect(Collectors.toMap(
                        reaction -> reaction.getTodoParticipant().getId(),
                        TodoReaction::getReactionType
                ));
    }

    private List<TodoReactionResponse> buildReactionResponses(Map<TodoReactionType, Long> reactionCounts) {
        return List.of(TodoReactionType.values()).stream()
                .map(type -> TodoReactionResponse.from(type, reactionCounts.getOrDefault(type, 0L)))
                .toList();
    }

    private void updateOrDeleteReaction(TodoReaction reaction, TodoReactionType reactionType) {
        if (reaction.hasSameType(reactionType)) {
            todoReactionRepository.delete(reaction);
            return;
        }

        reaction.updateType(reactionType);
    }

    private TodoSummaryResponse toSummaryResponse(
            Todo todo,
            List<TodoParticipantSummary> participants,
            Long userId,
            LocalDateTime now
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
                resolveDisplayStatus(todo.getStatus(), todo.getDeadline(), now),
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

            dailyStats.add(TodoReportDailyStatResponse.from(
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

        return TodoReportSummaryResponse.from(
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
            responses.add(TodoReportActionCandidateResponse.from(
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
            case IN_PROGRESS -> "미완료";
            case FAIL -> "실패";
        };
    }

    /**
     * 마감이 지난 IN_PROGRESS 투두는 스케줄러가 FAIL로 바꾸기 전이라도 응답에서는 FAIL로 표시한다.
     * (스케줄러 주기 사이 조회 시 표시 값이 흔들리지 않도록 계산으로 보정)
     */
    private TodoStatus resolveDisplayStatus(TodoStatus status, LocalDateTime deadline, LocalDateTime now) {
        if (status == TodoStatus.IN_PROGRESS && deadline != null && deadline.isBefore(now)) {
            return TodoStatus.FAIL;
        }
        return status;
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
