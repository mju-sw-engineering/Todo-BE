package com.todo.global.dto.request;

import com.todo.global.dto.UploadType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PresignedUploadRequestValidationTest {

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
    void fileSize가_없으면_검증에_걸린다() {
        PresignedUploadRequest request = request(null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("fileSize");
    }

    @Test
    void fileSize가_0이거나_음수면_검증에_걸린다() {
        assertThat(validator.validate(request(0L)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("fileSize");
        assertThat(validator.validate(request(-1L)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("fileSize");
    }

    @Test
    void fileSize가_최대_크기를_넘으면_검증에_걸린다() {
        PresignedUploadRequest request = request(PresignedUploadRequest.MAX_FILE_SIZE + 1);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("fileSize");
    }

    @Test
    void fileSize가_최대_크기_이하면_검증을_통과한다() {
        assertThat(validator.validate(request(1024L))).isEmpty();
        // 5MB 정확히는 허용 경계
        assertThat(validator.validate(request(PresignedUploadRequest.MAX_FILE_SIZE))).isEmpty();
    }

    private PresignedUploadRequest request(Long fileSize) {
        return new PresignedUploadRequest(UploadType.PROFILE, "profile.png", "image/png", fileSize, null, null);
    }
}
