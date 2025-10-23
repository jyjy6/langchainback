package langhchainback.langchain.AI;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * LangChain4j 예제 컨트롤러
 * @SystemMessage와 @UserMessage 활용 예제
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class AssistantController {
    private final Assistant assistant;

    /**
     * 예제 1: 기본 채팅
     * GET /ai/chat?message=안녕하세요
     */
    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        log.info("Basic chat request: {}", message);
        return assistant.chat(message);
    }

    /**
     * 예제 2: 시스템 메시지 적용된 채팅
     * GET /ai/chat-with-system?message=스프링부트에 대해 알려줘
     */
    @GetMapping("/chat-with-system")
    public String chatWithSystem(@RequestParam String message) {
        log.info("Chat with system message: {}", message);
        return assistant.chatWithSystemMessage(message);
    }

    /**
     * 예제 3: 전문가 역할 기반 설명
     * GET /ai/explain?role=의사&topic=운동의 효과
     */
    @GetMapping("/explain")
    public String explainAsExpert(
            @RequestParam String role,
            @RequestParam String topic) {
        log.info("Explain as expert - role: {}, topic: {}", role, topic);
        return assistant.explainAsExpert(role, topic);
    }

    /**
     * 예제 4: 코드 리뷰
     * POST /ai/code-review
     * Body: { "language": "Java", "code": "public void test() { ... }" }
     */
    @PostMapping("/code-review")
    public String reviewCode(@RequestBody CodeReviewRequest request) {
        log.info("Code review request - language: {}", request.language());
        return assistant.reviewCode(request.language(), request.code());
    }

    /**
     * 예제 5: 번역
     * GET /ai/translate?from=한국어&to=영어&text=안녕하세요
     */
    @GetMapping("/translate")
    public String translate(
            @RequestParam(name = "from") String sourceLang,
            @RequestParam(name = "to") String targetLang,
            @RequestParam String text) {
        log.info("Translation request - from {} to {}", sourceLang, targetLang);
        return assistant.translate(sourceLang, targetLang, text);
    }

    /**
     * 예제 6: 텍스트 요약
     * POST /ai/summarize
     * Body: { "text": "긴 텍스트...", "maxWords": 50 }
     */
    @PostMapping("/summarize")
    public String summarize(@RequestBody SummarizeRequest request) {
        log.info("Summarize request - max words: {}", request.maxWords());
        return assistant.summarize(request.text(), request.maxWords());
    }

    /**
     * 예제 7: SQL 쿼리 생성
     * GET /ai/generate-sql?table=users&request=30세 이상 사용자 조회
     */
    @GetMapping("/generate-sql")
    public String generateSql(
            @RequestParam String table,
            @RequestParam String request) {
        log.info("SQL generation request - table: {}, request: {}", table, request);
        return assistant.generateSql(table, request);
    }

    /**
     * 예제 8: 감정 분석
     * GET /ai/sentiment?text=오늘 정말 기분이 좋아요!
     */
    @GetMapping("/sentiment")
    public String analyzeSentiment(@RequestParam String text) {
        log.info("Sentiment analysis request");
        return assistant.analyzeSentiment(text);
    }

    // DTO Records
    public record CodeReviewRequest(String language, String code) {}
    public record SummarizeRequest(String text, int maxWords) {}
}
