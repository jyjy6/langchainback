package langhchainback.langchain.AI.RAG;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 문서 메타데이터 엔티티
 * 
 * 현업 베스트 프랙티스:
 * - 업로드된 문서의 메타데이터를 PostgreSQL에 영구 저장
 * - 서버 재시작 시에도 문서 목록 유지
 * - 문서 관리 및 추적 가능
 * 
 * 저장 정보:
 * - documentId: 문서 고유 식별자
 * - fileName: 원본 파일명
 * - chunkCount: 생성된 청크 개수
 * - fileSize: 파일 크기 (bytes)
 * - uploadedAt: 업로드 일시
 */
@Entity
@Table(
    name = "document_metadata",
    indexes = {
        @Index(name = "idx_document_id", columnList = "document_id", unique = true),
        @Index(name = "idx_uploaded_at", columnList = "uploaded_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class DocumentMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 문서 고유 식별자 (사용자 지정 또는 자동 생성)
     * 예: "회사규정_2024", "product_manual_v1"
     */
    @Column(name = "document_id", nullable = false, unique = true, length = 255)
    private String documentId;

    /**
     * 원본 파일명
     * 예: "회사규정.pdf", "Product Manual.docx"
     */
    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    /**
     * 생성된 청크(문서 조각) 개수
     * RAG 시스템에서 벡터화되어 저장된 조각의 수
     */
    @Column(name = "chunk_count", nullable = false)
    private Integer chunkCount;

    /**
     * 파일 크기 (bytes)
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * 업로드 일시
     */
    @CreatedDate
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    /**
     * 수정 일시 (향후 재업로드 등에 활용)
     */
    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 파일 타입 (확장자)
     * 예: "pdf", "docx", "txt"
     */
    @Column(name = "file_type", length = 50)
    private String fileType;

    /**
     * 문서 설명 (옵션)
     */
    @Column(name = "description", length = 1000)
    private String description;

    /**
     * 활성화 여부 (논리 삭제용)
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * DocumentInfo로 변환
     * Service 계층에서 사용
     */
    public RagService.DocumentInfo toDocumentInfo() {
        return new RagService.DocumentInfo(
                documentId,
                fileName,
                chunkCount,
                uploadedAt,
                fileSize
        );
    }

    /**
     * 파일 확장자 추출
     */
    public void extractFileType() {
        if (fileName != null && fileName.contains(".")) {
            this.fileType = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
    }
}

