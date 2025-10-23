package langhchainback.langchain.AI.RAG;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * Phase 2.1: RAG (Retrieval Augmented Generation) Assistant
 * 
 * RAG란?
 * - 외부 문서/데이터를 기반으로 AI가 답변을 생성하는 기술
 * - 사용자 질문 → 관련 문서 검색 → 문서 + 질문을 AI에 전달 → 정확한 답변 생성
 * 
 * 핵심 프로세스:
 * 1. 문서 로딩 (PDF, TXT, DOCX 등)
 * 2. 문서 분할 (Chunking) - 큰 문서를 작은 단위로 분할
 * 3. 임베딩 (Embedding) - 텍스트를 숫자 벡터로 변환
 * 4. 벡터 저장 (Vector Store) - 임베딩된 벡터를 DB에 저장
 * 5. 유사도 검색 - 사용자 질문과 유사한 문서 조각 검색
 * 6. 답변 생성 - 검색된 문서를 컨텍스트로 사용하여 AI 답변 생성
 * 
 * 활용 사례:
 * - 회사 내부 문서 기반 Q&A 시스템
 * - 제품 매뉴얼 챗봇
 * - 법률/의료 문서 검색 시스템
 * - 고객 지원 자동화
 */
public interface RagAssistant {

    /**
     * 문서 기반 질의응답
     * 
     * @SystemMessage:
     * - AI의 역할을 "문서 전문가"로 정의
     * - {{information}} 플레이스홀더: 검색된 관련 문서 내용이 여기에 주입됨
     * - 문서에 없는 내용은 답변하지 않도록 제한
     * 
     * @UserMessage:
     * - {{question}} 플레이스홀더: 사용자의 실제 질문이 주입됨
     * 
     * 실제 사용 예시:
     * 1. 사용자: "퇴직금 계산 방법은?"
     * 2. 시스템: 벡터 DB에서 "퇴직금" 관련 문서 조각 검색
     * 3. 검색된 문서 내용을 information 파라미터로 전달
     * 4. AI: 문서 기반으로 정확한 답변 생성
     * 
     * @param question 사용자의 질문
     * @param information 검색된 관련 문서 내용 (벡터 유사도 검색 결과)
     * @return AI가 문서 기반으로 생성한 답변
     */
    @SystemMessage("""
            당신은 제공된 문서를 바탕으로 정확한 답변을 제공하는 전문가입니다.
            
            중요 규칙:
            1. 반드시 제공된 문서 정보만을 사용하여 답변하세요.
            2. 문서에 없는 내용은 "제공된 문서에서 해당 정보를 찾을 수 없습니다"라고 답변하세요.
            3. 답변 시 근거가 되는 문서 내용을 인용하세요.
            4. 명확하고 간결하게 답변하세요.
            5. 한국어로 답변하세요.
            
            제공된 문서:
            {{information}}
            """)
    @UserMessage("""
            다음 질문에 답변해주세요:
            {{question}}
            
            답변 형식:
            - 답변: [문서 기반 답변]
            - 출처: [관련 문서 내용 인용]
            """)
    String answer(@V("question") String question, @V("information") String information);

    /**
     * 문서 요약 기능
     * 
     * 긴 문서를 간결하게 요약할 때 사용
     * RAG 시스템에서 검색된 여러 문서 조각을 종합 요약할 때 유용
     * 
     * @param documentContent 요약할 문서 내용
     * @return 요약된 내용
     */
    @SystemMessage("""
            당신은 문서 요약 전문가입니다.
            핵심 내용을 간결하게 정리하여 3-5문장으로 요약합니다.
            """)
    @UserMessage("""
            다음 문서를 요약해주세요:
            
            {{documentContent}}
            
            요약:
            """)
    String summarizeDocument(@V("documentContent") String documentContent);
}

