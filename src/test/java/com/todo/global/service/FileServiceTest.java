package com.todo.global.service;

import com.todo.domain.team.entity.Team;
import com.todo.domain.team.repository.TeamMemberRepository;
import com.todo.domain.todo.entity.Todo;
import com.todo.domain.todo.repository.TodoRepository;
import com.todo.domain.user.entity.User;
import com.todo.global.config.MinioProperties;
import com.todo.global.dto.UploadType;
import com.todo.global.dto.request.PresignedUploadRequest;
import com.todo.global.dto.response.PresignedUploadResponse;
import com.todo.global.exception.BusinessException;
import com.todo.global.exception.FileStorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    private FileService fileService;
    private MinioProperties props;

    @Mock
    private S3Client s3Client;
    @Mock
    private S3Presigner s3Presigner;
    @Mock
    private TodoRepository todoRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;

    @BeforeEach
    void setUp() {
        props = new MinioProperties();
        props.setBucket("uploads");
        props.setPresignedUrlExpiration(3600);
        props.setPutPresignedUrlExpiration(600);
        fileService = new FileService(s3Client, s3Presigner, props, todoRepository, teamMemberRepository);
    }

    private void givenTodoWithTeam(Long todoId, Long teamId) {
        Team team = Team.create("팀", null, "invite-code");
        ReflectionTestUtils.setField(team, "id", teamId);
        User creator = User.create("creator", "pw", "닉네임", null);
        Todo todo = Todo.create(team, creator, "투두", null, null);
        given(todoRepository.findById(todoId)).willReturn(Optional.of(todo));
    }

    @Test
    void 프로필_업로드용_presigned_put_url을_생성한다() throws Exception {
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(
                PresignedPutObjectRequest.builder()
                        .httpRequest(httpRequest("https://storage.example.com/upload", SdkHttpMethod.PUT))
                        .expiration(Instant.now().plusSeconds(600))
                        .isBrowserExecutable(false)
                        .signedHeaders(Map.of("host", List.of("storage.example.com")))
                        .build()
        );

        PresignedUploadResponse response = fileService.generatePresignedPutUrl(
                1L,
                new PresignedUploadRequest(UploadType.PROFILE, "profile.png", "image/png", 1024L, null, null)
        );

        assertThat(response.uploadUrl()).isEqualTo("https://storage.example.com/upload");
        assertThat(response.objectKey()).startsWith("profiles/1/");
        assertThat(response.objectKey()).endsWith(".png");

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        then(s3Presigner).should().presignPutObject(captor.capture());
        assertThat(captor.getValue().signatureDuration().getSeconds()).isEqualTo(600);
        assertThat(captor.getValue().putObjectRequest().bucket()).isEqualTo("uploads");
        assertThat(captor.getValue().putObjectRequest().contentType()).isEqualTo("image/png");
        // 서명에 크기가 항상 포함돼야 URL 하나로 무제한 크기를 업로드할 수 없다
        assertThat(captor.getValue().putObjectRequest().contentLength()).isEqualTo(1024L);
    }

    @Test
    void 파일_크기_없이는_presigned_url을_발급하지_않는다() {
        assertThatThrownBy(() -> fileService.generatePresignedPutUrl(
                1L,
                new PresignedUploadRequest(UploadType.PROFILE, "profile.png", "image/png", null, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("파일 크기는 필수입니다.");

        then(s3Presigner).shouldHaveNoInteractions();
    }

    @Test
    void 팀_업로드용_key는_teams_temp_경로를_사용한다() throws Exception {
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(
                PresignedPutObjectRequest.builder()
                        .httpRequest(httpRequest("https://storage.example.com/team", SdkHttpMethod.PUT))
                        .expiration(Instant.now().plusSeconds(600))
                        .isBrowserExecutable(false)
                        .signedHeaders(Map.of("host", List.of("storage.example.com")))
                        .build()
        );

        PresignedUploadResponse response = fileService.generatePresignedPutUrl(
                7L,
                new PresignedUploadRequest(UploadType.TEAM, "team-image", "image/jpeg", 1024L, null, null)
        );

        assertThat(response.objectKey()).startsWith("teams/temp/7/");
        assertThat(response.objectKey()).doesNotEndWith(".");
    }

    @Test
    void 인증사진_업로드용_key는_teamId_todoId_userId_경로를_사용한다() throws Exception {
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(
                PresignedPutObjectRequest.builder()
                        .httpRequest(httpRequest("https://storage.example.com/proof", SdkHttpMethod.PUT))
                        .expiration(Instant.now().plusSeconds(600))
                        .isBrowserExecutable(false)
                        .signedHeaders(Map.of("host", List.of("storage.example.com")))
                        .build()
        );
        givenTodoWithTeam(10L, 5L);
        given(teamMemberRepository.existsByTeamIdAndUserId(5L, 3L)).willReturn(true);

        PresignedUploadResponse response = fileService.generatePresignedPutUrl(
                3L,
                new PresignedUploadRequest(UploadType.PROOF, "proof.webp", "image/webp", 1024L, null, 10L)
        );

        assertThat(response.objectKey()).startsWith("proofs/5/10/3/");
        assertThat(response.objectKey()).endsWith(".webp");
    }

    @Test
    void PROOF_타입은_todoId가_없으면_400() {
        assertThatThrownBy(() -> fileService.generatePresignedPutUrl(
                3L,
                new PresignedUploadRequest(UploadType.PROOF, "proof.webp", "image/webp", 1024L, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("인증 파일 업로드에는 투두 ID가 필요합니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        then(s3Presigner).shouldHaveNoInteractions();
    }

    @Test
    void 존재하지_않는_투두면_400() {
        given(todoRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.generatePresignedPutUrl(
                3L,
                new PresignedUploadRequest(UploadType.PROOF, "proof.webp", "image/webp", 1024L, null, 10L)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("존재하지 않는 투두입니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void 투두가_속한_팀의_멤버가_아니면_403() {
        givenTodoWithTeam(10L, 5L);
        given(teamMemberRepository.existsByTeamIdAndUserId(5L, 3L)).willReturn(false);

        assertThatThrownBy(() -> fileService.generatePresignedPutUrl(
                3L,
                new PresignedUploadRequest(UploadType.PROOF, "proof.webp", "image/webp", 1024L, null, 10L)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("해당 투두가 속한 팀의 멤버만 인증 파일을 업로드할 수 있습니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        then(s3Presigner).shouldHaveNoInteractions();
    }

    @Test
    void 지원하지_않는_이미지_형식이면_400_예외를_던진다() {
        assertThatThrownBy(() -> fileService.generatePresignedPutUrl(
                1L,
                new PresignedUploadRequest(UploadType.PROFILE, "profile.gif", "image/gif", 1024L, null, null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("지원하지 않는 이미지 형식입니다.")
                .satisfies(e -> assertThat(((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        then(s3Presigner).should(never()).presignPutObject(any(PutObjectPresignRequest.class));
    }

    @Test
    void 이미지_key가_비어있으면_url을_생성하지_않는다() {
        assertThat(fileService.resolveImageUrl(null)).isNull();
        assertThat(fileService.resolveImageUrl(" ")).isNull();
    }

    @Test
    void 이미지_key가_있으면_presigned_get_url을_생성한다() throws Exception {
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).willReturn(
                PresignedGetObjectRequest.builder()
                        .httpRequest(httpRequest("https://storage.example.com/download", SdkHttpMethod.GET))
                        .expiration(Instant.now().plusSeconds(3600))
                        .isBrowserExecutable(true)
                        .signedHeaders(Map.of("host", List.of("storage.example.com")))
                        .build()
        );

        String url = fileService.resolveImageUrl("profiles/1/a.png");

        assertThat(url).isEqualTo("https://storage.example.com/download");
        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        then(s3Presigner).should().presignGetObject(captor.capture());
        assertThat(captor.getValue().signatureDuration().getSeconds()).isEqualTo(3600);
        assertThat(captor.getValue().getObjectRequest().bucket()).isEqualTo("uploads");
        assertThat(captor.getValue().getObjectRequest().key()).isEqualTo("profiles/1/a.png");
    }

    @Test
    void 본인_proofs_경로의_5MB_이하_파일은_인증_사진으로_검증한다() {
        givenTodoWithTeam(10L, 5L);
        given(s3Client.headObject(any(HeadObjectRequest.class))).willReturn(HeadObjectResponse.builder()
                .contentType("image/webp")
                .contentLength(5L * 1024 * 1024)
                .build());

        fileService.validateProofImage(1L, 10L, "proofs/5/10/1/a.webp");

        ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        then(s3Client).should().headObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("uploads");
        assertThat(captor.getValue().key()).isEqualTo("proofs/5/10/1/a.webp");
    }

    @Test
    void 다른_사용자의_인증_사진_key는_거절한다() {
        givenTodoWithTeam(10L, 5L);

        assertThatThrownBy(() -> fileService.validateProofImage(1L, 10L, "proofs/5/10/2/a.jpg"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("본인이 업로드한 인증 사진만 제출할 수 있습니다.");

        then(s3Client).should(never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void 본인_경로의_프로필_키는_통과한다() {
        fileService.validateProfileImageKey(1L, "profiles/1/a.png");
        fileService.validateProfileImageKey(1L, "profiles/temp/a.png");
    }

    @Test
    void 비로그인_프로필_키는_temp_경로만_통과한다() {
        fileService.validateProfileImageKey(null, "profiles/temp/a.png");

        assertThatThrownBy(() -> fileService.validateProfileImageKey(null, "profiles/1/a.png"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("본인이 업로드한 프로필 이미지만 사용할 수 있습니다.");
    }

    @Test
    void 프로필_키가_없으면_검증을_건너뛴다() {
        fileService.validateProfileImageKey(1L, null);
        fileService.validateProfileImageKey(1L, " ");
    }

    @Test
    void 다른_사용자_경로나_다른_용도의_키는_프로필로_거절한다() {
        assertThatThrownBy(() -> fileService.validateProfileImageKey(1L, "profiles/2/a.png"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("본인이 업로드한 프로필 이미지만 사용할 수 있습니다.");
        assertThatThrownBy(() -> fileService.validateProfileImageKey(1L, "proofs/2/a.jpg"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("본인이 업로드한 프로필 이미지만 사용할 수 있습니다.");
        // "profiles/1/..."의 prefix 우회 시도 — userId 뒤 구분자가 없으면 다른 사용자(12)의 경로다
        assertThatThrownBy(() -> fileService.validateProfileImageKey(1L, "profiles/12/a.png"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("본인이 업로드한 프로필 이미지만 사용할 수 있습니다.");
    }

    @Test
    void 본인_temp_경로의_팀_키는_통과하고_그_외는_거절한다() {
        fileService.validateTeamImageKey(1L, "teams/temp/1/a.png");
        fileService.validateTeamImageKey(1L, null);

        assertThatThrownBy(() -> fileService.validateTeamImageKey(1L, "teams/temp/2/a.png"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("본인이 업로드한 팀 이미지만 사용할 수 있습니다.");
        assertThatThrownBy(() -> fileService.validateTeamImageKey(1L, "proofs/1/a.jpg"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("본인이 업로드한 팀 이미지만 사용할 수 있습니다.");
    }

    @Test
    void 썸네일_key는_인증_사진으로_거절한다() {
        // 썸네일 여부는 DB 조회 없이 바로 판별되므로, teamId 조회(투두 mock)를 아예 안 걸어도 통과해야 한다.
        assertThatThrownBy(() -> fileService.validateProofImage(1L, 10L, "proofs/5/10/1/thumbs/a.jpg"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("썸네일 파일은 인증 사진으로 제출할 수 없습니다.");

        then(s3Client).should(never()).headObject(any(HeadObjectRequest.class));
        then(todoRepository).should(never()).findById(any());
    }

    @Test
    void 크기가_5MB를_초과한_인증_사진은_거절한다() {
        givenTodoWithTeam(10L, 5L);
        given(s3Client.headObject(any(HeadObjectRequest.class))).willReturn(HeadObjectResponse.builder()
                .contentType("image/jpeg")
                .contentLength(5L * 1024 * 1024 + 1)
                .build());

        assertThatThrownBy(() -> fileService.validateProofImage(1L, 10L, "proofs/5/10/1/a.jpg"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("인증 사진은 5MB 이하만 제출할 수 있습니다.");
    }

    @Test
    void 허용하지_않는_MIME의_인증_사진은_거절한다() {
        givenTodoWithTeam(10L, 5L);
        given(s3Client.headObject(any(HeadObjectRequest.class))).willReturn(HeadObjectResponse.builder()
                .contentType("image/gif")
                .contentLength(1024L)
                .build());

        assertThatThrownBy(() -> fileService.validateProofImage(1L, 10L, "proofs/5/10/1/a.gif"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("지원하지 않는 이미지 형식입니다.");
    }

    @Test
    void 삭제_key가_비어있으면_s3를_호출하지_않는다() {
        fileService.deleteObject(null);
        fileService.deleteObject(" ");

        then(s3Client).should(never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void 삭제는_s3_예외를_삼킨다() {
        doThrow(S3Exception.builder().message("failed").build())
                .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        fileService.deleteObject("profiles/1/a.png");

        then(s3Client).should().deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void outbox용_삭제는_s3_예외를_FileStorageException으로_전달한다() {
        var s3Exception = S3Exception.builder().message("failed").build();
        doThrow(s3Exception).when(s3Client).deleteObject(any(DeleteObjectRequest.class));

        assertThatThrownBy(() -> fileService.deleteObjectOrThrow("profiles/1/a.png"))
                .isInstanceOf(FileStorageException.class)
                .hasMessage("파일 삭제에 실패했습니다.")
                .hasCause(s3Exception);
    }

    @Test
    void 썸네일은_key가_비어있으면_null을_반환한다() {
        assertThat(fileService.createProofThumbnail(null)).isNull();
        assertThat(fileService.createProofThumbnail(" ")).isNull();
    }

    @Test
    void 썸네일_생성_실패시_null을_반환한다() {
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), new byte[]{1, 2, 3}));

        assertThat(fileService.createProofThumbnail("proofs/1/a.png")).isNull();
    }

    @Test
    void webp_인증사진도_썸네일을_생성한다() throws Exception {
        byte[] source = readFixture("fixtures/proof-sample.webp");
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willReturn(ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), source));

        String thumbnailKey = fileService.createProofThumbnail("proofs/1/a.webp");

        assertThat(thumbnailKey).isNotNull();

        ArgumentCaptor<RequestBody> body = ArgumentCaptor.forClass(RequestBody.class);
        then(s3Client).should().putObject(any(PutObjectRequest.class), body.capture());
        BufferedImage thumbnail = ImageIO.read(body.getValue().contentStreamProvider().newStream());
        assertThat(thumbnail).isNotNull();
        assertThat(Math.max(thumbnail.getWidth(), thumbnail.getHeight())).isLessThanOrEqualTo(480);
    }

    @Test
    void imageio에_webp_리더가_등록되어_있다() {
        assertThat(ImageIO.getImageReadersByMIMEType("image/webp").hasNext())
                .describedAs("허용 MIME에 image/webp가 있으므로 리더가 없으면 썸네일이 항상 실패한다")
                .isTrue();
    }

    private byte[] readFixture(String path) throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(in).describedAs("테스트 픽스처 없음: %s", path).isNotNull();
            return in.readAllBytes();
        }
    }

    private SdkHttpFullRequest httpRequest(String url, SdkHttpMethod method) {
        return SdkHttpFullRequest.builder()
                .uri(URI.create(url))
                .method(method)
                .build();
    }
}
