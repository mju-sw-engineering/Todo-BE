package com.todo.global.file.entity;

import com.todo.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * presign 발급 원장. presigned PUT URL을 발급할 때마다 한 행을 남긴다.
 *
 * <p>업로드만 하고 제출하지 않은 파일(고아)을 찾기 위한 후보 목록이다. 버킷을 스캔하지 않고
 * 이 원장만 보므로, 원장에 없는 객체는 정리 스케줄러의 눈에 아예 들어오지 않는다 — 스캔
 * 로직의 실수로 살아있는 파일을 지우는 사고를 구조적으로 막는다.
 *
 * <p>행의 수명: 유예 시간이 지나면 스케줄러가 확인한다. 키가 DB 어딘가에서 참조되면
 * 행만 지우고(제 역할 끝), 참조가 없으면 객체를 지운 뒤 행을 지운다. 객체 삭제에 실패하면
 * 행을 남겨 다음 실행에서 자연 재시도한다 — 행 자체가 재시도 큐다.
 */
@Entity
@Table(name = "upload_ledger",
        indexes = @Index(name = "idx_upload_ledger_created_at", columnList = "created_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UploadLedger extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    public static UploadLedger create(String objectKey) {
        UploadLedger ledger = new UploadLedger();
        ledger.objectKey = objectKey;
        return ledger;
    }
}
