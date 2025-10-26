package langhchainback.langchain.Config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import langhchainback.langchain.AI.Assistant;
import langhchainback.langchain.AI.ConversationalAssistant;
import langhchainback.langchain.AI.RAG.RagAssistant;
import langhchainback.langchain.AI.StreamingAssistant;
import langhchainback.langchain.Redis.RedisChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;


@Slf4j
@RequiredArgsConstructor
@Configuration
public class LangChainConfig {
    @Value("${langchain4j.google-ai-gemini.api-key}")
    String apiKey;

    @Value("${pgvector.datasource.url}")
    String pgvectorUrl;

    @Value("${pgvector.datasource.username}")
    String pgvectorUsername;

    @Value("${pgvector.datasource.password}")
    String pgvectorPassword;

    @Value("${pgvector.table.name}")
    String pgvectorTableName;

    @Value("${pgvector.dimension}")
    Integer pgvectorDimension;

    private final RedisTemplate redisTemplate;

    /**
     * Phase 1.1: 기본 Assistant (메모리 없음)
     */
    @Bean
    public Assistant assistant() {
        if (apiKey == null) {
            throw new IllegalStateException("GEMINI_API_KEY not set in environment variables");
        }

        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-2.5-flash")
                .temperature(0.7)
                .build();

        return AiServices.create(Assistant.class, model);
    }

    /**
     * Phase 1.2-A: InMemory 대화 메모리 Assistant
     * - InMemoryChatMemoryStore: 애플리케이션 메모리에 대화 이력 저장
     * - 개발/테스트용 또는 세션 기반 임시 저장소
     * - 애플리케이션 재시작 시 데이터 소실
     */
    @Bean("inMemoryConversationalAssistant")
    public ConversationalAssistant inMemoryConversationalAssistant() {
        if (apiKey == null) {
            throw new IllegalStateException("GEMINI_API_KEY not set in environment variables");
        }

        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-2.5-flash")
                .temperature(0.7)
                .build();

        // InMemory 저장소: 빠르지만 휘발성
        InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();

        return AiServices.builder(ConversationalAssistant.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)  // 사용자/세션 ID
                        .maxMessages(20)  // 최근 20개 메시지만 유지
                        .chatMemoryStore(store)
                        .build())
                .build();
    }

    /**
     * Phase 1.2-B: Redis 대화 메모리 Assistant
     * - RedisChatMemoryStore: Redis에 대화 이력 영구 저장
     * - 프로덕션 환경용 - 다중 서버 환경에서도 공유 가능
     * - TTL 설정으로 자동 만료 가능
     */
    @Bean("redisConversationalAssistant")
    public ConversationalAssistant redisConversationalAssistant() {
        if (apiKey == null) {
            throw new IllegalStateException("GEMINI_API_KEY not set in environment variables");
        }

        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-2.5-flash")
                .temperature(0.7)
                .build();

        // Redis 저장소: 영구적이고 확장 가능
        RedisChatMemoryStore store = new RedisChatMemoryStore(redisTemplate);

        return AiServices.builder(ConversationalAssistant.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)  // 사용자/세션 ID
                        .maxMessages(20)  // 최근 20개 메시지만 유지
                        .chatMemoryStore(store)
                        .build())
                .build();
    }



    /**
     * Phase 1.3: 스트리밍 응답 Assistant
     * - GoogleAiGeminiStreamingChatModel 사용 시도 (최신 버전 테스트)
     * - TokenStream을 통해 실시간 토큰 생성
     * - OpenAI ChatGPT와 동일한 방식
     */
    @Bean
    public StreamingAssistant streamingAssistant() {
        if (apiKey == null) {
            throw new IllegalStateException("GEMINI_API_KEY not set in environment variables");
        }

        GoogleAiGeminiStreamingChatModel streamingModel = GoogleAiGeminiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-2.5-flash")
                .temperature(0.7)
                .build();

        return AiServices.create(StreamingAssistant.class, streamingModel);
    }

    // ==================== Phase 2.1: RAG (Retrieval Augmented Generation) ====================

    /**
     * Phase 2.1-A: Embedding Model Bean
     * 
     * EmbeddingModel: 텍스트를 벡터(숫자 배열)로 변환하는 모델
     * 
     * AllMiniLmL6V2:
     * - 경량화된 오픈소스 임베딩 모델 (약 23MB)
     * - 384차원 벡터 생성
     * - 로컬에서 실행 가능 (API 호출 없음, 무료)
     * - 다국어 지원 (한국어 포함)
     * - 의미적 유사도 측정에 최적화
     * 
     * 작동 원리:
     * - "퇴직금 계산" → [0.123, -0.456, 0.789, ...] (384개 숫자)
     * - "퇴직금 산정" → [0.125, -0.450, 0.791, ...] (유사한 벡터)
     * - "날씨 정보"   → [0.891, 0.234, -0.567, ...] (다른 벡터)
     * 
     * 대안 모델:
     * - OpenAI text-embedding-ada-002 (유료, 1536차원, 더 정확)
     * - Google PaLM Embeddings (유료, 768차원)
     * - Cohere Embeddings (유료, 1024차원)
     */
    @Bean
    public EmbeddingModel embeddingModel() {

        String modelName = "text-embedding-004";
        log.info("🧠 Embedding Model 초기화 - Google AI ({})", modelName);

        return GoogleAiEmbeddingModel.builder()
                .apiKey(apiKey) // API 키 설정
                .modelName(modelName)
                .build();
    }

    /**
     * Phase 2.1-B: Embedding Store Bean (PostgreSQL pgvector)
     * 
     * EmbeddingStore: 벡터화된 문서를 저장하는 벡터 데이터베이스
     * 
     * PgVectorEmbeddingStore:
     * - PostgreSQL의 pgvector 확장을 사용한 벡터 저장소
     * - 영구 저장 (재시작 후에도 데이터 유지)
     * - 트랜잭션 지원
     * - 프로덕션 환경 적합
     * - 코사인 유사도 기반 검색 지원
     * 
     * 장점:
     * - 무료 오픈소스
     * - 기존 PostgreSQL 인프라 활용
     * - ACID 트랜잭션 보장
     * - SQL과 벡터 검색 결합 가능
     * 
     * InMemoryEmbeddingStore (개발용, 주석처리됨):
     * - 메모리 기반, 빠르지만 휘발성
     * - 애플리케이션 재시작 시 데이터 소실
     * 
     * @return PostgreSQL pgvector 벡터 저장소
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        log.info("💾 Embedding Store 초기화 - PostgreSQL pgvector (프로덕션용)");
        log.info("📊 연결: {}, 테이블: {}, 차원: {}", pgvectorUrl, pgvectorTableName, pgvectorDimension);
        
        try {
            // PgVectorEmbeddingStore 생성
            // host, port, database 파싱
            // URL 형식: jdbc:postgresql://localhost:5432/vector_db
            String cleanUrl = pgvectorUrl.replace("jdbc:postgresql://", "");
            String[] parts = cleanUrl.split("/");
            String[] hostPort = parts[0].split(":");
            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);
            String database = parts[1];
            
            log.info("🔗 PostgreSQL 연결 정보 - host: {}, port: {}, database: {}", host, port, database);
            
            PgVectorEmbeddingStore store = PgVectorEmbeddingStore.builder()
                    .host(host)
                    .port(port)
                    .database(database)
                    .user(pgvectorUsername)
                    .password(pgvectorPassword)
                    .table(pgvectorTableName)
                    .dimension(pgvectorDimension)
                    .createTable(true)  // 테이블이 없으면 자동 생성
                    .dropTableFirst(false)  // 기존 데이터 유지
                    .build();
            
            log.info("✅ PostgreSQL pgvector 연결 성공");
            return store;
            
        } catch (Exception e) {
            log.error("❌ PostgreSQL pgvector 연결 실패: {}", e.getMessage(), e);
            throw new IllegalStateException("PostgreSQL pgvector 초기화 실패", e);
        }
        
        // ========== InMemory 방식 (개발용, 주석처리) ==========
        // log.info("💾 Embedding Store 초기화 - InMemoryEmbeddingStore (개발용)");
        // log.warn("⚠️ 프로덕션 환경에서는 PostgreSQL pgvector 사용 권장");
        // return new InMemoryEmbeddingStore<>();
    }

    /**
     * Phase 2.1-C: RAG Assistant Bean
     * 
     * RagAssistant: 문서 기반 질의응답을 수행하는 AI 어시스턴트
     * 
     * 특징:
     * - 검색된 문서만을 기반으로 답변 생성
     * - 환각(Hallucination) 최소화
     * - 답변의 출처 추적 가능
     * 
     * 작동 방식:
     * 1. RagService에서 관련 문서 검색
     * 2. 검색된 문서를 @SystemMessage의 {{information}}에 주입
     * 3. 사용자 질문을 @UserMessage의 {{question}}에 주입
     * 4. AI가 문서 기반으로 답변 생성
     * 
     * @return RAG 전용 AI 어시스턴트
     */
    @Bean
    public RagAssistant ragAssistant() {
        if (apiKey == null) {
            throw new IllegalStateException("GEMINI_API_KEY not set in environment variables");
        }

        log.info("🤖 RAG Assistant 초기화 - Gemini 2.5 Flash");

        // RAG에는 일반 Chat Model 사용 (Streaming 불필요)
        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-2.5-pro")
                .temperature(0.4)  // RAG는 정확성이 중요하므로 낮은 temperature 사용
                .build();

        return AiServices.create(RagAssistant.class, model);
    }
}
