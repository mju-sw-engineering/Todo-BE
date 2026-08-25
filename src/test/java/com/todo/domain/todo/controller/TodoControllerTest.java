package com.todo.domain.todo.controller;

import com.todo.domain.todo.dto.request.AssignTodoWorkItemRequest;
import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.request.CreateTodoTaskRequest;
import com.todo.domain.todo.dto.request.ReactTodoRequest;
import com.todo.domain.todo.dto.request.SubmitTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.dto.response.MyWorkSummaryResponse;
import com.todo.domain.todo.dto.response.TodoActivePageResponse;
import com.todo.domain.todo.dto.response.TodoDetailResponse;
import com.todo.domain.todo.dto.response.TodoPeriodReportResponse;
import com.todo.domain.todo.dto.response.TodoReactionResponse;
import com.todo.domain.todo.dto.response.TodoSummaryResponse;
import com.todo.domain.todo.dto.response.TodoTaskResponse;
import com.todo.domain.todo.dto.response.TodoWorkItemAssigneeResponse;
import com.todo.domain.todo.dto.response.TodoWorkItemSubmissionResponse;
import com.todo.domain.todo.entity.TodoMode;
import com.todo.domain.todo.entity.ProofKind;
import com.todo.domain.todo.entity.TodoReactionType;
import com.todo.domain.todo.entity.TodoStatus;
import com.todo.domain.todo.entity.WorkItemStatus;
import com.todo.domain.todo.service.TodoService;
import com.todo.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TodoControllerTest {

    @Mock
    private TodoService todoService;

    @Test
    void DIRECT_투두_생성_응답을_반환한다() {
        TodoController controller = new TodoController(todoService);
        CreateTodoRequest request = directRequest();
        CreateTodoResponse serviceResponse = new CreateTodoResponse(
                1L, TodoMode.DIRECT, "투두", request.deadline(), TodoStatus.IN_PROGRESS, List.of(), List.of()
        );
        given(todoService.createTodo("user1", 10L, request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<CreateTodoResponse>> response = controller.createTodo(10L, request, auth());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getMessage()).isEqualTo("투두가 생성되었습니다.");
        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void TASK_투두_생성_요청을_서비스에_전달한다() {
        TodoController controller = new TodoController(todoService);
        CreateTodoRequest request = taskRequest();
        CreateTodoResponse serviceResponse = new CreateTodoResponse(
                1L, TodoMode.TASK, "발표", request.deadline(), TodoStatus.IN_PROGRESS, List.of(), List.of()
        );
        given(todoService.createTodo("user1", 10L, request)).willReturn(serviceResponse);

        controller.createTodo(10L, request, auth());

        then(todoService).should().createTodo("user1", 10L, request);
    }

    @Test
    void 투두_목록이_비어있으면_조회된_할일_없음_메시지를_반환한다() {
        TodoController controller = new TodoController(todoService);
        given(todoService.getTodoList(10L, "user1", null, null)).willReturn(List.of());

        ResponseEntity<ApiResponse<List<TodoSummaryResponse>>> response =
                controller.getTodoList(10L, null, null, auth());

        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getMessage()).isEqualTo("조회된 할 일이 없습니다");
    }

    @Test
    void 투두_목록이_있으면_구조화된_내_작업_요약과_함께_반환한다() {
        TodoController controller = new TodoController(todoService);
        TodoSummaryResponse summary = summary();
        given(todoService.getTodoList(10L, "user1", "ENDED", null)).willReturn(List.of(summary));

        ResponseEntity<ApiResponse<List<TodoSummaryResponse>>> response =
                controller.getTodoList(10L, "ENDED", null, auth());

        assertThat(response.getBody().getData()).containsExactly(summary);
        assertThat(response.getBody().getData().get(0).myWorkSummary().totalCount()).isEqualTo(2);
    }

    @Test
    void 날짜_조회_결과가_비어있으면_해당날짜_할일_없음_메시지를_반환한다() {
        TodoController controller = new TodoController(todoService);
        given(todoService.getTodoList(10L, "user1", null, "2026-06-04")).willReturn(List.of());

        ResponseEntity<ApiResponse<List<TodoSummaryResponse>>> response =
                controller.getTodoList(10L, null, "2026-06-04", auth());

        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getMessage()).isEqualTo("해당 날짜의 할 일이 없습니다");
    }

    @Test
    void 마감_미경과_목록이_비어있으면_빈_페이지_객체와_조회된_할일_없음_메시지를_반환한다() {
        TodoController controller = new TodoController(todoService);
        TodoActivePageResponse emptyPage = new TodoActivePageResponse(List.of(), false, null);
        given(todoService.getActiveTodoList(10L, "user1", null, null, 20)).willReturn(emptyPage);

        ResponseEntity<ApiResponse<TodoActivePageResponse>> response =
                controller.getActiveTodoList(10L, null, null, 20, auth());

        assertThat(response.getBody().getData()).isEqualTo(emptyPage);
        assertThat(response.getBody().getData().todos()).isEmpty();
        assertThat(response.getBody().getMessage()).isEqualTo("조회된 할 일이 없습니다");
    }

    @Test
    void 마감_미경과_목록은_status_cursor_size_파라미터를_그대로_서비스에_전달한다() {
        TodoController controller = new TodoController(todoService);
        TodoSummaryResponse summary = summary();
        TodoActivePageResponse page = new TodoActivePageResponse(List.of(summary), true, "next-cursor");
        given(todoService.getActiveTodoList(10L, "user1", "PENDING", "cursor-value", 5)).willReturn(page);

        ResponseEntity<ApiResponse<TodoActivePageResponse>> response =
                controller.getActiveTodoList(10L, "PENDING", "cursor-value", 5, auth());

        assertThat(response.getBody().getData()).isEqualTo(page);
        assertThat(response.getBody().getMessage()).isEqualTo("할 일 목록을 조회했습니다");
        then(todoService).should().getActiveTodoList(10L, "user1", "PENDING", "cursor-value", 5);
    }

    @Test
    void 기간_리포트_응답을_반환한다() {
        TodoController controller = new TodoController(todoService);
        TodoPeriodReportResponse serviceResponse = new TodoPeriodReportResponse(null, null, null, List.of(), List.of());
        given(todoService.getTodoPeriodReport(10L, "user1", "2026-06-01", "2026-06-04"))
                .willReturn(serviceResponse);

        ResponseEntity<ApiResponse<TodoPeriodReportResponse>> response =
                controller.getTodoPeriodReport(10L, "2026-06-01", "2026-06-04", auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void TASK_투두_상세_응답을_반환한다() {
        TodoController controller = new TodoController(todoService);
        TodoDetailResponse serviceResponse = new TodoDetailResponse(
                1L, TodoMode.TASK, "투두", "설명", OffsetDateTime.parse("2026-06-04T12:00:00+09:00"), "닉네임",
                TodoStatus.IN_PROGRESS, "0 / 1", List.of(), List.of(task())
        );
        given(todoService.getTodoDetail(1L, "user1")).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<TodoDetailResponse>> response = controller.getTodoDetail(1L, auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
        assertThat(response.getBody().getData().tasks()).hasSize(1);
    }

    @Test
    void deprecated_참여자_반응_경로를_계속_전달한다() {
        TodoController controller = new TodoController(todoService);
        ReactTodoRequest request = new ReactTodoRequest(TodoReactionType.LIKE);
        TodoReactionResponse serviceResponse = TodoReactionResponse.from(TodoReactionType.LIKE, 3);
        given(todoService.reactTodoParticipant(1L, "user1", request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<TodoReactionResponse>> response =
                controller.reactTodoParticipant(1L, request, auth());

        assertThat(response.getBody().getMessage()).isEqualTo("이모지 반응이 반영되었습니다.");
        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void WorkItem_반응_경로를_서비스에_전달한다() {
        TodoController controller = new TodoController(todoService);
        ReactTodoRequest request = new ReactTodoRequest(TodoReactionType.LIKE);
        TodoReactionResponse serviceResponse = TodoReactionResponse.from(TodoReactionType.LIKE, 3);
        given(todoService.reactTodoWorkItem(11L, "user1", request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<TodoReactionResponse>> response =
                controller.reactTodoWorkItem(11L, request, auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
        then(todoService).should().reactTodoWorkItem(11L, "user1", request);
    }

    @Test
    void DIRECT_투두_제출_별칭을_서비스에_전달한다() {
        TodoController controller = new TodoController(todoService);
        SubmitTodoRequest request = new SubmitTodoRequest("proofs/1/a.png", null);

        ResponseEntity<ApiResponse<Void>> response = controller.submitTodo(1L, request, auth());

        assertThat(response.getBody().getMessage()).isEqualTo("인증 파일이 제출되었습니다.");
        then(todoService).should().submitTodo(1L, "user1", request);
    }

    @Test
    void TASK_WorkItem_제출_경로를_서비스에_전달한다() {
        TodoController controller = new TodoController(todoService);
        SubmitTodoRequest request = new SubmitTodoRequest("proofs/1/a.png", null);

        ResponseEntity<ApiResponse<Void>> response = controller.submitTodoWorkItem(11L, request, auth());

        assertThat(response.getBody().getMessage()).isEqualTo("인증 파일이 제출되었습니다.");
        then(todoService).should().submitTodoWorkItem(11L, "user1", request);
    }

    @Test
    void WorkItem_제출_사진_URL을_반환한다() {
        TodoController controller = new TodoController(todoService);
        TodoWorkItemSubmissionResponse serviceResponse = new TodoWorkItemSubmissionResponse(
                11L, 2L, OffsetDateTime.parse("2026-06-04T12:00:00+09:00"), false,
                ProofKind.DOCUMENT, "발표자료.pdf", "application/pdf",
                "https://original", null, OffsetDateTime.parse("2026-06-04T13:00:00+09:00"),
                null
        );
        given(todoService.getTodoWorkItemSubmission(11L, "user1")).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<TodoWorkItemSubmissionResponse>> response =
                controller.getTodoWorkItemSubmission(11L, auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 미배정_WorkItem_재배정을_서비스에_전달한다() {
        TodoController controller = new TodoController(todoService);
        AssignTodoWorkItemRequest request = new AssignTodoWorkItemRequest(3L);
        TodoWorkItemAssigneeResponse serviceResponse = new TodoWorkItemAssigneeResponse(
                11L, 3L, "종혁", WorkItemStatus.IN_PROGRESS
        );
        given(todoService.reassignTodoWorkItem(11L, "user1", request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<TodoWorkItemAssigneeResponse>> response =
                controller.reassignTodoWorkItem(11L, request, auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
        assertThat(response.getBody().getMessage()).isEqualTo("담당자가 재배정되었습니다.");
    }

    private CreateTodoRequest directRequest() {
        return new CreateTodoRequest(
                "투두",
                null,
                OffsetDateTime.parse("2026-06-04T12:00:00+09:00"),
                List.of(1L),
                null
        );
    }

    private CreateTodoRequest taskRequest() {
        return new CreateTodoRequest(
                "발표",
                "준비",
                OffsetDateTime.parse("2026-06-04T18:00:00+09:00"),
                null,
                List.of(new CreateTodoTaskRequest(
                        "회의록",
                        null,
                        1L,
                        OffsetDateTime.parse("2026-06-04T15:00:00+09:00")
                ))
        );
    }

    private TodoSummaryResponse summary() {
        return new TodoSummaryResponse(
                1L,
                TodoMode.TASK,
                "투두",
                "설명",
                OffsetDateTime.parse("2026-06-04T12:00:00+09:00"),
                TodoStatus.IN_PROGRESS,
                "1 / 2",
                List.of(),
                new MyWorkSummaryResponse(2, 1, 0, 1)
        );
    }

    private TodoTaskResponse task() {
        return new TodoTaskResponse(
                11L,
                "회의록",
                null,
                1L,
                "닉네임",
                OffsetDateTime.parse("2026-06-04T12:00:00+09:00"),
                0,
                WorkItemStatus.IN_PROGRESS,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false
        );
    }

    private TestingAuthenticationToken auth() {
        return new TestingAuthenticationToken("user1", null);
    }
}
