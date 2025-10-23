package langhchainback.langchain.AI;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

/**
 * Phase 1.2: 대화 메모리 Controller
 * - 사용자별/세션별 대화 이력 관리
 * - @MemoryId를 통한 컨텍스트 유지
 * - @Qualifier를 사용하여 InMemory vs Redis 저장소 선택
 */
@RestController
@RequestMapping("/ai/memory")
@Slf4j
@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
public class ConversationalController {
    
    // InMemory 저장소 사용 (빠르지만 휘발성)
    private final ConversationalAssistant inMemoryAssistant;
    
    // Redis 저장소 사용 (영구적, 다중 서버 공유 가능)
    private final ConversationalAssistant redisAssistant;
    
    /**
     * 생성자 주입으로 두 개의 빈을 구분하여 주입
     * @Qualifier로 빈 이름을 지정하여 정확한 빈을 선택
     */
    public ConversationalController(
            @Qualifier("inMemoryConversationalAssistant") ConversationalAssistant inMemoryAssistant,
            @Qualifier("redisConversationalAssistant") ConversationalAssistant redisAssistant) {
        this.inMemoryAssistant = inMemoryAssistant;
        this.redisAssistant = redisAssistant;
    }

    /**
     * 예제 1-A: InMemory 대화 메모리 (빠르지만 휘발성)
     * GET /ai/memory/chat/inmemory?userId=user123&message=내 이름은 철수야
     * GET /ai/memory/chat/inmemory?userId=user123&message=내 이름이 뭐였지?
     * 
     * 특징:
     * - 같은 userId로 요청하면 이전 대화를 기억
     * - 빠른 응답 속도 (메모리 접근)
     * - 애플리케이션 재시작 시 데이터 소실
     * - 단일 서버 환경에 적합
     */
    @GetMapping("/chat/inmemory")
    public ChatResponse chatInMemory(
            @RequestParam String userId,
            @RequestParam String message) {
        log.info("[InMemory] Memory chat - userId: {}, message: {}", userId, message);
        
        long startTime = System.currentTimeMillis();
        String response = inMemoryAssistant.chat(userId, message);
        long duration = System.currentTimeMillis() - startTime;
        
        return new ChatResponse(response, userId, duration, "InMemory");
    }

    /**
     * 예제 1-B: Redis 대화 메모리 (영구적, 확장 가능)
     * GET /ai/memory/chat/redis?userId=user123&message=내 이름은 철수야
     * GET /ai/memory/chat/redis?userId=user123&message=내 이름이 뭐였지?
     * 
     * 특징:
     * - 같은 userId로 요청하면 이전 대화를 기억
     * - Redis에 영구 저장 (애플리케이션 재시작 후에도 유지)
     * - 다중 서버 환경에서 대화 이력 공유 가능
     * - TTL 설정으로 자동 만료 가능
     */
    @GetMapping("/chat/redis")
    public ChatResponse chatRedis(
            @RequestParam String userId,
            @RequestParam String message) {
        log.info("[Redis] Memory chat - userId: {}, message: {}", userId, message);
        
        long startTime = System.currentTimeMillis();
        String response = redisAssistant.chat(userId, message);
        long duration = System.currentTimeMillis() - startTime;
        
        return new ChatResponse(response, userId, duration, "Redis");
    }

    /**
     * 예제 2: 개인 비서 (Redis 사용 - 일정은 영구 저장이 중요)
     * GET /ai/memory/assistant?userId=user123&message=내일 9시에 회의 있어
     * GET /ai/memory/assistant?userId=user123&message=내일 일정이 뭐였지?
     */
    @GetMapping("/assistant")
    public ChatResponse personalAssistant(
            @RequestParam String userId,
            @RequestParam String message) {
        log.info("[Redis] Personal assistant - userId: {}", userId);
        
        // 개인 일정/선호도는 영구 저장이 중요하므로 Redis 사용
        String response = redisAssistant.personalAssistant(userId, message);
        return new ChatResponse(response, userId, null, "Redis");
    }

    /**
     * 예제 3: 기술 지원 (InMemory 사용 - 임시 세션)
     * POST /ai/memory/support
     * Body: { "sessionId": "session123", "message": "인터넷이 안돼요" }
     */
    @PostMapping("/support")
    public ChatResponse techSupport(@RequestBody SupportRequest request) {
        log.info("[InMemory] Tech support - session: {}", request.sessionId());
        
        // 기술 지원은 세션 기반 임시 대화이므로 InMemory 사용
        String response = inMemoryAssistant.techSupport(
            request.sessionId(), 
            request.message()
        );
        
        return new ChatResponse(response, request.sessionId(), null, "InMemory");
    }

    /**
     * 예제 4: 언어 학습 (Redis 사용 - 학습 진도 저장)
     * GET /ai/memory/tutor?studentId=student1&language=영어&message=How are you?
     */
    @GetMapping("/tutor")
    public ChatResponse languageTutor(
            @RequestParam String studentId,
            @RequestParam String language,
            @RequestParam String message) {
        log.info("[Redis] Language tutor - student: {}, language: {}", studentId, language);
        
        // 학습 진도와 실수 패턴은 영구 저장이 필요하므로 Redis 사용
        String response = redisAssistant.languageTutor(
            studentId,
            language,
            message
        );
        
        return new ChatResponse(response, studentId, null, "Redis");
    }

    /**
     * 예제 5: 쇼핑 어시스턴트 (Redis 사용 - 장바구니/선호도 저장)
     * GET /ai/memory/shopping?userId=user123&message=200만원 정도 예산으로 노트북 찾아줘
     */
    @GetMapping("/shopping")
    public ChatResponse shopping(
            @RequestParam String userId,
            @RequestParam String message) {
        log.info("[Redis] Shopping assistant - userId: {}", userId);
        
        // 장바구니와 선호도는 영구 저장이 필요하므로 Redis 사용
        String response = redisAssistant.shoppingAssistant(userId, message);
        return new ChatResponse(response, userId, null, "Redis");
    }

    /**
     * 예제 6: 스토리텔링 (InMemory 사용 - 일회성 스토리)
     * GET /ai/memory/story?sessionId=story1&message=우주선을 타고 여행을 떠났어요
     */
    @GetMapping("/story")
    public ChatResponse storyteller(
            @RequestParam String sessionId,
            @RequestParam String message) {
        log.info("[InMemory] Storyteller - session: {}", sessionId);
        
        // 스토리텔링은 일회성 세션이므로 InMemory 사용
        String response = inMemoryAssistant.storyteller(sessionId, message);
        return new ChatResponse(response, sessionId, null, "InMemory");
    }

    /**
     * 예제 7: 다국어 대화 (Redis 사용 - 언어 선호도 저장)
     * GET /ai/memory/multilingual?userId=user123&message=Hello
     * GET /ai/memory/multilingual?userId=user123&message=안녕하세요
     */
    @GetMapping("/multilingual")
    public ChatResponse multilingual(
            @RequestParam String userId,
            @RequestParam String message) {
        log.info("[Redis] Multilingual chat - userId: {}", userId);
        
        // 언어 선호도는 사용자별로 저장되어야 하므로 Redis 사용
        String response = redisAssistant.multilingualChat(userId, message);
        return new ChatResponse(response, userId, null, "Redis");
    }

    // DTO Records
    public record ChatResponse(
        String response,
        String memoryId,
        Long durationMs,
        String storageType  // "InMemory" or "Redis"
    ) {}

    public record SupportRequest(
        String sessionId,
        String message
    ) {}
}

