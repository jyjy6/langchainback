package langhchainback.langchain.AI.RAG;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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
     * @param file 업로드된 파일 (PDF, DOCX, TXT 등)
     * @param documentId 문서 식별자 (사용자별/프로젝트별 구분용)
     * @return 처리된 청크(문서 조각) 개수
     * @throws IOException 파일 읽기 실패 시
     */
    public int ingestDocument(MultipartFile file, String documentId) throws IOException {
        log.info("📄 문서 수집 시작 - 파일명: {}, 크기: {} bytes", file.getOriginalFilename(), file.getSize());

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

        // Step 3 & 4: 임베딩 생성 및 저장
        // 각 청크를 벡터로 변환하고 메타데이터와 함께 저장
        int count = 0;
        for (TextSegment segment : segments) {
            // 임베딩 생성 (텍스트 → 벡터)
            // 벡터: 보통 384차원 또는 768차원의 실수 배열
            // 의미가 유사한 텍스트는 벡터 공간에서 가까운 위치에 배치됨
            Embedding embedding = embeddingModel.embed(segment).content();
            
            // 벡터 저장소에 저장
            // segment에는 원본 텍스트와 메타데이터(documentId 등) 포함
            embeddingStore.add(embedding, segment);
            count++;
        }

        log.info("✅ 임베딩 저장 완료 - {} 개의 벡터가 저장소에 추가됨", count);
        return count;
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
        log.info("🔍 관련 문서 검색 시작 - 질문: {}", question);

        // Step 1: 질문을 임베딩으로 변환
        Embedding questionEmbedding = embeddingModel.embed(question).content();
        log.debug("✅ 질문 임베딩 생성 완료");

        // Step 2: 벡터 저장소에서 유사한 문서 검색
        // EmbeddingSearchRequest: 검색 조건 설정
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding)  // 검색할 벡터
                .maxResults(maxResults)              // 최대 결과 수
                .minScore(minScore)                  // 최소 유사도 (0.7 = 70% 유사)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

        log.info("✅ 검색 완료 - {} 개의 관련 문서 발견", matches.size());

        // Step 3: 검색된 문서들을 하나의 문자열로 결합
        // AI에게 컨텍스트로 전달하기 위해
        if (matches.isEmpty()) {
            log.warn("⚠️ 관련 문서를 찾을 수 없음 - 최소 유사도: {}", minScore);
            return "관련 문서를 찾을 수 없습니다.";
        }

        String relevantContent = matches.stream()
                .map(match -> {
                    double score = match.score();
                    String text = match.embedded().text();
                    log.debug("- 문서 조각 (유사도: {:.2f}): {}", score, 
                            text.length() > 100 ? text.substring(0, 100) + "..." : text);
                    return String.format("[유사도: %.2f]\n%s\n", score, text);
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
        log.info("💬 질의응답 시작 - 질문: {}", question);

        // Step 1: 관련 문서 검색
        // maxResults=3: 상위 3개 문서 조각만 사용 (토큰 절약)
        // minScore=0.7: 70% 이상 유사한 문서만 사용 (정확도 향상)
        String relevantContent = searchRelevantContent(question, 3, 0.7);

        // Step 2: AI에게 문서와 질문을 함께 전달하여 답변 생성
        // RagAssistant의 @SystemMessage에 relevantContent가 주입됨
        String answer = ragAssistant.answer(question, relevantContent);

        log.info("✅ 답변 생성 완료 - 답변 길이: {} 자", answer.length());
        return answer;
    }

    /**
     * 유틸리티: 저장된 임베딩 개수 확인
     * 
     * 현재 벡터 DB에 저장된 문서 조각 개수 반환
     * 디버깅 및 모니터링 용도
     * 
     * @return 저장된 벡터 개수
     */
    public int getEmbeddingCount() {
        // InMemoryEmbeddingStore의 경우 직접 카운트 불가능
        // 프로덕션에서는 DB 쿼리로 대체 가능
        log.info("📊 임베딩 개수 조회 요청");
        return -1; // InMemory는 카운트 미지원, 실제 DB 사용 시 구현 필요
    }
}

