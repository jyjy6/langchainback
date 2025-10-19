package langhchainback.langchain.AI;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * LangChain4j Assistant 인터페이스
 * @SystemMessage와 @UserMessage 어노테이션을 활용한 프롬프트 엔지니어링
 */
@AiService
public interface Assistant {

    /**
     * 기본 채팅 (시스템 메시지 없음)
     */
    String chat(String userMessage);

    /**
     * 예제 1: 시스템 메시지로 AI의 역할 정의
     * - 한국어로 답변하는 친절한 AI 어시스턴트
     */
    @SystemMessage("""
            당신은 친절하고 전문적인 AI 어시스턴트입니다.
            항상 한국어로 답변하며, 사용자의 질문에 정확하고 유용한 정보를 제공합니다.
            답변은 간결하면서도 이해하기 쉽게 작성해주세요.
            """)
    String chatWithSystemMessage(String userMessage);

    /**
     * 예제 2: 프롬프트 템플릿 사용 (변수 바인딩)
     * - {{variable}} 형식으로 동적 값 주입
     */
    @SystemMessage("당신은 {{role}} 전문가입니다.")
    @UserMessage("""
            다음 주제에 대해 {{role}}의 관점에서 설명해주세요:
            주제: {{topic}}
            
            상세하고 전문적인 답변을 부탁드립니다.
            """)
    String explainAsExpert(String role, String topic);

    /**
     * 예제 3: 코드 리뷰 어시스턴트
     */
    @SystemMessage("""
            당신은 시니어 개발자입니다.
            코드 리뷰를 수행하며, 다음 관점에서 피드백을 제공합니다:
            1. 코드 품질
            2. 성능 최적화
            3. 보안 취약점
            4. 베스트 프랙티스
            """)
    @UserMessage("""
            다음 {{language}} 코드를 리뷰해주세요:
            
            ```{{language}}
            {{code}}
            ```
            
            개선이 필요한 부분과 구체적인 수정 방안을 알려주세요.
            """)
    String reviewCode(String language, String code);

    /**
     * 예제 4: 번역 어시스턴트
     */
    @SystemMessage("당신은 전문 번역가입니다. 문맥을 고려하여 자연스럽게 번역합니다.")
    @UserMessage("다음 텍스트를 {{sourceLang}}에서 {{targetLang}}(으)로 번역해주세요: {{text}}")
    String translate(String sourceLang, String targetLang, String text);

    /**
     * 예제 5: 요약 어시스턴트
     */
    @SystemMessage("""
            당신은 텍스트 요약 전문가입니다.
            핵심 내용을 간결하게 정리하여 전달합니다.
            """)
    @UserMessage("""
            다음 텍스트를 {{maxWords}}단어 이내로 요약해주세요:
            
            {{text}}
            
            요약:
            """)
    String summarize(String text, int maxWords);

    /**
     * 예제 6: SQL 생성 어시스턴트
     */
    @SystemMessage("""
            당신은 데이터베이스 전문가입니다.
            자연어 요청을 SQL 쿼리로 변환합니다.
            MySQL 문법을 사용하며, 쿼리는 최적화되어야 합니다.
            """)
    @UserMessage("""
            다음 요청사항에 맞는 SQL 쿼리를 작성해주세요:
            
            테이블: {{tableName}}
            요청: {{request}}
            
            SQL 쿼리만 반환하고, 설명은 주석으로 추가해주세요.
            """)
    String generateSql(String tableName, String request);

    /**
     * 예제 7: 감정 분석 어시스턴트
     */
    @SystemMessage("""
            당신은 텍스트 감정 분석 전문가입니다.
            텍스트의 감정을 분석하고 '긍정', '부정', '중립' 중 하나로 분류합니다.
            """)
    @UserMessage("""
            다음 텍스트의 감정을 분석해주세요:
            "{{text}}"
            
            결과를 다음 형식으로 답변해주세요:
            감정: [긍정/부정/중립]
            신뢰도: [0-100]%
            이유: [간단한 설명]
            """)
    String analyzeSentiment(String text);
}