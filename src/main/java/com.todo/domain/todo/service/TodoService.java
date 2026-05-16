package com.todo.domain.todo.service;

import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.request.SubmitTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.dto.response.ParticipantDetailResponse;
import com.todo.domain.todo.dto.response.TodoDetailResponse;
import com.todo.domain.todo.dto.response.TodoSummaryResponse;
import com.todo.domain.todo.entity.ParticipantStatus;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoParticipant;
import com.todo.domain.todo.entity.TodoParticipant;
import com.todo.domain.todo.repository.TodoParticipantDetail;
import com.todo.domain.todo.repository.TodoParticipantRepository;
import com.todo.domain.todo.repository.TodoParticipantSummary;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import com.todo.global.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoService {

    private final TodoRepository todoRepository;
    private final TodoParticipantRepository todoParticipantRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final FileService fileService;

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
                request.deadline()
        ));

        List<Long> assigneeIds = request.assigneeIds();
        for (Long assigneeId : assigneeIds) {
            User assignee = userRepository.findById(assigneeId)
                    .orElseThrow(() -> new BusinessException("존재하지 않는 사용자입니다: " + assigneeId, HttpStatus.NOT_FOUND));
            todoParticipantRepository.save(TodoParticipant.create(todo, assignee));
        }

        return CreateTodoResponse.from(todo, assigneeIds);
    }

    public List<TodoSummaryResponse> getTodoList(Long teamId, String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        if (!teamRepository.existsById(teamId)) {
            throw new BusinessException("존재하지 않는 팀입니다.", HttpStatus.NOT_FOUND);
        }
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, user.getId())) {
            throw new BusinessException("팀에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        List<Todo> todos = todoRepository.findByTeamIdWithCreator(teamId);
        if (todos.isEmpty()) {
            return List.of();
        }

        List<Long> todoIds = todos.stream().map(Todo::getId).toList();
        Map<Long, List<TodoParticipantSummary>> participantsByTodoId = todoParticipantRepository
                .findSummaryByTodoIdIn(todoIds)
                .stream()
                .collect(Collectors.groupingBy(TodoParticipantSummary::getTodoId));

        return todos.stream()
                .map(todo -> {
                    List<TodoParticipantSummary> participants = participantsByTodoId.getOrDefault(todo.getId(), List.of());
                    long total = participants.size();
                    long success = participants.stream()
                            .filter(p -> p.getStatus() == ParticipantStatus.SUCCESS)
                            .count();
                    String myStatus = participants.stream()
                            .filter(p -> p.getUserId().equals(user.getId()))
                            .findFirst()
                            .map(p -> mapStatus(p.getStatus()))
                            .orElse(null);

                    return new TodoSummaryResponse(
                            todo.getId(),
                            todo.getTitle(),
                            todo.getDeadline(),
                            todo.getCreator().getNickname(),
                            todo.getStatus(),
                            success + " / " + total,
                            myStatus
                    );
                })
                .toList();
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
                todo.getDeadline(),
                todo.getCreator().getNickname(),
                todo.getStatus(),
                success + " / " + total,
                participantResponses
        );
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public void submitTodo(Long todoId, String loginId, SubmitTodoRequest request) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("사용자를 찾을 수 없습니다.", HttpStatus.UNAUTHORIZED));

        TodoParticipant participant = todoParticipantRepository
                .findByTodoIdAndUserIdWithTodo(todoId, user.getId())
                .orElseThrow(() -> new BusinessException("해당 투두의 배정자가 아닙니다.", HttpStatus.FORBIDDEN));

        if (LocalDateTime.now().isAfter(participant.getTodo().getDeadline())) {
            participant.markAsFail();
            throw new BusinessException("마감 시간이 지났습니다.", HttpStatus.BAD_REQUEST);
        }

        participant.submit(request.proofImageKey());
    }

    private String mapStatus(ParticipantStatus status) {
        return switch (status) {
            case SUCCESS -> "완료";
            case PENDING -> "평가 대기중";
            case IN_PROGRESS -> "미완료";
            case FAIL -> "실패";
        };
    }
}
