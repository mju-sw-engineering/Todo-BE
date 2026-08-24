package com.todo.domain.todo.recommendation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.domain.chat.command.SlashCommand;
import com.todo.domain.chat.command.entity.SlashCommandExecution;
import com.todo.domain.chat.command.entity.SlashCommandExecutionStatus;
import com.todo.domain.chat.command.repository.SlashCommandExecutionRepository;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.recommendation.dto.TeamTodoRecommendationItem;
import com.todo.domain.todo.recommendation.dto.TeamTodoRecommendationResult;
import com.todo.domain.todo.service.TodoService;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 추천 카드의 [등록]을 처리한다.
 *
 * <p>클라이언트가 기존 Todo 생성 API를 직접 부르지 않고 이 경로를 두는 이유는 두 가지다.
 * 팀원 둘이 같은 카드를 동시에 누르면 투두가 두 개 생기고, 카드에 "등록됨"을 남길 방법이 없다.
 * 실행 행을 잠그고 결과 JSON을 갱신해 둘 다 해결한다.
 *
 * <p>투두 생성 자체는 {@link TodoService#createTodo}를 그대로 쓴다 — 알림·이벤트 같은 부수 효과가
 * 평소 생성과 같아야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamTodoRecommendationRegisterService {

    private final SlashCommandExecutionRepository executionRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final TodoService todoService;
    private final ObjectMapper objectMapper;

    @Transactional
    public CreateTodoResponse register(Long teamId, String userId, Long messageId, int index) {
        User registrant = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다.", HttpStatus.UNAUTHORIZED));
        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, registrant.getId())) {
            throw new BusinessException("팀에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        // 잠금 조회 — 동시 클릭을 직렬화한다. 두 번째 요청은 갱신된 결과를 보고 409로 떨어진다.
        SlashCommandExecution execution = executionRepository
                .findByChatMessageIdAndTeamIdForUpdate(messageId, teamId)
                .filter(e -> e.getCommand() == SlashCommand.TODO_RECOMMENDATION)
                .filter(e -> e.getStatus() == SlashCommandExecutionStatus.DONE)
                .orElseThrow(() -> new BusinessException("추천 결과를 찾을 수 없습니다.", HttpStatus.NOT_FOUND));

        TeamTodoRecommendationResult result = readResult(execution);
        if (index < 0 || index >= result.items().size()) {
            throw new BusinessException("추천 항목을 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
        TeamTodoRecommendationItem item = result.items().get(index);
        if (item.isRegistered()) {
            throw new BusinessException("이미 등록된 추천입니다.", HttpStatus.CONFLICT);
        }

        List<Long> assigneeIds = item.suggestedAssigneeIds().isEmpty()
                ? List.of(registrant.getId())
                : item.suggestedAssigneeIds();
        CreateTodoResponse created = todoService.createTodo(userId, teamId, new CreateTodoRequest(
                item.title(),
                item.description() == null || item.description().isBlank() ? null : item.description(),
                item.suggestedDeadline(),
                assigneeIds,
                null
        ));

        execution.updateResult(writeResult(result.withItem(
                index, item.withRegistration(created.todoId(), registrant.getId(), registrant.getNickname()))));
        log.info("추천 카드에서 투두를 등록했습니다. teamId={}, messageId={}, index={}, todoId={}",
                teamId, messageId, index, created.todoId());
        return created;
    }

    private TeamTodoRecommendationResult readResult(SlashCommandExecution execution) {
        try {
            return objectMapper.readValue(execution.getResultJson(), TeamTodoRecommendationResult.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BusinessException("추천 결과를 읽지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String writeResult(TeamTodoRecommendationResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new BusinessException("추천 결과를 저장하지 못했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
