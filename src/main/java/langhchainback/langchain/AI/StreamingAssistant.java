package langhchainback.langchain.AI;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * Phase 1.3: 스트리밍 응답 Assistant
 * TokenStream을 반환하여 실시간으로 응답 생성
 */
public interface StreamingAssistant {

    /**
     * 예제 1: 기본 스트리밍 채팅
     * - ChatGPT처럼 답변이 실시간으로 생성됨
     */
    @SystemMessage("""
            당신은 친절하고 전문적인 AI 어시스턴트입니다.
            자세하고 유익한 답변을 제공합니다.
            """)
    TokenStream chat(String message);

    /**
     * 예제 2: 긴 형식의 콘텐츠 생성
     * - 블로그 포스트, 기사 등 긴 텍스트에 적합
     */
    @SystemMessage("당신은 전문 작가입니다. 상세하고 구조화된 콘텐츠를 작성합니다.")
    @UserMessage("다음 주제로 블로그 포스트를 작성해주세요: {{topic}}")
    TokenStream writeBlogPost(String topic);

    /**
     * 예제 3: 코드 생성 (스트리밍)
     * - 긴 코드를 실시간으로 생성
     */
    @SystemMessage("""
            당신은 시니어 개발자입니다.
            완전하고 실행 가능한 코드를 작성합니다.
            주석과 설명을 포함합니다.
            """)
    @UserMessage("""
            {{language}}로 다음 기능을 구현해주세요:
            {{description}}
            
            완전한 코드와 사용 예제를 제공해주세요.
            """)
    TokenStream generateCode(String language, String description);

    /**
     * 예제 4: 스토리 생성
     * - 긴 스토리를 실시간으로 생성
     */
    @SystemMessage("""
            당신은 창의적인 작가입니다.
            흥미진진하고 상세한 스토리를 작성합니다.
            """)
    @UserMessage("""
            다음 설정으로 짧은 이야기를 써주세요:
            장르: {{genre}}
            주제: {{topic}}
            
            1000단어 정도의 완전한 스토리를 작성해주세요.
            """)
    TokenStream writeStory(String genre, String topic);

    /**
     * 예제 5: 문서 분석 및 요약 (긴 응답)
     * - 상세한 분석 결과를 스트리밍
     */
    @SystemMessage("""
            당신은 문서 분석 전문가입니다.
            텍스트를 깊이 있게 분석하고 상세한 인사이트를 제공합니다.
            """)
    @UserMessage("""
            다음 텍스트를 분석해주세요:
            {{text}}
            
            다음 관점에서 분석해주세요:
            1. 주요 내용 요약
            2. 핵심 주제
            3. 감정 톤
            4. 개선 제안
            """)
    TokenStream analyzeDocument(String text);

    /**
     * 예제 6: 강의 자료 생성
     * - 상세한 강의 내용을 스트리밍
     */
    @SystemMessage("""
            당신은 교육 전문가입니다.
            학생들이 이해하기 쉬운 강의 자료를 만듭니다.
            예제와 연습 문제를 포함합니다.
            """)
    @UserMessage("""
            "{{topic}}"에 대한 강의 자료를 작성해주세요.
            대상: {{audience}}
            
            다음을 포함해주세요:
            - 개념 설명
            - 실전 예제
            - 연습 문제
            """)
    TokenStream createLecture(String topic, String audience);

    /**
     * 예제 7: 번역 (긴 텍스트)
     * - 긴 문서를 스트리밍으로 번역
     */
    @SystemMessage("당신은 전문 번역가입니다. 문맥을 고려한 자연스러운 번역을 제공합니다.")
    @UserMessage("다음 텍스트를 {{targetLang}}으로 번역해주세요:\n\n{{text}}")
    TokenStream translateDocument(String text, String targetLang);
}

