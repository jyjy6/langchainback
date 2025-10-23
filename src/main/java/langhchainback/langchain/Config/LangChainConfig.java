package langhchainback.langchain.Config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
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
        log.info("🧠 Embedding Model 초기화 - AllMiniLmL6V2 (로컬 실행)");
        
        // 로컬에서 실행되는 경량 임베딩 모델
        // 최초 실행 시 모델 다운로드 (약 23MB, 이후 캐시 사용)
        return new AllMiniLmL6V2EmbeddingModel();
    }

    /**
     * Phase 2.1-B: Embedding Store Bean
     * 
     * EmbeddingStore: 벡터화된 문서를 저장하는 벡터 데이터베이스
     * 
     * InMemoryEmbeddingStore:
     * - 메모리 기반 벡터 저장소
     * - 빠른 속도 (메모리 접근)
     * - 개발/테스트/PoC용 적합
     * - 애플리케이션 재시작 시 데이터 소실
     * - 대용량 문서에는 부적합 (메모리 제한)
     * 
     * 프로덕션 대안:
     * 1. PostgreSQL + pgvector (추천)
     *    - 장점: 기존 DB 활용, 트랜잭션 지원, 무료
     *    - 단점: 대용량 벡터 검색 시 성능 저하
     * 
     * 2. Pinecone (클라우드 벡터 DB)
     *    - 장점: 관리 불필요, 확장성 우수, 빠른 검색
     *    - 단점: 유료, 외부 의존성
     * 
     * 3. Chroma (오픈소스 벡터 DB)
     *    - 장점: 무료, Python/JS 지원, 로컬 실행 가능
     *    - 단점: Java 지원 제한적
     * 
     * 4. Weaviate (오픈소스 벡터 DB)
     *    - 장점: GraphQL 지원, 하이브리드 검색
     *    - 단점: 설치/운영 복잡
     * 
     * @return InMemory 벡터 저장소 (개발용)
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        log.info("💾 Embedding Store 초기화 - InMemoryEmbeddingStore (개발용)");
        log.warn("⚠️ 프로덕션 환경에서는 PostgreSQL pgvector 또는 Pinecone 사용 권장");
        
        // TextSegment: 문서 조각 (텍스트 + 메타데이터)
        return new InMemoryEmbeddingStore<>();
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
