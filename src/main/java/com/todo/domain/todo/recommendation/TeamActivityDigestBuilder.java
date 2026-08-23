package com.todo.domain.todo.recommendation;

import com.todo.domain.team.entity.Team;
import com.todo.domain.team.entity.TeamMember;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.team.repository.TeamRepository;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.entity.TodoWorkItem;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.recommendation.holiday.Holiday;
import com.todo.domain.todo.recommendation.holiday.HolidayProvider;
import com.todo.domain.todo.repository.CheckInActivityRecord;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.todo.repository.TodoWorkItemRepository;
import com.todo.domain.todo.repository.WorkItemCheckInRepository;
import com.todo.domain.user.entity.User;
import com.todo.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 팀 활동을 모델이 읽을 짧은 요약 텍스트로 만든다.
 *
 * <p><b>원시 행을 던지지 않는다.</b> 집계는 여기서 하고 모델에는 결과만 준다 — 토큰 비용과
 * 인젝션 면적 둘 다 이유다. 각 목록은 {@link #LIST_CAP}건으로 자른다.
 *
 * <p>"2주째 밀림" 같은 패턴 탐지는 일부러 모델에 맡긴다. 실패 목록에 제목과 날짜가 있으면
 * 유사 제목의 반복 실패를 모델이 짚을 수 있고, Java 제목 유사도 휴리스틱보다 오타·표현 차이에
 * 강하다. (이 데이터 모델에 "미루기"는 없다 — 마감을 넘기면 FAIL로 확정된다. "밀린다"는
 * 곧 "같은 제목이 연속으로 FAIL"이다.)
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TeamActivityDigestBuilder {

    static final int LOOKBACK_DAYS = 28;
    static final int HOLIDAY_LOOKAHEAD_DAYS = 14;
    static final int LIST_CAP = 20;
    static final int CHECK_IN_CAP = 5;
    /**
     * 요일 패턴을 모델에 보여주기 위한 최소 표본. 완료·실패 항목이 이보다 적으면 섹션을 아예
     * 생략한다 — "화 1/1"을 보여주면 모델이 한 건을 "화요일에 강한 팀"으로 일반화한다.
     */
    static final int WEEKDAY_PATTERN_MIN_SAMPLES = 5;

    /** 모델이 데이터와 지시를 헷갈리지 않게 감싸는 구분자. 프롬프트가 이 태그를 언급한다. */
    static final String DATA_OPEN = "<team_data>";
    static final String DATA_CLOSE = "</team_data>";

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int TITLE_CAP = 40;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TodoRepository todoRepository;
    private final TodoWorkItemRepository todoWorkItemRepository;
    private final WorkItemCheckInRepository workItemCheckInRepository;
    private final HolidayProvider holidayProvider;

    public TeamActivityDigest build(Long teamId, LocalDate today) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new BusinessException("존재하지 않는 팀입니다.", HttpStatus.NOT_FOUND));
        List<TeamMember> members = teamMemberRepository.findByTeamIdWithUser(teamId);
        Map<Long, String> nicknames = members.stream()
                .map(TeamMember::getUser)
                .collect(Collectors.toMap(User::getId, User::getNickname, (a, b) -> a, LinkedHashMap::new));

        List<Todo> allTodos = todoRepository.findByTeamIdWithCreator(teamId);
        RecommendationMode mode = decideMode(team, allTodos);
        List<Holiday> holidays = holidayProvider.holidaysBetween(today, today.plusDays(HOLIDAY_LOOKAHEAD_DAYS));

        if (mode == RecommendationMode.NONE) {
            return new TeamActivityDigest(mode, "", today, members.size(), nicknames, Set.of(), holidays);
        }

        LocalDateTime since = today.minusDays(LOOKBACK_DAYS).atStartOfDay();
        List<Todo> inProgress = allTodos.stream()
                .filter(t -> t.getStatus() == TodoStatus.IN_PROGRESS)
                .sorted(Comparator.comparing(Todo::getDeadline))
                .limit(LIST_CAP)
                .toList();
        List<Todo> recentSuccess = recent(allTodos, TodoStatus.SUCCESS, since);
        List<Todo> recentFail = recent(allTodos, TodoStatus.FAIL, since);

        List<Long> selectedIds = new ArrayList<>();
        inProgress.forEach(t -> selectedIds.add(t.getId()));
        recentSuccess.forEach(t -> selectedIds.add(t.getId()));
        recentFail.forEach(t -> selectedIds.add(t.getId()));
        Map<Long, List<TodoWorkItem>> workItemsByTodo = selectedIds.isEmpty()
                ? Map.of()
                : todoWorkItemRepository.findByTodoIdInOrderByTodoIdAndPosition(selectedIds).stream()
                        .collect(Collectors.groupingBy(wi -> wi.getTodo().getId()));

        StringBuilder text = new StringBuilder();
        appendTeam(text, team, members.size(), allTodos.size(), today);
        appendTodos(text, "[진행 중]", inProgress, workItemsByTodo, true);
        if (mode == RecommendationMode.FULL) {
            appendTodos(text, "[최근 4주 성공]", recentSuccess, workItemsByTodo, false);
            appendTodos(text, "[최근 4주 실패]", recentFail, workItemsByTodo, false);
            appendMemberLoad(text, nicknames, inProgress, recentSuccess, recentFail, workItemsByTodo);
            appendWeekdayPattern(text, recentSuccess, recentFail, workItemsByTodo);
            appendCheckIns(text, teamId, today, allTodos);
        }
        appendCalendar(text, today, holidays);

        Set<Long> todoIds = allTodos.stream().map(Todo::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        return new TeamActivityDigest(
                mode,
                DATA_OPEN + "\n" + text.toString().strip() + "\n" + DATA_CLOSE,
                today,
                members.size(),
                nicknames,
                todoIds,
                holidays
        );
    }

    /**
     * 기준은 "기록의 양"이 아니라 "재료의 존재"다. 투두가 하나라도 있으면 FULL — 진행 중 10개인
     * 팀은 실패 기록이 없어도 부하·미배정 분석(REBALANCE)이 가능하다. 완료 개수 문턱을 두지
     * 않는 대신, 없는 신호는 요약에서 섹션째 빠지고 프롬프트가 "근거 없으면 빈 배열"을 강제하며,
     * 한 건짜리 기록의 과잉 일반화는 프롬프트 규칙과 {@link #WEEKDAY_PATTERN_MIN_SAMPLES}가 막는다.
     */
    private RecommendationMode decideMode(Team team, List<Todo> allTodos) {
        if (!allTodos.isEmpty()) {
            return RecommendationMode.FULL;
        }
        boolean hasDescription = team.getDescription() != null && !team.getDescription().isBlank();
        return hasDescription ? RecommendationMode.STARTER : RecommendationMode.NONE;
    }

    private List<Todo> recent(List<Todo> allTodos, TodoStatus status, LocalDateTime since) {
        return allTodos.stream()
                .filter(t -> t.getStatus() == status && !t.getDeadline().isBefore(since))
                .sorted(Comparator.comparing(Todo::getDeadline).reversed())
                .limit(LIST_CAP)
                .toList();
    }

    private void appendTeam(StringBuilder text, Team team, int memberCount, int totalTodos, LocalDate today) {
        text.append("[팀] ").append(sanitize(team.getTeamName(), TITLE_CAP))
                .append(" · 팀원 ").append(memberCount).append("명")
                .append(" · 전체 투두 ").append(totalTodos).append("개");
        if (team.getCreatedAt() != null) {
            text.append(" · 시작 ").append(team.getCreatedAt().toLocalDate().format(FULL_DATE));
        }
        text.append('\n');
        if (team.getDescription() != null && !team.getDescription().isBlank()) {
            text.append("[팀 설명] ").append(sanitize(team.getDescription(), 120)).append('\n');
        }
    }

    private void appendTodos(
            StringBuilder text,
            String heading,
            List<Todo> todos,
            Map<Long, List<TodoWorkItem>> workItemsByTodo,
            boolean showUnassigned
    ) {
        text.append(heading);
        if (todos.isEmpty()) {
            text.append(" 없음\n");
            return;
        }
        text.append('\n');
        for (Todo todo : todos) {
            List<TodoWorkItem> items = workItemsByTodo.getOrDefault(todo.getId(), List.of());
            long verified = items.stream().filter(wi -> wi.getStatus() == WorkItemStatus.SUCCESS).count();
            long unassigned = items.stream()
                    .filter(wi -> wi.getStatus() == WorkItemStatus.IN_PROGRESS && wi.getAssignee() == null)
                    .count();
            text.append("- #").append(todo.getId()).append(' ').append(sanitize(todo.getTitle(), TITLE_CAP))
                    .append(" (마감 ").append(formatDeadline(todo.getDeadline()))
                    .append(", 참여 ").append(items.size())
                    .append(", 인증 ").append(verified);
            if (showUnassigned && unassigned > 0) {
                text.append(", 미배정 ").append(unassigned);
            }
            text.append(")\n");
        }
    }

    private void appendMemberLoad(
            StringBuilder text,
            Map<Long, String> nicknames,
            List<Todo> inProgress,
            List<Todo> recentSuccess,
            List<Todo> recentFail,
            Map<Long, List<TodoWorkItem>> workItemsByTodo
    ) {
        if (nicknames.size() < 2) {
            return;
        }
        Map<Long, int[]> load = new HashMap<>();
        nicknames.keySet().forEach(id -> load.put(id, new int[3]));
        countByAssignee(inProgress, workItemsByTodo, WorkItemStatus.IN_PROGRESS, load, 0);
        countByAssignee(recentSuccess, workItemsByTodo, WorkItemStatus.SUCCESS, load, 1);
        countByAssignee(recentFail, workItemsByTodo, WorkItemStatus.FAIL, load, 2);

        text.append("[팀원별 부하] (진행 중 / 4주 성공 / 4주 실패)\n");
        nicknames.forEach((id, nickname) -> {
            int[] counts = load.get(id);
            text.append("- ").append(sanitize(nickname, 20)).append(": ")
                    .append(counts[0]).append(" / ").append(counts[1]).append(" / ").append(counts[2]).append('\n');
        });
    }

    private void countByAssignee(
            List<Todo> todos,
            Map<Long, List<TodoWorkItem>> workItemsByTodo,
            WorkItemStatus status,
            Map<Long, int[]> load,
            int slot
    ) {
        for (Todo todo : todos) {
            for (TodoWorkItem item : workItemsByTodo.getOrDefault(todo.getId(), List.of())) {
                if (item.getStatus() != status || item.getAssignee() == null) {
                    continue;
                }
                int[] counts = load.get(item.getAssignee().getId());
                if (counts != null) {
                    counts[slot]++;
                }
            }
        }
    }

    /** 마감 요일별 제출 성공 수 / 마감 수. "성공률 높은 요일 우선" 규칙의 근거다. */
    private void appendWeekdayPattern(
            StringBuilder text,
            List<Todo> recentSuccess,
            List<Todo> recentFail,
            Map<Long, List<TodoWorkItem>> workItemsByTodo
    ) {
        Map<DayOfWeek, int[]> byDay = new EnumMap<>(DayOfWeek.class);
        for (Todo todo : concat(recentSuccess, recentFail)) {
            for (TodoWorkItem item : workItemsByTodo.getOrDefault(todo.getId(), List.of())) {
                if (item.getStatus() == WorkItemStatus.IN_PROGRESS) {
                    continue;
                }
                int[] counts = byDay.computeIfAbsent(item.getEffectiveDeadline().getDayOfWeek(), d -> new int[2]);
                counts[1]++;
                if (item.getStatus() == WorkItemStatus.SUCCESS) {
                    counts[0]++;
                }
            }
        }
        int samples = byDay.values().stream().mapToInt(c -> c[1]).sum();
        if (samples < WEEKDAY_PATTERN_MIN_SAMPLES) {
            return;
        }
        text.append("[마감 요일별 성공/전체] ");
        text.append(byDay.entrySet().stream()
                .map(e -> koreanDay(e.getKey()) + " " + e.getValue()[0] + "/" + e.getValue()[1])
                .collect(Collectors.joining(", ")));
        text.append('\n');
    }

    private void appendCheckIns(StringBuilder text, Long teamId, LocalDate today, List<Todo> allTodos) {
        List<CheckInActivityRecord> records =
                workItemCheckInRepository.findActivityByTeamId(teamId, today.minusDays(LOOKBACK_DAYS));
        if (records.isEmpty()) {
            return;
        }
        Map<Long, String> titles = allTodos.stream()
                .collect(Collectors.toMap(Todo::getId, Todo::getTitle, (a, b) -> a));
        Map<Long, Long> countByTodo = records.stream()
                .collect(Collectors.groupingBy(CheckInActivityRecord::getTodoId, Collectors.counting()));
        text.append("[최근 4주 체크인 많은 투두] ");
        text.append(countByTodo.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(CHECK_IN_CAP)
                .map(e -> "#" + e.getKey() + " " + sanitize(titles.getOrDefault(e.getKey(), "?"), TITLE_CAP)
                        + " " + e.getValue() + "회")
                .collect(Collectors.joining(", ")));
        text.append('\n');
    }

    /**
     * 공휴일 조회가 "설정 없음"인지 "없음"인지를 구분해 적는다. 둘을 섞으면 모델이 "공휴일이
     * 없다"고 단정하고 연휴 한가운데에 마감을 잡을 수 있다.
     */
    private void appendCalendar(StringBuilder text, LocalDate today, List<Holiday> holidays) {
        text.append("[달력] 오늘 ").append(today.format(FULL_DATE)).append('(').append(koreanDay(today.getDayOfWeek())).append(')');
        text.append(" · ").append(HOLIDAY_LOOKAHEAD_DAYS).append("일 내 공휴일: ");
        if (!holidayProvider.isAvailable()) {
            text.append("정보 없음");
        } else if (holidays.isEmpty()) {
            text.append("없음");
        } else {
            text.append(holidays.stream()
                    .map(h -> h.date().format(MONTH_DAY) + "(" + koreanDay(h.date().getDayOfWeek()) + ") " + h.name())
                    .collect(Collectors.joining(", ")));
        }
        text.append('\n');
    }

    private static List<Todo> concat(List<Todo> a, List<Todo> b) {
        List<Todo> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static String formatDeadline(LocalDateTime deadline) {
        return deadline.toLocalDate().format(MONTH_DAY) + "(" + koreanDay(deadline.getDayOfWeek()) + ")";
    }

    static String koreanDay(DayOfWeek day) {
        return day.getDisplayName(TextStyle.SHORT, Locale.KOREAN);
    }

    /**
     * 사용자 입력을 한 줄로 눌러 넣는다. 줄바꿈과 구분자 태그를 지워 "데이터 안에서 지시를
     * 시작하는" 모양을 만들 수 없게 한다. 길이는 토큰 비용 때문에 자른다.
     */
    static String sanitize(String raw, int cap) {
        if (raw == null) {
            return "";
        }
        String oneLine = raw.replaceAll("[\\r\\n\\t]+", " ")
                .replace(DATA_OPEN, "")
                .replace(DATA_CLOSE, "")
                .replaceAll("\\s{2,}", " ")
                .strip();
        return oneLine.length() > cap ? oneLine.substring(0, cap) + "…" : oneLine;
    }
}
