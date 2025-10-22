package langhchainback.langchain.AI;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.CompletableFuture;

/**
 * Phase 1.3: 스트리밍 응답 Controller
 * - Server-Sent Events (SSE)로 실시간 응답
 * - ChatGPT처럼 답변이 점진적으로 생성됨
 */
@RestController
@RequestMapping("/ai/stream")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class StreamingController {
    private final StreamingAssistant streamingAssistant;  // 실제 스트리밍 Assistant 사용!

    /**
     * 예제 1: 기본 스트리밍 채팅
     * GET /ai/stream/chat?message=스프링부트에 대해 자세히 설명해줘
     * 
     * 실제 TokenStream 스트리밍 사용 (Gemini 1.0.0+ 지원)
     * curl -N "http://localhost:8080/ai/stream/chat?message=안녕하세요"
     */
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestParam String message) {
        log.info("Streaming chat request: {}", message);
        
        return createFluxFromTokenStream(
            streamingAssistant.chat(message)
        );
    }

    /**
     * 예제 2: 블로그 포스트 생성
     * GET /ai/stream/blog?topic=LangChain4j 시작하기
     */
    @GetMapping(value = "/blog", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> writeBlog(@RequestParam String topic) {
        log.info("Streaming blog generation: {}", topic);
        
        return createFluxFromTokenStream(
            streamingAssistant.writeBlogPost(topic)
        );
    }

    /**
     * 예제 3: 코드 생성
     * POST /ai/stream/code
     * Body: { "language": "Java", "description": "사용자 인증 REST API" }
     */
    @PostMapping(value = "/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateCode(@RequestBody CodeRequest request) {
        log.info("Streaming code generation - language: {}", request.language());
        
        return createFluxFromTokenStream(
            streamingAssistant.generateCode(
                request.language(),
                request.description()
            )
        );
    }

    /**
     * 예제 4: 스토리 생성
     * GET /ai/stream/story?genre=SF&topic=시간여행
     */
    @GetMapping(value = "/story", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> writeStory(
            @RequestParam String genre,
            @RequestParam String topic) {
        log.info("Streaming story generation - genre: {}, topic: {}", genre, topic);
        
        return createFluxFromTokenStream(
            streamingAssistant.writeStory(genre, topic)
        );
    }

    /**
     * 예제 5: 문서 분석
     * POST /ai/stream/analyze
     * Body: { "text": "분석할 긴 텍스트..." }
     */
    @PostMapping(value = "/analyze", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> analyzeDocument(@RequestBody AnalyzeRequest request) {
        log.info("Streaming document analysis");
        
        return createFluxFromTokenStream(
            streamingAssistant.analyzeDocument(request.text())
        );
    }

    /**
     * 예제 6: 강의 자료 생성
     * GET /ai/stream/lecture?topic=Java%20스트림%20API&audience=초급%20개발자
     */
    @GetMapping(value = "/lecture", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> createLecture(
            @RequestParam String topic,
            @RequestParam String audience) {
        log.info("Streaming lecture creation - topic: {}, audience: {}", topic, audience);
        
        return createFluxFromTokenStream(
            streamingAssistant.createLecture(topic, audience)
        );
    }

    /**
     * 예제 7: 긴 텍스트 번역
     * POST /ai/stream/translate
     * Body: { "text": "긴 텍스트...", "targetLang": "영어" }
     */
    @PostMapping(value = "/translate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> translateDocument(@RequestBody TranslateRequest request) {
        log.info("Streaming translation to {}", request.targetLang());
        
        return createFluxFromTokenStream(
            streamingAssistant.translateDocument(
                request.text(),
                request.targetLang()
            )
        );
    }

    /**
     * TokenStream을 Reactor Flux로 변환하는 헬퍼 메서드
     * - TokenStream: LangChain4j의 스트리밍 인터페이스
     * - Flux: Spring WebFlux의 리액티브 스트림
     */
    private Flux<String> createFluxFromTokenStream(TokenStream tokenStream) {
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        
        CompletableFuture<Response<AiMessage>> future = new CompletableFuture<>();
        
        tokenStream
            .onNext(token -> {
                // 각 토큰이 생성될 때마다 스트리밍
                log.info("🔹 Token received ({}자): [{}]", token.length(), token);
                sink.tryEmitNext(token);
            })
            .onComplete(response -> {
                // 스트리밍 완료
                log.info("✅ Streaming completed - Total tokens: {}", response);
                sink.tryEmitComplete();
                future.complete(response);
            })
            .onError(error -> {
                // 에러 발생
                log.error("Streaming error", error);
                sink.tryEmitError(error);
                future.completeExceptionally(error);
            })
            .start();
        
        return sink.asFlux();
    }

    // DTO Records
    public record CodeRequest(String language, String description) {}
    public record AnalyzeRequest(String text) {}
    public record TranslateRequest(String text, String targetLang) {}
}

