package com.todo.domain.todo.controller;

import com.todo.domain.todo.dto.request.CreateTodoRequest;
import com.todo.domain.todo.dto.request.ReactTodoRequest;
import com.todo.domain.todo.dto.request.SubmitTodoRequest;
import com.todo.domain.todo.dto.response.CreateTodoResponse;
import com.todo.domain.todo.dto.response.TodoDetailResponse;
import com.todo.domain.todo.dto.response.TodoPeriodReportResponse;
import com.todo.domain.todo.dto.response.TodoReactionResponse;
import com.todo.domain.todo.dto.response.TodoSummaryResponse;
import com.todo.domain.todo.entity.TodoReactionType;
import com.todo.domain.todo.entity.TodoStatus;
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
    void 투두_생성_응답을_반환한다() {
        TodoController controller = new TodoController(todoService);
        CreateTodoRequest request = createRequest();
        CreateTodoResponse serviceResponse = new CreateTodoResponse(
                1L, 10L, 1L, "투두", null, request.deadline(), TodoStatus.IN_PROGRESS, List.of(1L), null
        );
        given(todoService.createTodo("user1", 10L, request)).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<CreateTodoResponse>> response = controller.createTodo(10L, request, auth());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getMessage()).isEqualTo("투두가 생성되었습니다.");
        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 투두_목록이_비어있으면_오늘_할일_없음_메시지를_반환한다() {
        TodoController controller = new TodoController(todoService);
        given(todoService.getTodoList(10L, "user1", null)).willReturn(List.of());

        ResponseEntity<ApiResponse<List<TodoSummaryResponse>>> response =
                controller.getTodoList(10L, null, auth());

        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getMessage()).isEqualTo("오늘 할 일이 없습니다");
    }

    @Test
    void 투두_목록이_있으면_목록을_반환한다() {
        TodoController controller = new TodoController(todoService);
        TodoSummaryResponse summary = summary();
        given(todoService.getTodoList(10L, "user1", "ENDED")).willReturn(List.of(summary));

        ResponseEntity<ApiResponse<List<TodoSummaryResponse>>> response =
                controller.getTodoList(10L, "ENDED", auth());

        assertThat(response.getBody().getData()).containsExactly(summary);
    }

    @Test
    void 오늘_투두가_비어있으면_오늘_할일_없음_메시지를_반환한다() {
        TodoController controller = new TodoController(todoService);
        given(todoService.getTodayTodoList(10L, "user1")).willReturn(List.of());

        ResponseEntity<ApiResponse<List<TodoSummaryResponse>>> response =
                controller.getTodayTodoList(10L, auth());

        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getMessage()).isEqualTo("오늘 할 일이 없습니다");
    }

    @Test
    void 오늘_투두가_있으면_목록을_반환한다() {
        TodoController controller = new TodoController(todoService);
        TodoSummaryResponse summary = summary();
        given(todoService.getTodayTodoList(10L, "user1")).willReturn(List.of(summary));

        ResponseEntity<ApiResponse<List<TodoSummaryResponse>>> response =
                controller.getTodayTodoList(10L, auth());

        assertThat(response.getBody().getData()).containsExactly(summary);
    }

    @Test
    void 히스토리가_비어있으면_해당날짜_할일_없음_메시지를_반환한다() {
        TodoController controller = new TodoController(todoService);
        given(todoService.getTodoHistory(10L, "user1", "2026-06-04")).willReturn(List.of());

        ResponseEntity<ApiResponse<List<TodoSummaryResponse>>> response =
                controller.getTodoHistory(10L, "2026-06-04", auth());

        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getMessage()).isEqualTo("해당 날짜의 할 일이 없습니다");
    }

    @Test
    void 히스토리가_있으면_목록을_반환한다() {
        TodoController controller = new TodoController(todoService);
        TodoSummaryResponse summary = summary();
        given(todoService.getTodoHistory(10L, "user1", "2026-06-04")).willReturn(List.of(summary));

        ResponseEntity<ApiResponse<List<TodoSummaryResponse>>> response =
                controller.getTodoHistory(10L, "2026-06-04", auth());

        assertThat(response.getBody().getData()).containsExactly(summary);
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
    void 투두_상세_응답을_반환한다() {
        TodoController controller = new TodoController(todoService);
        TodoDetailResponse serviceResponse = new TodoDetailResponse(
                1L, "투두", OffsetDateTime.parse("2026-06-04T12:00:00+09:00"), "닉네임",
                TodoStatus.IN_PROGRESS, "0 / 1", List.of()
        );
        given(todoService.getTodoDetail(1L, "user1")).willReturn(serviceResponse);

        ResponseEntity<ApiResponse<TodoDetailResponse>> response = controller.getTodoDetail(1L, auth());

        assertThat(response.getBody().getData()).isEqualTo(serviceResponse);
    }

    @Test
    void 이모지_반응_응답을_반환한다() {
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
    void 투두_제출_응답을_반환한다() {
        TodoController controller = new TodoController(todoService);
        SubmitTodoRequest request = new SubmitTodoRequest("proofs/1/a.png");

        ResponseEntity<ApiResponse<Void>> response = controller.submitTodo(1L, request, auth());

        assertThat(response.getBody().getMessage()).isEqualTo("인증 사진이 제출되었습니다.");
        then(todoService).should().submitTodo(1L, "user1", request);
    }

    private CreateTodoRequest createRequest() {
        return new CreateTodoRequest(
                "투두",
                null,
                OffsetDateTime.parse("2026-06-04T12:00:00+09:00"),
                List.of(1L)
        );
    }

    private TodoSummaryResponse summary() {
        return new TodoSummaryResponse(
                1L,
                "투두",
                OffsetDateTime.parse("2026-06-04T12:00:00+09:00"),
                "닉네임",
                TodoStatus.IN_PROGRESS,
                "0 / 1",
                "미완료",
                0,
                List.of()
        );
    }

    private TestingAuthenticationToken auth() {
        return new TestingAuthenticationToken("user1", null);
    }
}
