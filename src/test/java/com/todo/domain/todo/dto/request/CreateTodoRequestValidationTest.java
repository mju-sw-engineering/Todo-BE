package com.todo.domain.todo.dto.request;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CreateTodoRequestValidationTest {

    private static Validator validator;
    private static ValidatorFactory validatorFactory;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void tasks_원소의_필수_필드도_검증한다() {
        CreateTodoRequest request = new CreateTodoRequest(
                "발표",
                null,
                OffsetDateTime.parse("2026-08-10T18:00:00+09:00"),
                null,
                List.of(new CreateTodoTaskRequest(
                        "",
                        null,
                        null,
                        null
                ))
        );

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("tasks[0].title", "tasks[0].assigneeId", "tasks[0].deadline");
    }
}
