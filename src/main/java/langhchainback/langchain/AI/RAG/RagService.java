package langhchainback.langchain.AI.RAG;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Phase 2.1: RAG Service
 * 
 * RAG 시스템의 핵심 비즈니스 로직을 담당하는 서비스
 * 
 * 주요 기능:
 * 1. ingestDocument() - 문서를 시스템에 저장 (벡터화 및 임베딩 저장소에 저장)
 * 2. searchRelevantContent() - 질문과 관련된 문서 내용 검색
 * 3. answerQuestion() - 검색된 문서를 바탕으로 AI 답변 생성
 * 
 * 아키텍처:
 * 문서 파일 → 파싱 → 청크 분할 → 임베딩(벡터화) → 벡터 저장소 저장
 * 사용자 질문 → 임베딩 → 유사도 검색 → 관련 문서 추출 → AI 답변 생성
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    /**
     * EmbeddingModel: 텍스트를 숫자 벡터로 변환하는 모델
     * - 의미가 유사한 텍스트는 유사한 벡터값을 가짐
     * - AllMiniLmL6V2: 경량화된 오픈소스 임베딩 모델 (로컬 실행 가능)
     */
    private final EmbeddingModel embeddingModel;

    /**
     * EmbeddingStore: 벡터화된 문서를 저장하는 저장소
     * - InMemory: 개발/테스트용 (메모리에 저장, 재시작 시 소실)
     * - 프로덕션: PostgreSQL pgvector, Pinecone, Chroma 등 사용 권장
     */
    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * RagAssistant: 문서 기반 질의응답을 수행하는 AI 어시스턴트
     */
    private final RagAssistant ragAssistant;

    /**
     * 문서 메타데이터 Repository
     * 
     * ✨ 현업 베스트 프랙티스 적용:
     * - PostgreSQL에 문서 메타데이터 영구 저장
     * - 서버 재시작 시에도 문서 목록 유지
     * - 일반 JPA Repository 패턴 사용 (평소 MVC처럼)
     */
    private final DocumentMetadataRepository documentMetadataRepository;

    /**
     * 문서 정보 DTO
     */
    public record DocumentInfo(
            String documentId,
            String fileName,
            int chunkCount,
            LocalDateTime uploadedAt,
            long fileSize
    ) {}

    /**
     * 1단계: 문서 수집 (Document Ingestion)
     * 
     * 업로드된 파일을 읽어서 벡터 DB에 저장하는 프로세스
     * 
     * 프로세스:
     * 1. 파일 파싱 (PDF, DOCX, TXT 등 → 텍스트 추출)
     * 2. 문서 분할 (Chunking) - 큰 문서를 작은 단위로 분할
     *    - 왜? AI 입력 토큰 제한 & 정확한 검색을 위해
     *    - chunk size: 300자, overlap: 30자 (연속성 유지)
     * 3. 임베딩 생성 - 각 청크를 벡터로 변환
     * 4. 벡터 저장소에 저장 - 나중에 검색 가능하도록
     * 
     * ✨ 메타데이터 기반 문서 관리 (현업 베스트 프랙티스):
     * - 각 청크에 documentId와 fileName을 메타데이터로 저장
     * - 나중에 특정 문서만 검색 가능 (쿼리 낭비 방지, 토큰 절약)
     * - 문서 목록 관리로 사용자가 원하는 문서 선택 가능
     * 
     * @param file 업로드된 파일 (PDF, DOCX, TXT 등)
     * @param documentId 문서 고유 식별자 (사용자 지정 또는 자동 생성)
     * @return 문서 정보 (DocumentInfo)
     * @throws IOException 파일 읽기 실패 시
     */
    public DocumentInfo ingestDocument(MultipartFile file, String documentId) throws IOException {
        String fileName = file.getOriginalFilename();
        long fileSize = file.getSize();
        
        log.info("📄 문서 수집 시작 - documentId: {}, 파일명: {}, 크기: {} bytes", 
                documentId, fileName, fileSize);

        // 중복 documentId 체크
        if (documentMetadataRepository.existsByDocumentId(documentId)) {
            String errorMessage = String.format(
                "이미 존재하는 문서 ID입니다: %s. 다른 이름을 사용해주세요.", documentId
            );
            log.warn("⚠️ {}", errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        // Step 1: 문서 파싱 (파일 → 텍스트)
        // Apache Tika: 다양한 포맷(PDF, DOCX, TXT 등) 자동 인식 및 파싱
        DocumentParser parser = new ApacheTikaDocumentParser();
        Document document;
        
        try (InputStream inputStream = file.getInputStream()) {
            document = parser.parse(inputStream);
            log.info("✅ 문서 파싱 완료 - 텍스트 길이: {} 자", document.text().length());
        }

        // Step 2: 문서 분할 (Chunking)
        // 왜 분할하는가?
        // - AI 입력 토큰 제한 (예: GPT-3.5는 4096 토큰 제한)
        // - 더 정확한 검색 (특정 주제의 문단만 추출 가능)
        // - 메모리 효율성
        DocumentSplitter splitter = DocumentSplitters.recursive(
            300,  // maxSegmentSizeInChars: 한 청크당 최대 300자
            30    // maxOverlapSizeInChars: 청크 간 30자 겹침 (문맥 연결성 유지)
        );
        
        List<TextSegment> segments = splitter.split(document);
        log.info("📑 문서 분할 완료 - 총 {} 개의 청크로 분할됨", segments.size());

        // Step 3 & 4: 임베딩 생성 및 저장 (메타데이터 포함)
        // 각 청크를 벡터로 변환하고 벡터 저장소에 저장
        // ✨ 핵심: 각 청크에 documentId와 fileName을 메타데이터로 저장
        int count = 0;
        for (TextSegment segment : segments) {
            // 메타데이터 추가 (현업 베스트 프랙티스)
            Metadata metadata = segment.metadata();
            metadata.put("documentId", documentId);
            metadata.put("fileName", fileName);
            metadata.put("uploadedAt", LocalDateTime.now().toString());
            metadata.put("chunkIndex", count);
            
            // 메타데이터가 추가된 새로운 TextSegment 생성
            TextSegment segmentWithMetadata = TextSegment.from(segment.text(), metadata);
            
            // 임베딩 생성 (텍스트 → 벡터)
            // 벡터: 보통 384차원 또는 768차원의 실수 배열
            // 의미가 유사한 텍스트는 벡터 공간에서 가까운 위치에 배치됨
            Embedding embedding = embeddingModel.embed(segmentWithMetadata).content();
            
            // 벡터 저장소에 저장 (메타데이터와 함께)
            embeddingStore.add(embedding, segmentWithMetadata);
            count++;
        }

        log.info("✅ 임베딩 저장 완료 - {} 개의 벡터가 저장소에 추가됨", count);
        
        // Step 5: 문서 메타데이터를 PostgreSQL에 영구 저장 (현업 베스트 프랙티스)
        DocumentMetadata documentMetadata = DocumentMetadata.builder()
                .documentId(documentId)
                .fileName(fileName)
                .chunkCount(count)
                .fileSize(fileSize)
                .build();
        
        // 파일 타입 추출 (pdf, docx 등)
        documentMetadata.extractFileType();
        
        // PostgreSQL에 저장
        DocumentMetadata saved = documentMetadataRepository.save(documentMetadata);
        
        long totalDocuments = documentMetadataRepository.count();
        log.info("📋 문서 메타데이터 DB 저장 완료 - 총 {} 개 문서 관리 중", totalDocuments);
        
        return saved.toDocumentInfo();
    }

    /**
     * 2단계: 관련 문서 검색 (Retrieval)
     * 
     * 사용자 질문과 의미적으로 유사한 문서 조각들을 검색
     * 
     * 작동 원리:
     * 1. 질문을 임베딩으로 변환 (텍스트 → 벡터)
     * 2. 벡터 DB에서 코사인 유사도(cosine similarity) 계산
     * 3. 가장 유사한 상위 N개의 문서 조각 반환
     * 
     * 예시:
     * - 질문: "퇴직금은 어떻게 계산하나요?"
     * - 검색 결과: 
     *   1. "퇴직금 계산 방법은 근속연수 × 평균임금 × 30일..." (유사도: 0.89)
     *   2. "퇴직금 지급 기준은 1년 이상 근무한 직원..." (유사도: 0.76)
     * 
     * @param question 사용자 질문
     * @param maxResults 검색할 최대 결과 수 (기본 3개)
     * @param minScore 최소 유사도 점수 (0.0 ~ 1.0, 기본 0.7)
     * @return 검색된 관련 문서 내용들
     */
    public String searchRelevantContent(String question, int maxResults, double minScore) {
        return searchRelevantContent(question, maxResults, minScore, null);
    }

    /**
     * 2단계: 관련 문서 검색 (Retrieval) - 문서 필터링 버전
     * 
     * ✨ 현업 베스트 프랙티스: 특정 문서만 검색
     * - 메타데이터 필터를 사용하여 특정 documentId의 청크만 검색
     * - 쿼리 낭비 방지: 불필요한 문서 검색 X
     * - 토큰 절약: 관련 없는 문서를 AI에 전달하지 않음
     * - 정확도 향상: 사용자가 선택한 문서만 참조
     * 
     * 예시:
     * - 사용자가 "회사규정.pdf"만 선택
     * - 질문: "휴가 신청 방법은?"
     * → "회사규정.pdf"의 청크만 검색하여 답변 생성
     * 
     * @param question 사용자 질문
     * @param maxResults 검색할 최대 결과 수
     * @param minScore 최소 유사도 점수
     * @param documentId 검색할 문서 ID (null이면 전체 문서 검색)
     * @return 검색된 관련 문서 내용들
     */
    public String searchRelevantContent(String question, int maxResults, double minScore, String documentId) {
        if (documentId != null) {
            log.info("🔍 관련 문서 검색 시작 - 질문: {}, 대상 문서: {}", question, documentId);
        } else {
            log.info("🔍 관련 문서 검색 시작 (전체 문서) - 질문: {}", question);
        }

        // Step 1: 질문을 임베딩으로 변환
        Embedding questionEmbedding = embeddingModel.embed(question).content();
        log.debug("✅ 질문 임베딩 생성 완료");

        // Step 2: 벡터 저장소에서 유사한 문서 검색
        // ✨ 핵심: documentId가 있으면 메타데이터 필터 적용
        EmbeddingSearchRequest.EmbeddingSearchRequestBuilder requestBuilder = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)
                .maxResults(maxResults)
                .minScore(minScore);
        
        // 특정 문서만 검색하도록 필터 추가 (현업 베스트 프랙티스)
        if (documentId != null) {
            Filter documentFilter = new IsEqualTo("documentId", documentId);
            requestBuilder.filter(documentFilter);
            log.info("📌 필터 적용: documentId = {}", documentId);
        }
        
        EmbeddingSearchRequest searchRequest = requestBuilder.build();
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();
        
        log.info("✅ 검색 완료 - {} 개의 관련 문서 발견", matches.size());

        // Step 3: 검색된 문서들을 하나의 문자열로 결합
        if (matches.isEmpty()) {
            log.warn("⚠️ 관련 문서를 찾을 수 없음 - 최소 유사도: {}", minScore);
            return "관련 문서를 찾을 수 없습니다.";
        }

        String relevantContent = matches.stream()
                .map(match -> {
                    double score = match.score();
                    String text = match.embedded().text();
                    String fileName = match.embedded().metadata().getString("fileName");
                    
                    log.info("- 문서 조각 (파일: {}, 유사도: {:.2f}): {}", 
                            fileName, score,
                            text.length() > 100 ? text.substring(0, 100) + "..." : text);
                    
                    return String.format("[파일: %s, 유사도: %.2f]\n%s\n", fileName, score, text);
                })
                .collect(Collectors.joining("\n---\n"));

        return relevantContent;
    }

    /**
     * 3단계: 질의응답 (Question Answering)
     * 
     * 검색된 문서를 바탕으로 AI가 답변을 생성하는 최종 단계
     * 
     * 전체 RAG 파이프라인:
     * 1. 질문 → 벡터 검색 (searchRelevantContent)
     * 2. 검색된 문서 → AI 컨텍스트로 전달
     * 3. AI → 문서 기반 답변 생성
     * 
     * 장점:
     * - 환각(Hallucination) 방지: AI가 임의로 답변을 만들지 않음
     * - 최신 정보: 문서만 업데이트하면 최신 정보 반영 가능
     * - 출처 명확: 어떤 문서 기반인지 추적 가능
     * 
     * @param question 사용자 질문
     * @return AI가 문서 기반으로 생성한 답변
     */
    public String answerQuestion(String question) {
        return answerQuestion(question, null);
    }

    /**
     * 3단계: 질의응답 (Question Answering) - 문서 필터링 버전
     * 
     * ✨ 현업 베스트 프랙티스: 특정 문서만 참조하여 답변 생성
     * - 사용자가 선택한 문서만 검색
     * - 토큰 절약 및 정확도 향상
     * 
     * @param question 사용자 질문
     * @param documentId 검색할 문서 ID (null이면 전체 문서 검색)
     * @return AI가 문서 기반으로 생성한 답변
     */
    public String answerQuestion(String question, String documentId) {
        if (documentId != null) {
            log.info("💬 질의응답 시작 - 질문: {}, 대상 문서: {}", question, documentId);
        } else {
            log.info("💬 질의응답 시작 (전체 문서) - 질문: {}", question);
        }

        // Step 1: 관련 문서 검색 (특정 문서만 검색)
        // maxResults=5: 상위 5개 문서 조각만 사용 (토큰 절약)
        // minScore=0.7: 70% 이상 유사한 문서만 사용 (정확도 향상)
        String relevantContent = searchRelevantContent(question, 5, 0.7, documentId);

        // Step 2: AI에게 문서와 질문을 함께 전달하여 답변 생성
        // RagAssistant의 @SystemMessage에 relevantContent가 주입됨
        String answer = ragAssistant.answer(question, relevantContent);

        log.info("✅ 답변 생성 완료 - 답변 길이: {} 자", answer.length());
        return answer;
    }

    /**
     * 업로드된 문서 목록 조회
     * 
     * ✨ 현업 베스트 프랙티스 적용:
     * - PostgreSQL에서 문서 목록 조회 (영구 저장)
     * - 평소 MVC 패턴처럼 Repository 사용
     * - 최신 업로드순으로 정렬
     * 
     * @return 업로드된 문서 목록
     */
    public List<DocumentInfo> getUploadedDocuments() {
        log.info("📋 DB에서 문서 목록 조회");
        
        // PostgreSQL에서 활성화된 문서 조회 (최신순)
        List<DocumentMetadata> metadataList = documentMetadataRepository
                .findByIsActiveTrueOrderByUploadedAtDesc();
        
        // DocumentInfo로 변환
        List<DocumentInfo> documentInfoList = metadataList.stream()
                .map(DocumentMetadata::toDocumentInfo)
                .toList();
        
        log.info("✅ 문서 목록 조회 완료 - {} 개 문서", documentInfoList.size());
        
        return documentInfoList;
    }

    /**
     * 특정 문서 정보 조회
     * 
     * @param documentId 문서 ID
     * @return 문서 정보 (없으면 Optional.empty())
     */
    public Optional<DocumentInfo> getDocumentInfo(String documentId) {
        log.debug("📄 문서 정보 조회 - documentId: {}", documentId);
        
        // PostgreSQL에서 documentId로 조회
        return documentMetadataRepository.findByDocumentId(documentId)
                .map(DocumentMetadata::toDocumentInfo);
    }

    /**
     * 문서 삭제 (논리 삭제)
     * 
     * @param documentId 문서 ID
     */
    @Transactional
    public void deleteDocument(String documentId) {
        log.info("🗑️ 문서 삭제 요청 - documentId: {}", documentId);
        
        DocumentMetadata document = documentMetadataRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new RuntimeException("문서를 찾을 수 없습니다: " + documentId));
        
        // 논리 삭제 (isActive = false)
        document.setIsActive(false);
        documentMetadataRepository.save(document);
        
        log.info("✅ 문서 삭제 완료 - documentId: {}", documentId);
    }


}

