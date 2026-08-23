package com.todo.domain.todo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "투두 인증 파일 제출 요청")
public record SubmitTodoRequest(

        @NotBlank
        @Schema(
                description = "인증 파일 오브젝트 키 (presigned-upload 후 반환된 값). 이미지와 문서 모두 이 필드로 전달합니다.",
                example = "proofs/5/10/1/uuid.pdf"
        )
        String proofImageKey,

        @Size(max = 255)
        @Schema(
                description = "원본 파일명. 오브젝트 키는 UUID라 사람이 알아볼 수 없어, 카드에 표시할 이름으로 사용합니다. "
                        + "생략하면 파일명 없이 저장됩니다.",
                example = "발표자료_초안.pdf"
        )
        String proofFileName
) {}
