package com.todo.domain.evaluation.service;

import com.todo.domain.evaluation.client.AiDailyEvaluationClient;
import com.todo.domain.evaluation.dto.request.AiDailyEvaluationRequest;
import com.todo.domain.evaluation.dto.response.AiDailyEvaluationResponse;
import com.todo.domain.evaluation.dto.response.DailyEvaluationResponse;
import com.todo.domain.evaluation.entity.DailyEvaluation;
import com.todo.domain.evaluation.repository.DailyEvaluationRepository;
import com.todo.domain.team.entity.AiPersona;
import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.repository.TodoDailyEvaluationStat;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.entity.User;
import com.todo.domain.user.repository.UserRepository;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyEvaluationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int FALLBACK_LOOKBACK_DAYS = 14;

    private final DailyEvaluationRepository dailyEvaluationRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final TodoRepository todoRepository;
    private final AiDailyEvaluationClient aiDailyEvaluationClient;

    @Transactional
    public DailyEvaluationResponse getDailyEvaluation(Long teamId, String loginId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new BusinessException("로그인이 필요합니다", HttpStatus.UNAUTHORIZED));
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 팀입니다.", HttpStatus.NOT_FOUND));

        if (!teamMemberRepository.existsByTeamIdAndUserId(teamId, user.getId())) {
            throw new BusinessException("팀에 접근할 권한이 없습니다.", HttpStatus.FORBIDDEN);
        }

        LocalDate evaluationDate = LocalDate.now(KST).minusDays(1);
        Optional<DailyEvaluation> savedEvaluation = dailyEvaluationRepository.findByTeamIdAndEvaluationDate(teamId, evaluationDate);
        if (savedEvaluation.isPresent() && savedEvaluation.get().getPersona() == team.getAiPersona()) {
            return DailyEvaluationResponse.from(savedEvaluation.get());
        }

        return createOrUpdateEvaluation(team, evaluationDate, savedEvaluation.orElse(null));
    }

    private DailyEvaluationResponse createOrUpdateEvaluation(
            Team team,
            LocalDate evaluationDate,
            DailyEvaluation savedEvaluation
    ) {
        todoRepository.markExpiredTodosAsFail(LocalDateTime.now(KST));

        List<TodoDailyEvaluationStat> todos = todoRepository.findDailyEvaluationStats(
                team.getId(),
                evaluationDate.atStartOfDay(),
                evaluationDate.plusDays(1).atStartOfDay()
        );
        LocalDate basisDate = evaluationDate;
        boolean fallback = false;

        if (todos.isEmpty()) {
            basisDate = todoRepository.findLatestDailyEvaluationTodoDate(
                    team.getId(),
                    evaluationDate.minusDays(FALLBACK_LOOKBACK_DAYS).atStartOfDay(),
                    evaluationDate.atStartOfDay()
            ).orElseThrow(() -> new BusinessException("최근 투두가 없어 평가를 생성할 수 없습니다.", HttpStatus.NOT_FOUND));
            todos = todoRepository.findDailyEvaluationStats(
                    team.getId(),
                    basisDate.atStartOfDay(),
                    basisDate.plusDays(1).atStartOfDay()
            );
            fallback = true;
        }

        if (todos.isEmpty()) {
            throw new BusinessException("최근 투두가 없어 평가를 생성할 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        AiPersona persona = team.getAiPersona();
        AiDailyEvaluationRequest request = buildAiRequest(team, evaluationDate, basisDate, fallback, persona, todos);
        AiDailyEvaluationResponse aiResponse = aiDailyEvaluationClient.createDailyEvaluation(request);

        if (savedEvaluation != null) {
            savedEvaluation.updateEvaluation(persona, aiResponse.message());
            return DailyEvaluationResponse.from(savedEvaluation);
        }

        try {
            DailyEvaluation evaluation = dailyEvaluationRepository.saveAndFlush(DailyEvaluation.create(
                    team,
                    evaluationDate,
                    persona,
                    aiResponse.message()
            ));
            return DailyEvaluationResponse.from(evaluation);
        } catch (DataIntegrityViolationException e) {
            DailyEvaluation evaluation = dailyEvaluationRepository.findByTeamIdAndEvaluationDate(team.getId(), evaluationDate)
                    .orElseThrow(() -> new BusinessException("AI 평가 저장에 실패했습니다.", HttpStatus.INTERNAL_SERVER_ERROR));
            if (evaluation.getPersona() != persona) {
                evaluation.updateEvaluation(persona, aiResponse.message());
            }
            return DailyEvaluationResponse.from(evaluation);
        }
    }

    private AiDailyEvaluationRequest buildAiRequest(
            Team team,
            LocalDate evaluationDate,
            LocalDate basisDate,
            boolean fallback,
            AiPersona persona,
            List<TodoDailyEvaluationStat> todos
    ) {
        long totalTodoCount = todos.size();
        long successCount = countByStatus(todos, TodoStatus.SUCCESS);
        long failCount = countByStatus(todos, TodoStatus.FAIL);
        long inProgressCount = countByStatus(todos, TodoStatus.IN_PROGRESS);
        int achievementRate = (int) ((successCount * 100) / totalTodoCount);

        AiDailyEvaluationRequest.Summary summary = new AiDailyEvaluationRequest.Summary(
                totalTodoCount,
                successCount,
                failCount,
                inProgressCount,
                achievementRate,
                team.getConsecutiveTodoCount()
        );

        List<AiDailyEvaluationRequest.TodoInfo> todoInfos = todos.stream()
                .map(todo -> new AiDailyEvaluationRequest.TodoInfo(
                        todo.getTitle(),
                        todo.getStatus(),
                        todo.getDeadline(),
                        todo.getAchievementCount(),
                        todo.getParticipantCount()
                ))
                .toList();

        return new AiDailyEvaluationRequest(team.getId(), evaluationDate, basisDate, fallback, persona, summary, todoInfos);
    }

    private long countByStatus(List<TodoDailyEvaluationStat> todos, TodoStatus status) {
        return todos.stream()
                .filter(todo -> todo.getStatus() == status)
                .count();
    }
}
