package langhchainback.langchain.AI.RAG;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 문서 메타데이터 Repository
 * 
 * Spring Data JPA 사용 - 평소 MVC 패턴처럼 사용
 * 
 * 제공 기능:
 * - 문서 저장/조회/수정/삭제
 * - documentId로 검색
 * - 활성화된 문서만 조회
 * - 최신 업로드 순 정렬
 */
@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, Long> {

    /**
     * documentId로 문서 찾기
     * 
     * @param documentId 문서 고유 식별자
     * @return 문서 메타데이터
     */
    Optional<DocumentMetadata> findByDocumentId(String documentId);

    /**
     * documentId 존재 여부 확인
     * 
     * @param documentId 문서 고유 식별자
     * @return 존재 여부
     */
    boolean existsByDocumentId(String documentId);

    /**
     * 활성화된 문서 목록 조회 (최신순)
     * 
     * @return 활성화된 문서 목록
     */
    List<DocumentMetadata> findByIsActiveTrueOrderByUploadedAtDesc();

    /**
     * 전체 문서 목록 조회 (최신순)
     * 
     * @return 전체 문서 목록
     */
    List<DocumentMetadata> findAllByOrderByUploadedAtDesc();

    /**
     * 특정 날짜 이후 업로드된 문서 조회
     * 
     * @param date 기준 날짜
     * @return 문서 목록
     */
    List<DocumentMetadata> findByUploadedAtAfter(LocalDateTime date);

    /**
     * 파일 타입별 문서 조회
     * 
     * @param fileType 파일 타입 (pdf, docx, txt 등)
     * @return 문서 목록
     */
    List<DocumentMetadata> findByFileType(String fileType);

    /**
     * 활성화된 문서 개수 조회
     * 
     * @return 활성화된 문서 개수
     */
    long countByIsActiveTrue();

    /**
     * 전체 청크 개수 합계 조회
     * 
     * @return 전체 청크 개수
     */
    @Query("SELECT SUM(d.chunkCount) FROM DocumentMetadata d WHERE d.isActive = true")
    Long sumChunkCount();

    /**
     * documentId로 논리 삭제 (soft delete)
     * 
     * @param documentId 문서 ID
     */
    @Query("UPDATE DocumentMetadata d SET d.isActive = false WHERE d.documentId = :documentId")
    void softDeleteByDocumentId(String documentId);
}

