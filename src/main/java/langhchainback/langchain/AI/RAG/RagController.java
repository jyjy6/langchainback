package langhchainback.langchain.AI.RAG;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Phase 2.1: RAG (Retrieval Augmented Generation) Controller
 * 
 * RAG 시스템의 REST API 엔드포인트를 제공
 * 
 * 제공 API:
 * 1. POST /rag/ingest - 문서 업로드 및 벡터화
 * 2. POST /rag/ask - 문서 기반 질의응답
 * 3. POST /rag/search - 관련 문서 검색만 수행
 * 
 * RAG 사용 흐름:
 * 1. 관리자: /rag/ingest로 회사 문서(PDF, DOCX 등) 업로드
 * 2. 시스템: 문서를 분석하여 벡터 DB에 저장
 * 3. 사용자: /rag/ask로 질문
 * 4. 시스템: 관련 문서를 찾아 AI 답변 생성
 * 
 * 실제 활용 예시:
 * - 회사 규정집 업로드 → 직원이 복리후생 관련 질문
 * - 제품 매뉴얼 업로드 → 고객이 사용법 질문
 * - 법률 문서 업로드 → 변호사가 판례 검색
 */
@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class RagController {

    private final RagService ragService;

    /**
     * API 1: 문서 수집 (Document Ingestion)
     * 
     * 엔드포인트: POST /rag/ingest
     * 
     * 기능:
     * - 파일을 업로드하면 시스템이 자동으로 처리
     * - 파일 파싱 → 청크 분할 → 임베딩 생성 → 벡터 DB 저장
     * 
     * ✨ 개선사항 (현업 베스트 프랙티스):
     * - documentId를 사용자가 지정 가능 (없으면 자동 생성)
     * - 메타데이터에 파일명, documentId 저장
     * - 나중에 특정 문서만 검색 가능
     * 
     * 지원 파일 형식:
     * - PDF: 가장 일반적인 문서 형식
     * - DOCX: Microsoft Word 문서
     * - TXT: 일반 텍스트 파일
     * - HTML: 웹 페이지
     * - 기타: Apache Tika가 지원하는 모든 형식
     * 
     * 사용 예시:
     * ```bash
     * curl -X POST http://localhost:8080/rag/ingest \
     *   -F "file=@company_handbook.pdf" \
     *   -F "documentId=handbook-2024"
     * ```
     * 
     * 프론트엔드 예시:
     * ```javascript
     * const formData = new FormData();
     * formData.append('file', fileInput.files[0]);
     * formData.append('documentId', 'handbook-2024'); // Optional
     * 
     * const response = await fetch('/rag/ingest', {
     *   method: 'POST',
     *   body: formData
     * });
     * ```
     * 
     * @param file 업로드할 문서 파일
     * @param documentId 문서 식별자 (선택, 없으면 자동 생성)
     * @return 처리 결과 (생성된 벡터 개수, documentId 등)
     */
    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingestDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentId", required = false) String documentId) {
        
        log.info("📥 문서 업로드 요청 - 파일명: {}, 크기: {} bytes, documentId: {}", 
                file.getOriginalFilename(), file.getSize(), documentId);

        // 파일 검증
        if (file.isEmpty()) {
            log.warn("⚠️ 빈 파일 업로드 시도");
            return ResponseEntity.badRequest()
                    .body(new IngestResponse(false, "파일이 비어있습니다.", 0, null, null));
        }

        // documentId가 없으면 자동 생성 (파일명 기반)
        if (documentId == null || documentId.trim().isEmpty()) {
            String fileName = file.getOriginalFilename();
            if (fileName != null) {
                // 파일 확장자 제거 및 공백을 언더스코어로 치환
                fileName = fileName.replaceFirst("[.][^.]+$", "").replaceAll("\\s+", "_");
                documentId = fileName + "_" + System.currentTimeMillis();
            } else {
                documentId = "doc_" + System.currentTimeMillis();
            }
            log.info("📝 자동 생성된 documentId: {}", documentId);
        }

        try {
            // 문서 처리 (파싱 → 분할 → 임베딩 → 저장)
            RagService.DocumentInfo docInfo = ragService.ingestDocument(file, documentId);

            log.info("✅ 문서 처리 완료 - {} 개의 청크 생성", docInfo.chunkCount());

            IngestResponse response = new IngestResponse(
                    true,
                    "문서가 성공적으로 처리되었습니다.",
                    docInfo.chunkCount(),
                    docInfo.documentId(),
                    docInfo.fileName()
            );

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("❌ 문서 처리 실패 - 파일명: {}, 에러: {}", file.getOriginalFilename(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new IngestResponse(false, "문서 처리 중 오류 발생: " + e.getMessage(), 0, null, null));
        }
    }

    /**
     * API 2: 문서 기반 질의응답
     * 
     * 엔드포인트: POST /rag/ask
     * 
     * 기능:
     * - 사용자 질문을 받아 관련 문서를 검색
     * - 검색된 문서를 바탕으로 AI가 답변 생성
     * 
     * ✨ 개선사항 (현업 베스트 프랙티스):
     * - documentId를 지정하면 특정 문서만 검색
     * - 토큰 절약 및 정확도 향상
     * - 불필요한 문서 검색 방지
     * 
     * RAG 파이프라인 실행:
     * 1. 질문 임베딩 생성
     * 2. 벡터 DB에서 유사 문서 검색 (코사인 유사도)
     *    - documentId가 있으면 해당 문서만 검색 (메타데이터 필터링)
     * 3. 검색된 문서를 AI 컨텍스트로 전달
     * 4. AI가 문서 기반 답변 생성
     * 
     * 장점:
     * - 정확성: 업로드된 문서만 참조하므로 환각(hallucination) 방지
     * - 추적성: 답변의 출처가 명확함
     * - 최신성: 문서만 업데이트하면 최신 정보 반영
     * - 효율성: 특정 문서만 검색하여 토큰 절약
     * 
     * 사용 예시:
     * ```bash
     * # 전체 문서 검색
     * curl -X POST http://localhost:8080/rag/ask \
     *   -H "Content-Type: application/json" \
     *   -d '{"question": "휴가 신청은 어떻게 하나요?"}'
     * 
     * # 특정 문서만 검색
     * curl -X POST http://localhost:8080/rag/ask \
     *   -H "Content-Type: application/json" \
     *   -d '{"question": "휴가 신청은 어떻게 하나요?", "documentId": "handbook-2024"}'
     * ```
     * 
     * 프론트엔드 예시:
     * ```javascript
     * const response = await fetch('/rag/ask', {
     *   method: 'POST',
     *   headers: { 'Content-Type': 'application/json' },
     *   body: JSON.stringify({ 
     *     question: '휴가 신청은 어떻게 하나요?',
     *     documentId: 'handbook-2024' // Optional
     *   })
     * });
     * const data = await response.json();
     * console.log(data.answer);
     * ```
     * 
     * @param request 질문 요청 (question, documentId 필드 포함)
     * @return AI 답변 및 관련 정보
     */
    @PostMapping("/ask")
    public ResponseEntity<AskResponse> askQuestion(@RequestBody AskRequest request) {
        log.info("❓ 질문 요청 - 질문: {}, documentId: {}", request.question(), request.documentId());

        // 질문 검증
        if (request.question() == null || request.question().trim().isEmpty()) {
            log.warn("⚠️ 빈 질문 요청");
            return ResponseEntity.badRequest()
                    .body(new AskResponse(false, "질문이 비어있습니다.", null, null, null));
        }

        try {
            // RAG 파이프라인 실행 (검색 + 답변 생성)
            // documentId가 있으면 특정 문서만 검색
            String answer = ragService.answerQuestion(request.question(), request.documentId());

            log.info("답변 {}", answer);
            log.info("✅ 답변 생성 완료 - 답변 길이: {} 자", answer.length());

            AskResponse response = new AskResponse(
                    true,
                    "답변이 생성되었습니다.",
                    answer,
                    request.question(),
                    request.documentId()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ 답변 생성 실패 - 질문: {}, 에러: {}", request.question(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new AskResponse(false, "답변 생성 중 오류 발생: " + e.getMessage(), null, null, null));
        }
    }

    /**
     * API 3: 관련 문서 검색
     * 
     * 엔드포인트: POST /rag/search
     * 
     * 기능:
     * - AI 답변 없이 관련 문서만 검색
     * - 디버깅 및 문서 품질 확인용
     * 
     * ✨ 개선사항:
     * - documentId로 특정 문서만 검색 가능
     * 
     * 사용 예시:
     * - 어떤 문서가 검색되는지 확인
     * - 유사도 점수 확인
     * - 문서 청크가 적절한지 검증
     * 
     * @param request 검색 요청 (질문, 최대 결과 수, 최소 유사도, documentId)
     * @return 검색된 문서 내용
     */
    @PostMapping("/search")
    public ResponseEntity<SearchResponse> searchDocuments(@RequestBody SearchRequest request) {
        log.info("🔍 문서 검색 요청 - 질문: {}, 최대 결과: {}, 최소 점수: {}, documentId: {}", 
                request.question(), request.maxResults(), request.minScore(), request.documentId());

        // 기본값 설정
        int maxResults = request.maxResults() != null ? request.maxResults() : 3;
        double minScore = request.minScore() != null ? request.minScore() : 0.7;

        try {
            // 문서 검색만 수행 (AI 답변 생성 없음)
            String relevantContent = ragService.searchRelevantContent(
                    request.question(),
                    maxResults, 
                    minScore,
                    request.documentId()
            );

            log.info("✅ 검색 완료");

            SearchResponse response = new SearchResponse(
                    true,
                    "검색이 완료되었습니다.",
                    relevantContent,
                    maxResults,
                    minScore,
                    request.documentId()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ 검색 실패 - 에러: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new SearchResponse(false, "검색 중 오류 발생: " + e.getMessage(), null, 0, 0.0, null));
        }
    }

    /**
     * API 4: 업로드된 문서 목록 조회
     * 
     * 엔드포인트: GET /rag/documents
     * 
     * ✨ 현업 베스트 프랙티스:
     * - 사용자가 업로드한 문서 목록 확인
     * - 질문 시 특정 문서 선택 가능
     * - 문서 관리 UI 구성에 필수
     * 
     * 기능:
     * - 업로드된 모든 문서의 메타데이터 반환
     * - documentId, 파일명, 청크 개수, 업로드 날짜 등
     * 
     * 사용 예시:
     * ```bash
     * curl -X GET http://localhost:8080/rag/documents
     * ```
     * 
     * 프론트엔드 예시:
     * ```javascript
     * const response = await fetch('/rag/documents');
     * const data = await response.json();
     * // 문서 목록을 드롭다운에 표시
     * data.documents.forEach(doc => {
     *   console.log(doc.documentId, doc.fileName);
     * });
     * ```
     * 
     * @return 업로드된 문서 목록
     */
    @GetMapping("/documents")
    public ResponseEntity<DocumentListResponse> getDocuments() {
        log.info("📋 문서 목록 조회 요청");

        try {
            List<RagService.DocumentInfo> documents = ragService.getUploadedDocuments();
            
            log.info("✅ 문서 목록 조회 완료 - {} 개 문서", documents.size());

            DocumentListResponse response = new DocumentListResponse(
                    true,
                    "문서 목록을 조회했습니다.",
                    documents,
                    documents.size()
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ 문서 목록 조회 실패 - 에러: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new DocumentListResponse(false, "문서 목록 조회 중 오류 발생: " + e.getMessage(), null, 0));
        }
    }

    // ==================== DTO Records ====================

    /**
     * 문서 업로드 요청은 MultipartFile로 받으므로 별도 DTO 불필요
     * documentId는 @RequestParam으로 처리
     */

    /**
     * 문서 업로드 응답 DTO
     * 
     * @param success 성공 여부
     * @param message 결과 메시지
     * @param chunkCount 생성된 문서 청크 개수
     * @param documentId 처리된 문서 ID
     * @param fileName 업로드된 파일명
     */
    public record IngestResponse(
            boolean success,
            String message,
            int chunkCount,
            String documentId,
            String fileName
    ) {}

    /**
     * 질의응답 요청 DTO
     * 
     * @param question 사용자 질문
     * @param documentId 검색할 문서 ID (null이면 전체 문서 검색)
     */
    public record AskRequest(
            String question,
            String documentId  // nullable
    ) {}

    /**
     * 질의응답 응답 DTO
     * 
     * @param success 성공 여부
     * @param message 결과 메시지
     * @param answer AI가 생성한 답변
     * @param originalQuestion 원본 질문 (참조용)
     * @param documentId 검색된 문서 ID
     */
    public record AskResponse(
            boolean success,
            String message,
            String answer,
            String originalQuestion,
            String documentId  // nullable
    ) {}

    /**
     * 문서 검색 요청 DTO
     * 
     * @param question 검색 질문
     * @param maxResults 최대 검색 결과 수 (기본 3)
     * @param minScore 최소 유사도 점수 (기본 0.7, 범위: 0.0~1.0)
     * @param documentId 검색할 문서 ID (null이면 전체 문서 검색)
     */
    public record SearchRequest(
            String question,
            Integer maxResults,    // nullable, 기본값 3
            Double minScore,       // nullable, 기본값 0.7
            String documentId      // nullable
    ) {}

    /**
     * 문서 검색 응답 DTO
     * 
     * @param success 성공 여부
     * @param message 결과 메시지
     * @param relevantContent 검색된 관련 문서 내용
     * @param resultCount 검색된 문서 개수
     * @param minScore 사용된 최소 유사도
     * @param documentId 검색된 문서 ID
     */
    public record SearchResponse(
            boolean success,
            String message,
            String relevantContent,
            int resultCount,
            double minScore,
            String documentId  // nullable
    ) {}

    /**
     * 문서 목록 조회 응답 DTO
     * 
     * @param success 성공 여부
     * @param message 결과 메시지
     * @param documents 업로드된 문서 목록
     * @param totalCount 총 문서 개수
     */
    public record DocumentListResponse(
            boolean success,
            String message,
            List<RagService.DocumentInfo> documents,
            int totalCount
    ) {}
}

