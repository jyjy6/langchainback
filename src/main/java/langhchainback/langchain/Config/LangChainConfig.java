package langhchainback.langchain.Config;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import langhchainback.langchain.AI.Assistant;
import langhchainback.langchain.AI.ConversationalAssistant;
import langhchainback.langchain.AI.StreamingAssistant;
import langhchainback.langchain.Redis.RedisChatMemoryStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;


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
     * - GoogleAiGeminiChatModel 하나로 일반/스트리밍 모두 처리
     * - AiServices가 인터페이스의 반환 타입(TokenStream)을 보고 자동으로 스트리밍 처리
     * - 별도의 StreamingChatModel이 필요 없음
     */
    @Bean
    public StreamingAssistant streamingAssistant() {
        if (apiKey == null) {
            throw new IllegalStateException("GEMINI_API_KEY not set in environment variables");
        }

        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-2.5-flash")
                .temperature(0.7)
                .build();

        // 동일한 모델로 생성 - AiServices가 TokenStream 반환을 보고 자동 스트리밍 처리
        return AiServices.create(StreamingAssistant.class, model);
    }
}
