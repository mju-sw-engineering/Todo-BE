package com.todo.domain.todo.service;

import com.todo.domain.notification.message.NotificationMessageFactory;
import com.todo.domain.notification.service.NotificationService;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.dto.request.AssignTodoWorkItemRequest;
import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.request.CreateTodoTaskRequest;
import com.todo.domain.todo.dto.request.ReactTodoRequest;
import com.todo.domain.todo.dto.request.SubmitTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.dto.response.MyWorkSummaryResponse;
import com.todo.domain.todo.dto.response.TodoDetailResponse;
import com.todo.domain.todo.dto.response.TodoDirectAssigneeResponse;
import com.todo.domain.todo.dto.response.TodoPeriodReportResponse;
import com.todo.domain.todo.dto.response.TodoReactionResponse;
import com.todo.domain.todo.dto.response.TodoReportActionCandidateResponse;
import com.todo.domain.todo.dto.response.TodoReportDailyStatResponse;
import com.todo.domain.todo.dto.response.TodoReportPeriodResponse;
import com.todo.domain.todo.dto.response.TodoReportSummaryResponse;
import com.todo.domain.todo.dto.response.TodoSummaryResponse;
import com.todo.domain.todo.dto.response.TodoTaskResponse;
import com.todo.domain.todo.dto.response.TodoWorkItemAssigneeResponse;
import com.todo.domain.todo.dto.response.TodoWorkItemSubmissionResponse;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoMode;
import com.todo.domain.todo.entity.TodoReaction;
import com.todo.domain.todo.entity.TodoReactionType;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.entity.WorkItemType;
import com.todo.domain.todo.repository.TodoReactionCount;
import com.todo.domain.todo.repository.TodoReactionRepository;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.todo.repository.TodoWorkItemSummary;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.domain.user.support.WithdrawnUserDisplay;
import com.todo.global.exception.BusinessException;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);
    private static final int ACTION_CANDIDATE_LIMIT = 5;

    private final TodoRepository todoRepository;
    private final TodoWorkItemRepository todoWorkItemRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final FileService fileService;
    private final TodoReactionRepository todoReactionRepository;
    private final TransactionTemplate transactionTemplate;
    private final NotificationService notificationService;
    private final NotificationMessageFactory notificationMessageFactory;
    private final TodoStatusTransitionService todoStatusTransitionService;

    @Transactional
    public CreateTodoResponse createTodo(String userId, Long teamId, CreateTodoRequest request) {
        User creator = findAuthenticatedUser(userId);
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 팀입니다.", HttpStatus.NOT_FOUND));
        requireTeamMember(teamId, creator.getId(), "팀에 접근할 권한이 없습니다.");

        LocalDateTime todoDeadline = toKstLocalDateTime(request.deadline());
        if (todoDeadline == null || !todoDeadline.isAfter(LocalDateTime.now(KST))) {
            throw new BusinessException("Todo 마감은 현재보다 미래여야 합니다.", HttpStatus.BAD_REQUEST);
        }

        List<Long> assigneeIds = request.assigneeIds() == null ? List.of() : request.assigneeIds();
        List<CreateTodoTaskRequest> tasks = request.tasks() == null ? List.of() : request.tasks();
        boolean hasDirectAssignees = !assigneeIds.isEmpty();
        boolean hasTasks = !tasks.isEmpty();
        if (hasDirectAssignees == hasTasks) {
            throw new BusinessException("assigneeIds와 tasks 중 하나만 입력해야 합니다.", HttpStatus.BAD_REQUEST);
        }

        TodoMode mode = hasTasks ? TodoMode.TASK : TodoMode.DIRECT;
        Todo todo = todoRepository.save(Todo.create(
                team,
                creator,
                request.title(),
                request.description(),
                todoDeadline,
                mode
        ));

        List<TodoWorkItem> workItems = hasTasks
                ? createTaskWorkItems(todo, teamId, tasks)
                : createDirectWorkItems(todo, teamId, assigneeIds);
        todoWorkItemRepository.saveAll(workItems);

        sendCreationNotifications(todo, creator, workItems);
        return toCreateResponse(todo, workItems);
    }

    public List<TodoSummaryResponse> getTodoList(Long teamId, String userId, String filter, String date) {
        User user = validateTeamMember(teamId, userId);
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
        return toSummaryResponses(todos, user.getId());
    }

    public List<TodoSummaryResponse> getActiveTodoList(Long teamId, String userId, String status) {
        User user = validateTeamMember(teamId, userId);
        List<TodoStatus> statuses = resolveActiveStatuses(status);
        List<Todo> todos = todoRepository.findByTeamIdAndStatusInAndDeadlineAfterWithCreator(
                teamId,
                statuses,
                LocalDateTime.now(KST)
        );
        return toSummaryResponses(todos, user.getId());
    }

    private List<TodoStatus> resolveActiveStatuses(String status) {
        if (status == null || status.isBlank()) {
            return List.of(TodoStatus.IN_PROGRESS, TodoStatus.SUCCESS, TodoStatus.FAIL);
        }
        return switch (status) {
            case "PENDING" -> List.of(TodoStatus.IN_PROGRESS);
            case "DONE" -> List.of(TodoStatus.SUCCESS, TodoStatus.FAIL);
            default -> throw new BusinessException("알 수 없는 status 값입니다.", HttpStatus.BAD_REQUEST);
        };
    }

    public TodoPeriodReportResponse getTodoPeriodReport(
            Long teamId,
            String userId,
            String startDate,
            String endDate
    ) {
        validateTeamMember(teamId, userId);
        LocalDate start = parseRequiredDate("startDate", startDate);
        LocalDate end = parseRequiredDate("endDate", endDate);
        if (start.isAfter(end)) {
            throw new BusinessException("startDate는 endDate보다 늦을 수 없습니다.", HttpStatus.BAD_REQUEST);
        }

        List<TodoWorkItem> workItems = todoWorkItemRepository.findByTeamIdAndEffectiveDeadlineBetween(
                teamId,
                start.atStartOfDay(),
                end.plusDays(1).atStartOfDay()
        );
        List<TodoReportDailyStatResponse> dailyStats = buildDailyStats(start, end, workItems);
        TodoReportSummaryResponse summary = buildPeriodSummary(dailyStats);

        return TodoPeriodReportResponse.from(
                TodoReportPeriodResponse.from(start, end, dailyStats.size()),
                summary,
                findWeakestDay(dailyStats),
                dailyStats,
                buildActionCandidates(workItems)
        );
    }

    public TodoDetailResponse getTodoDetail(Long todoId, String userId) {
        User user = findAuthenticatedUser(userId);
        Todo todo = todoRepository.findByIdWithCreatorAndTeam(todoId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 투두입니다.", HttpStatus.NOT_FOUND));
        requireTeamMember(todo.getTeam().getId(), user.getId(), "해당 투두의 상세 정보 조회 권한이 없습니다.");

        List<TodoWorkItem> workItems = todoWorkItemRepository.findByTodoIdOrderByPositionAsc(todoId);
        Map<Long, Map<TodoReactionType, Long>> reactionCounts = getReactionCountsByWorkItemId(workItems);
        Map<Long, TodoReactionType> myReactions = getMyReactionsByWorkItemId(workItems, user.getId());

        List<TodoDirectAssigneeResponse> directAssignees = workItems.stream()
                .filter(workItem -> workItem.getType() == WorkItemType.DIRECT)
                .map(workItem -> TodoDirectAssigneeResponse.from(
                        workItem,
                        toKstOffset(workItem.getSubmittedAt()),
                        resolveThumbnailUrl(workItem),
                        reactionCounts.getOrDefault(workItem.getId(), Map.of()),
                        myReactions.get(workItem.getId())
                ))
                .toList();
        List<TodoTaskResponse> tasks = workItems.stream()
                .filter(workItem -> workItem.getType() == WorkItemType.TASK)
                .map(workItem -> TodoTaskResponse.from(
                        workItem,
                        toKstOffset(workItem.getEffectiveDeadline()),
                        toKstOffset(workItem.getSubmittedAt()),
                        resolveThumbnailUrl(workItem),
                        reactionCounts.getOrDefault(workItem.getId(), Map.of()),
                        myReactions.get(workItem.getId())
                ))
                .toList();

        return TodoDetailResponse.from(
                todo,
                toKstOffset(todo.getDeadline()),
                resolveCreatorNickname(todo),
                achievementCount(workItems),
                directAssignees,
                tasks
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void submitTodo(Long todoId, String userId, SubmitTodoRequest request) {
        User user = findAuthenticatedUser(userId);
        TodoWorkItem workItem = todoWorkItemRepository
                .findDirectByTodoIdAndAssigneeIdWithTodo(todoId, user.getId())
                .orElseThrow(() -> new BusinessException("해당 투두의 DIRECT 담당자가 아닙니다.", HttpStatus.FORBIDDEN));
        submitWorkItem(workItem.getId(), workItem.getTodo().getId(), user, request.proofImageKey());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void submitTodoWorkItem(Long workItemId, String userId, SubmitTodoRequest request) {
        User user = findAuthenticatedUser(userId);
        TodoWorkItem workItem = todoWorkItemRepository.findByIdWithTodoAndTeam(workItemId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 WorkItem입니다.", HttpStatus.NOT_FOUND));
        submitWorkItem(workItemId, workItem.getTodo().getId(), user, request.proofImageKey());
    }

    public TodoWorkItemSubmissionResponse getTodoWorkItemSubmission(Long workItemId, String userId) {
        User user = findAuthenticatedUser(userId);
        TodoWorkItem workItem = todoWorkItemRepository.findByIdWithTodoAndTeam(workItemId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 WorkItem입니다.", HttpStatus.NOT_FOUND));
        requireTeamMember(workItem.getTodo().getTeam().getId(), user.getId(), "해당 제출 사진을 조회할 권한이 없습니다.");
        if (workItem.getProofImageKey() == null || workItem.getSubmittedAt() == null) {
            throw new BusinessException("제출된 인증 사진이 없습니다.", HttpStatus.NOT_FOUND);
        }

        String originalUrl = fileService.resolveImageUrl(workItem.getProofImageKey());
        String thumbnailUrl = workItem.getProofThumbnailKey() == null
                ? originalUrl
                : fileService.resolveImageUrl(workItem.getProofThumbnailKey());
        OffsetDateTime expiresAt = OffsetDateTime.now(KST_OFFSET).plus(fileService.getPresignedUrlExpiration());
        return TodoWorkItemSubmissionResponse.from(
                workItem,
                toKstOffset(workItem.getSubmittedAt()),
                originalUrl,
                thumbnailUrl,
                expiresAt
        );
    }

    @Transactional
    public TodoReactionResponse reactTodoWorkItem(Long workItemId, String userId, ReactTodoRequest request) {
        User user = findAuthenticatedUser(userId);
        TodoWorkItem workItem = todoWorkItemRepository.findByIdWithTodoAndTeam(workItemId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 WorkItem입니다.", HttpStatus.NOT_FOUND));
        requireTeamMember(workItem.getTodo().getTeam().getId(), user.getId(), "팀에 접근할 권한이 없습니다.");
        if (workItem.getProofImageKey() == null || workItem.getProofImageKey().isBlank()) {
            throw new BusinessException("인증 사진이 제출된 항목에만 반응할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }

        TodoReactionType reactionType = request.type();
        TodoReaction reaction = todoReactionRepository.findByTodoWorkItemIdAndUserId(workItemId, user.getId())
                .map(existing -> updateOrDeleteReaction(existing, reactionType))
                .orElseGet(() -> todoReactionRepository.save(TodoReaction.create(workItem, user, reactionType)));
        long count = todoReactionRepository.countByTodoWorkItemIdAndReactionType(workItemId, reactionType);
        return TodoReactionResponse.from(reaction, count);
    }

    /**
     * 기존 participant 반응 경로의 deprecated 별칭이다. PK는 마이그레이션에서 그대로 보존된다.
     */
    @Deprecated
    @Transactional
    public TodoReactionResponse reactTodoParticipant(Long participantId, String userId, ReactTodoRequest request) {
        return reactTodoWorkItem(participantId, userId, request);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TodoWorkItemAssigneeResponse reassignTodoWorkItem(
            Long workItemId,
            String userId,
            AssignTodoWorkItemRequest request
    ) {
        User requester = findAuthenticatedUser(userId);
        TodoWorkItem snapshot = todoWorkItemRepository.findByIdWithTodoAndTeam(workItemId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 WorkItem입니다.", HttpStatus.NOT_FOUND));
        Long teamId = snapshot.getTodo().getTeam().getId();
        requireTeamMember(teamId, requester.getId(), "해당 WorkItem을 재배정할 권한이 없습니다.");
        if (snapshot.getAssignee() != null) {
            throw new BusinessException("이미 담당자가 배정된 WorkItem입니다.", HttpStatus.BAD_REQUEST);
        }

        User assignee = userRepository.findById(request.assigneeId())
                .orElseThrow(() -> new BusinessException("존재하지 않는 사용자입니다.", HttpStatus.NOT_FOUND));
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, assignee.getId())) {
            throw new BusinessException("현재 팀원만 담당자로 배정할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }

        AssignmentResult result = transactionTemplate.execute(status -> assignWorkItem(
                workItemId,
                snapshot.getTodo().getId(),
                requester,
                assignee
        ));
        if (result == null) {
            throw new BusinessException("재배정에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        result.throwIfFailed();
        return result.response();
    }

    private List<TodoWorkItem> createDirectWorkItems(Todo todo, Long teamId, List<Long> assigneeIds) {
        if (new HashSet<>(assigneeIds).size() != assigneeIds.size()) {
            throw new BusinessException("DIRECT 담당자를 중복해서 배정할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        Map<Long, User> users = findAssignableUsers(teamId, assigneeIds);
        return assigneeIds.stream()
                .map(assigneeId -> TodoWorkItem.createDirect(todo, users.get(assigneeId)))
                .toList();
    }

    private List<TodoWorkItem> createTaskWorkItems(
            Todo todo,
            Long teamId,
            List<CreateTodoTaskRequest> tasks
    ) {
        List<Long> assigneeIds = tasks.stream().map(CreateTodoTaskRequest::assigneeId).distinct().toList();
        Map<Long, User> users = findAssignableUsers(teamId, assigneeIds);
        List<TodoWorkItem> workItems = new ArrayList<>();
        for (int position = 0; position < tasks.size(); position++) {
            CreateTodoTaskRequest task = tasks.get(position);
            LocalDateTime taskDeadline = toKstLocalDateTime(task.deadline());
            if (taskDeadline != null && !taskDeadline.isAfter(LocalDateTime.now(KST))) {
                throw new BusinessException("Task 마감은 현재보다 미래여야 합니다.", HttpStatus.BAD_REQUEST);
            }
            workItems.add(TodoWorkItem.createTask(
                    todo,
                    users.get(task.assigneeId()),
                    task.title(),
                    task.description(),
                    taskDeadline,
                    position
            ));
        }
        return workItems;
    }

    private Map<Long, User> findAssignableUsers(Long teamId, List<Long> userIds) {
        Map<Long, User> users = new LinkedHashMap<>();
        for (Long userId : userIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(
                            "존재하지 않는 사용자입니다: " + userId,
                            HttpStatus.NOT_FOUND
                    ));
            if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
                throw new BusinessException("팀 멤버가 아닌 사용자는 배정할 수 없습니다: " + userId, HttpStatus.BAD_REQUEST);
            }
            users.put(userId, user);
        }
        return users;
    }

    private void sendCreationNotifications(Todo todo, User creator, List<TodoWorkItem> workItems) {
        if (todo.getMode() == TodoMode.DIRECT) {
            List<User> receivers = teamMemberRepository.findByTeamIdExcludingUser(
                            todo.getTeam().getId(),
                            creator.getId()
                    ).stream()
                    .map(TeamMember::getUser)
                    .toList();
            notificationService.sendAll(
                    receivers,
                    creator,
                    notificationMessageFactory.todoCreated(todo.getTitle()),
                    todo.getId()
            );
            return;
        }

        Map<Long, Long> assignmentCounts = workItems.stream()
                .collect(Collectors.groupingBy(
                        workItem -> workItem.getAssignee().getId(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        Map<Long, User> assignedUsers = workItems.stream()
                .map(TodoWorkItem::getAssignee)
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left));

        List<User> createdReceivers = teamMemberRepository.findByTeamIdExcludingUser(
                        todo.getTeam().getId(),
                        creator.getId()
                ).stream()
                .map(TeamMember::getUser)
                .filter(user -> !assignmentCounts.containsKey(user.getId()))
                .toList();
        notificationService.sendAll(
                createdReceivers,
                creator,
                notificationMessageFactory.todoCreated(todo.getTitle()),
                todo.getId()
        );

        assignmentCounts.forEach((assigneeId, count) -> {
            if (!creator.getId().equals(assigneeId)) {
                notificationService.send(
                        assignedUsers.get(assigneeId),
                        creator,
                        notificationMessageFactory.todoAssigned(todo.getTitle(), Math.toIntExact(count)),
                        todo.getId()
                );
            }
        });
    }

    private CreateTodoResponse toCreateResponse(Todo todo, List<TodoWorkItem> workItems) {
        List<TodoDirectAssigneeResponse> directAssignees = workItems.stream()
                .filter(workItem -> workItem.getType() == WorkItemType.DIRECT)
                .map(workItem -> TodoDirectAssigneeResponse.from(workItem, null, null, Map.of(), null))
                .toList();
        List<TodoTaskResponse> tasks = workItems.stream()
                .filter(workItem -> workItem.getType() == WorkItemType.TASK)
                .map(workItem -> TodoTaskResponse.from(
                        workItem,
                        toKstOffset(workItem.getEffectiveDeadline()),
                        null,
                        null,
                        Map.of(),
                        null
                ))
                .toList();
        return CreateTodoResponse.from(todo, toKstOffset(todo.getDeadline()), directAssignees, tasks);
    }

    private void submitWorkItem(Long workItemId, Long todoId, User user, String proofImageKey) {
        OperationResult check = transactionTemplate.execute(status -> checkSubmission(todoId, workItemId, user.getId()));
        requireOperationResult(check).throwIfFailed();

        fileService.validateProofImage(user.getId(), proofImageKey);
        if (todoWorkItemRepository.existsByProofImageKey(proofImageKey)) {
            throw new BusinessException("이미 다른 WorkItem에 제출된 인증 사진입니다.", HttpStatus.BAD_REQUEST);
        }
        String proofThumbnailKey = fileService.createProofThumbnail(proofImageKey);

        OperationResult result;
        try {
            result = transactionTemplate.execute(status -> submitInTransaction(
                    todoId,
                    workItemId,
                    user.getId(),
                    proofImageKey,
                    proofThumbnailKey
            ));
        } catch (RuntimeException e) {
            cleanupProofThumbnail(proofThumbnailKey, e);
            if (e instanceof DataIntegrityViolationException) {
                throw new BusinessException("이미 다른 WorkItem에 제출된 인증 사진입니다.", HttpStatus.BAD_REQUEST);
            }
            throw e;
        }
        result = requireOperationResult(result);
        if (result.hasFailed()) {
            cleanupProofThumbnail(proofThumbnailKey, result.failure());
        }
        result.throwIfFailed();
    }

    private OperationResult checkSubmission(Long todoId, Long workItemId, Long userId) {
        Todo todo = todoRepository.findByIdWithLock(todoId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 투두입니다.", HttpStatus.NOT_FOUND));
        TodoWorkItem workItem = todoWorkItemRepository.findByIdWithLock(workItemId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 WorkItem입니다.", HttpStatus.NOT_FOUND));
        return validateSubmission(todo, workItem, userId);
    }

    private OperationResult submitInTransaction(
            Long todoId,
            Long workItemId,
            Long userId,
            String proofImageKey,
            String proofThumbnailKey
    ) {
        Todo todo = todoRepository.findByIdWithLock(todoId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 투두입니다.", HttpStatus.NOT_FOUND));
        TodoWorkItem workItem = todoWorkItemRepository.findByIdWithLock(workItemId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 WorkItem입니다.", HttpStatus.NOT_FOUND));
        OperationResult validation = validateSubmission(todo, workItem, userId);
        if (validation.hasFailed()) {
            return validation;
        }
        if (todoWorkItemRepository.existsByProofImageKey(proofImageKey)) {
            return OperationResult.failed("이미 다른 WorkItem에 제출된 인증 사진입니다.", HttpStatus.BAD_REQUEST);
        }

        workItem.submit(proofImageKey, proofThumbnailKey);
        todoStatusTransitionService.reevaluate(todo);
        return OperationResult.success();
    }

    private OperationResult validateSubmission(Todo todo, TodoWorkItem workItem, Long userId) {
        if (!todo.getId().equals(workItem.getTodo().getId())) {
            return OperationResult.failed("Todo와 WorkItem이 일치하지 않습니다.", HttpStatus.BAD_REQUEST);
        }
        if (workItem.getAssignee() == null || !userId.equals(workItem.getAssignee().getId())) {
            return OperationResult.failed("해당 WorkItem의 담당자가 아닙니다.", HttpStatus.FORBIDDEN);
        }
        if (workItem.getStatus() != WorkItemStatus.IN_PROGRESS) {
            return OperationResult.failed("이미 제출했거나 종료된 WorkItem입니다.", HttpStatus.CONFLICT);
        }
        if (LocalDateTime.now(KST).isAfter(workItem.getEffectiveDeadline())) {
            todoStatusTransitionService.failOnDeadlinePassed(todo, workItem);
            return OperationResult.failed("마감 시간이 지났습니다.", HttpStatus.BAD_REQUEST);
        }
        return OperationResult.success();
    }

    private AssignmentResult assignWorkItem(Long workItemId, Long todoId, User requester, User assignee) {
        Todo todo = todoRepository.findByIdWithLock(todoId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 투두입니다.", HttpStatus.NOT_FOUND));
        TodoWorkItem workItem = todoWorkItemRepository.findByIdWithLock(workItemId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 WorkItem입니다.", HttpStatus.NOT_FOUND));
        if (workItem.getAssignee() != null) {
            return AssignmentResult.failed("다른 요청이 먼저 담당자를 배정했습니다.", HttpStatus.CONFLICT);
        }
        if (workItem.getStatus() != WorkItemStatus.IN_PROGRESS) {
            return AssignmentResult.failed("진행 중인 WorkItem만 재배정할 수 있습니다.", HttpStatus.BAD_REQUEST);
        }
        if (LocalDateTime.now(KST).isAfter(workItem.getEffectiveDeadline())) {
            todoStatusTransitionService.failOnDeadlinePassed(todo, workItem);
            return AssignmentResult.failed("마감이 지난 WorkItem은 재배정할 수 없습니다.", HttpStatus.BAD_REQUEST);
        }
        if (workItem.getType() == WorkItemType.DIRECT
                && todoWorkItemRepository.findDirectByTodoIdAndAssigneeId(todoId, assignee.getId()).isPresent()) {
            return AssignmentResult.failed("이미 같은 Todo의 DIRECT 담당자입니다.", HttpStatus.CONFLICT);
        }

        workItem.assign(assignee);
        if (!requester.getId().equals(assignee.getId())) {
            notificationService.send(
                    assignee,
                    requester,
                    notificationMessageFactory.todoAssigned(todo.getTitle(), 1),
                    todo.getId()
            );
        }
        return AssignmentResult.success(TodoWorkItemAssigneeResponse.from(workItem));
    }

    private List<TodoSummaryResponse> toSummaryResponses(List<Todo> todos, Long userId) {
        if (todos.isEmpty()) {
            return List.of();
        }
        Map<Long, List<TodoWorkItemSummary>> workItemsByTodoId = todoWorkItemRepository
                .findSummaryByTodoIdIn(todos.stream().map(Todo::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(TodoWorkItemSummary::getTodoId));

        return todos.stream()
                .map(todo -> {
                    List<TodoWorkItemSummary> workItems = workItemsByTodoId.getOrDefault(todo.getId(), List.of());
                    int total = workItems.size();
                    int success = countStatus(workItems, WorkItemStatus.SUCCESS);
                    List<TodoWorkItemSummary> mine = workItems.stream()
                            .filter(workItem -> userId.equals(workItem.getAssigneeId()))
                            .toList();
                    MyWorkSummaryResponse mySummary = MyWorkSummaryResponse.from(
                            todo,
                            mine.size(),
                            countStatus(mine, WorkItemStatus.SUCCESS),
                            countStatus(mine, WorkItemStatus.FAIL),
                            countStatus(mine, WorkItemStatus.IN_PROGRESS)
                    );
                    return TodoSummaryResponse.from(
                            todo,
                            toKstOffset(todo.getDeadline()),
                            success + " / " + total,
                            mySummary
                    );
                })
                .toList();
    }

    private int countStatus(List<TodoWorkItemSummary> workItems, WorkItemStatus status) {
        return Math.toIntExact(workItems.stream().filter(workItem -> workItem.getStatus() == status).count());
    }

    private Map<Long, Map<TodoReactionType, Long>> getReactionCountsByWorkItemId(List<TodoWorkItem> workItems) {
        List<Long> ids = workItems.stream().map(TodoWorkItem::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<TodoReactionType, Long>> result = new HashMap<>();
        for (TodoReactionCount count : todoReactionRepository.countByTodoWorkItemIds(ids)) {
            result.computeIfAbsent(count.getTodoWorkItemId(), ignored -> new EnumMap<>(TodoReactionType.class))
                    .put(count.getReactionType(), count.getReactionCount());
        }
        return result;
    }

    private Map<Long, TodoReactionType> getMyReactionsByWorkItemId(List<TodoWorkItem> workItems, Long userId) {
        List<Long> ids = workItems.stream().map(TodoWorkItem::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return todoReactionRepository.findByTodoWorkItemIdInAndUserId(ids, userId).stream()
                .collect(Collectors.toMap(
                        reaction -> reaction.getTodoWorkItem().getId(),
                        TodoReaction::getReactionType
                ));
    }

    private TodoReaction updateOrDeleteReaction(TodoReaction reaction, TodoReactionType requestedType) {
        if (reaction.hasSameType(requestedType)) {
            todoReactionRepository.delete(reaction);
            return reaction;
        }
        reaction.updateType(requestedType);
        return reaction;
    }

    private List<TodoReportDailyStatResponse> buildDailyStats(
            LocalDate start,
            LocalDate end,
            List<TodoWorkItem> workItems
    ) {
        Map<LocalDate, List<TodoWorkItem>> byDate = workItems.stream()
                .collect(Collectors.groupingBy(workItem -> workItem.getEffectiveDeadline().toLocalDate()));
        List<TodoReportDailyStatResponse> result = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            List<TodoWorkItem> daily = byDate.getOrDefault(date, List.of());
            int success = countEntityStatus(daily, WorkItemStatus.SUCCESS);
            int fail = countEntityStatus(daily, WorkItemStatus.FAIL);
            int inProgress = countEntityStatus(daily, WorkItemStatus.IN_PROGRESS);
            result.add(TodoReportDailyStatResponse.from(
                    date,
                    daily.size(),
                    success,
                    fail,
                    inProgress,
                    calculateAchievementRate(success, daily.size())
            ));
        }
        return result;
    }

    private int countEntityStatus(List<TodoWorkItem> workItems, WorkItemStatus status) {
        return Math.toIntExact(workItems.stream().filter(workItem -> workItem.getStatus() == status).count());
    }

    private TodoReportSummaryResponse buildPeriodSummary(List<TodoReportDailyStatResponse> dailyStats) {
        int total = dailyStats.stream().mapToInt(TodoReportDailyStatResponse::totalTodoCount).sum();
        int success = dailyStats.stream().mapToInt(TodoReportDailyStatResponse::successCount).sum();
        int fail = dailyStats.stream().mapToInt(TodoReportDailyStatResponse::failCount).sum();
        int inProgress = dailyStats.stream().mapToInt(TodoReportDailyStatResponse::inProgressCount).sum();
        return TodoReportSummaryResponse.from(
                total,
                success,
                fail,
                inProgress,
                calculateAchievementRate(success, total)
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

    private List<TodoReportActionCandidateResponse> buildActionCandidates(List<TodoWorkItem> workItems) {
        List<Candidate> candidates = workItems.stream()
                .collect(Collectors.groupingBy(workItem -> workItem.getTodo().getId(), LinkedHashMap::new, Collectors.toList()))
                .values()
                .stream()
                .filter(items -> items.get(0).getTodo().getStatus() == TodoStatus.IN_PROGRESS)
                .map(Candidate::from)
                .sorted(Comparator
                        .comparingInt(Candidate::progressRate)
                        .thenComparing(Comparator.comparingInt(Candidate::unverifiedCount).reversed())
                        .thenComparing(Candidate::deadline))
                .limit(ACTION_CANDIDATE_LIMIT)
                .toList();

        List<TodoReportActionCandidateResponse> result = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            Candidate candidate = candidates.get(index);
            result.add(TodoReportActionCandidateResponse.from(
                    index + 1,
                    candidate.todo().getId(),
                    candidate.todo().getTitle(),
                    toKstOffset(candidate.deadline()),
                    candidate.todo().getStatus(),
                    candidate.successCount(),
                    candidate.totalCount(),
                    candidate.unverifiedCount(),
                    candidate.progressRate()
            ));
        }
        return result;
    }

    private User validateTeamMember(Long teamId, String userId) {
        User user = findAuthenticatedUser(userId);
        if (!teamRepository.existsById(teamId)) {
            throw new BusinessException("존재하지 않는 팀입니다.", HttpStatus.NOT_FOUND);
        }
        requireTeamMember(teamId, user.getId(), "팀에 접근할 권한이 없습니다.");
        return user;
    }

    private User findAuthenticatedUser(String userId) {
        return userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));
    }

    private void requireTeamMember(Long teamId, Long userId, String message) {
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw new BusinessException(message, HttpStatus.FORBIDDEN);
        }
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

    private String achievementCount(List<TodoWorkItem> workItems) {
        long success = workItems.stream().filter(workItem -> workItem.getStatus() == WorkItemStatus.SUCCESS).count();
        return success + " / " + workItems.size();
    }

    private String resolveThumbnailUrl(TodoWorkItem workItem) {
        if (workItem.getProofThumbnailKey() != null && !workItem.getProofThumbnailKey().isBlank()) {
            return fileService.resolveImageUrl(workItem.getProofThumbnailKey());
        }
        return fileService.resolveImageUrl(workItem.getProofImageKey());
    }

    private String resolveCreatorNickname(Todo todo) {
        return todo.getCreator() == null ? WithdrawnUserDisplay.NICKNAME : todo.getCreator().getNickname();
    }

    private Integer calculateAchievementRate(int successCount, int totalCount) {
        return totalCount == 0 ? null : successCount * 100 / totalCount;
    }

    private int sortAchievementRate(Integer achievementRate) {
        return achievementRate == null ? 101 : achievementRate;
    }

    private OffsetDateTime toKstOffset(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atOffset(KST_OFFSET);
    }

    private LocalDateTime toKstLocalDateTime(OffsetDateTime dateTime) {
        return dateTime == null ? null : dateTime.withOffsetSameInstant(KST_OFFSET).toLocalDateTime();
    }

    private void cleanupProofThumbnail(String proofThumbnailKey, RuntimeException primaryFailure) {
        try {
            fileService.deleteObject(proofThumbnailKey);
        } catch (RuntimeException deleteException) {
            primaryFailure.addSuppressed(deleteException);
        }
    }

    private OperationResult requireOperationResult(OperationResult result) {
        if (result == null) {
            throw new BusinessException("제출 처리에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return result;
    }

    private record OperationResult(BusinessException failure) {
        static OperationResult success() {
            return new OperationResult(null);
        }

        static OperationResult failed(String message, HttpStatus status) {
            return new OperationResult(new BusinessException(message, status));
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

    private record AssignmentResult(TodoWorkItemAssigneeResponse response, BusinessException failure) {
        static AssignmentResult success(TodoWorkItemAssigneeResponse response) {
            return new AssignmentResult(response, null);
        }

        static AssignmentResult failed(String message, HttpStatus status) {
            return new AssignmentResult(null, new BusinessException(message, status));
        }

        void throwIfFailed() {
            if (failure != null) {
                throw failure;
            }
        }
    }

    private record Candidate(
            Todo todo,
            LocalDateTime deadline,
            int successCount,
            int totalCount,
            int unverifiedCount,
            int progressRate
    ) {
        static Candidate from(List<TodoWorkItem> workItems) {
            Todo todo = workItems.get(0).getTodo();
            int total = workItems.size();
            int success = Math.toIntExact(workItems.stream()
                    .filter(workItem -> workItem.getStatus() == WorkItemStatus.SUCCESS)
                    .count());
            LocalDateTime deadline = workItems.stream()
                    .filter(workItem -> workItem.getStatus() == WorkItemStatus.IN_PROGRESS)
                    .map(TodoWorkItem::getEffectiveDeadline)
                    .min(LocalDateTime::compareTo)
                    .orElse(todo.getDeadline());
            return new Candidate(
                    todo,
                    deadline,
                    success,
                    total,
                    Math.max(total - success, 0),
                    total == 0 ? 0 : success * 100 / total
            );
        }
    }
}
