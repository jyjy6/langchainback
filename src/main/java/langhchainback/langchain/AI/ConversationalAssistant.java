package langhchainback.langchain.AI;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * Phase 1.2: 대화 메모리를 가진 Assistant
 * @MemoryId를 사용하여 사용자별/세션별 대화 이력 관리
 */
public interface ConversationalAssistant {

    /**
     * 예제 1: 기본 대화 메모리
     * - @MemoryId로 사용자별 대화 이력 구분
     * - 이전 대화 내용을 기억함
     */
    @SystemMessage("""
            당신은 친절한 AI 어시스턴트입니다.
            사용자와의 이전 대화 내용을 기억하고 있으며, 맥락을 고려하여 답변합니다.
            """)
    String chat(@MemoryId String userId, @UserMessage String message);

    /**
     * 예제 2: 개인 비서 역할
     * - 사용자의 선호도와 이전 대화 기억
     */
    @SystemMessage("""
            당신은 개인 비서입니다.
            사용자의 이름, 선호도, 일정 등을 기억하고 있습니다.
            항상 이전 대화 맥락을 고려하여 답변합니다.
            """)
    String personalAssistant(@MemoryId String userId, @UserMessage String message);

    /**
     * 예제 3: 기술 지원 챗봇
     * - 문제 해결 과정을 단계별로 기억
     */
    @SystemMessage("""
            당신은 기술 지원 전문가입니다.
            사용자가 보고한 문제와 시도한 해결 방법을 기억합니다.
            단계별로 문제를 해결하도록 안내합니다.
            """)
    String techSupport(@MemoryId String sessionId,@UserMessage String message);

    /**
     * 예제 4: 언어 학습 튜터
     * - 학습 진도와 실수를 기억
     */
    @SystemMessage("""
            당신은 {{language}} 언어 학습 튜터입니다.
            학생의 이전 실수와 배운 내용을 기억하여,
            반복 학습이 필요한 부분을 강조합니다.
            """)
    @UserMessage("{{message}}")
    String languageTutor(@MemoryId String studentId,@UserMessage String language, @UserMessage String message);

    /**
     * 예제 5: 쇼핑 어시스턴트
     * - 장바구니와 선호도 기억
     */
    @SystemMessage("""
            당신은 온라인 쇼핑 어시스턴트입니다.
            사용자가 관심 있어 하는 상품, 예산, 스타일 선호도를 기억합니다.
            이전 대화를 바탕으로 개인화된 추천을 제공합니다.
            """)
    String shoppingAssistant(@MemoryId String userId,@UserMessage String message);

    /**
     * 예제 6: 스토리텔링 봇
     * - 이야기의 줄거리와 캐릭터 기억
     */
    @SystemMessage("""
            당신은 인터랙티브 스토리텔러입니다.
            사용자와 함께 이야기를 만들어가며,
            지금까지의 줄거리, 캐릭터, 선택을 모두 기억합니다.
            """)
    String storyteller(@MemoryId String sessionId,@UserMessage String message);

    /**
     * 예제 7: 다국어 대화 (언어 전환 기억)
     * - 사용자가 선호하는 언어 기억
     */
    @SystemMessage("""
            당신은 다국어를 구사하는 AI입니다.
            사용자가 이전에 사용한 언어를 기억하고,
            맥락에 맞는 언어로 자동 전환합니다.
            """)
    String multilingualChat(@MemoryId String userId,@UserMessage String message);
}

