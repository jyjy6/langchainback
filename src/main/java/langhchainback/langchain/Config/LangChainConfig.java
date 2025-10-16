package langhchainback.langchain.Config;

import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import langhchainback.langchain.AI.Assistant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChainConfig {
    @Value("${langchain4j.google-ai-gemini.api-key}")
    String apiKey;

    @Bean
    public Assistant assistant() {
        // 환경변수에서 Gemini API 키 읽기

        if (apiKey == null) {
            throw new IllegalStateException("GEMINI_API_KEY not set in environment variables");
        }

        // LangChain4j용 Gemini 모델 생성
        GoogleAiGeminiChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-2.5-flash")
                .temperature(0.7)
                .build();

        // AiService 인터페이스(Assistant)와 모델 연결
        return AiServices.create(Assistant.class, model);
    }
}
