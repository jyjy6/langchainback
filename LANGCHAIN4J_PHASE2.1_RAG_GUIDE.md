# LangChain4j Phase 2.1: RAG (Retrieval Augmented Generation) ✅

## 📚 학습 목표

- **RAG 개념 이해**: 문서 기반 질의응답 시스템의 핵심 원리
- **벡터 임베딩**: 텍스트를 숫자 벡터로 변환하는 기술
- **유사도 검색**: 코사인 유사도를 활용한 관련 문서 검색
- **문서 처리 파이프라인**: 로딩 → 분할 → 임베딩 → 저장 → 검색
- **실전 응용**: 회사 문서 Q&A, 제품 매뉴얼 챗봇 등

---

## 🎯 구현 완료 내용

### 1. 의존성 추가

**파일**: `build.gradle`

```gradle
// Phase 2.1: RAG (Retrieval Augmented Generation) 지원
// Document loaders - 다양한 파일 형식 지원
implementation "dev.langchain4j:langchain4j-document-parser-apache-tika:${langchain4jVersion}"
// Embedding models - 텍스트를 벡터로 변환
implementation "dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2:${langchain4jVersion}"
```

---

### 2. RAG Assistant 인터페이스

**파일**: `src/main/java/langhchainback/langchain/AI/RAG/RagAssistant.java`

```java
public interface RagAssistant {
    // 문서 기반 질의응답
    String answer(String question, String information);

    // 문서 요약
    String summarizeDocument(String documentContent);
}
```

**핵심 기능**:

- `@SystemMessage`로 문서 기반 답변만 하도록 제한
- `{{information}}` 플레이스홀더에 검색된 문서 주입
- 환각(hallucination) 최소화

---

### 3. RAG Service (핵심 비즈니스 로직)

**파일**: `src/main/java/langhchainback/langchain/AI/RAG/RagService.java`

**3가지 주요 메서드**:

#### 📄 문서 수집 (Document Ingestion)

```java
int ingestDocument(MultipartFile file, String documentId)
```

**프로세스**:

1. **파일 파싱** - Apache Tika로 PDF, DOCX, TXT 등 자동 인식
2. **문서 분할 (Chunking)** - 300자 단위, 30자 겹침
3. **임베딩 생성** - AllMiniLmL6V2 모델 (384차원 벡터)
4. **벡터 저장** - InMemoryEmbeddingStore에 저장

#### 🔍 관련 문서 검색 (Retrieval)

```java
String searchRelevantContent(String question, int maxResults, double minScore)
```

**작동 원리**:

1. 질문을 벡터로 변환
2. 코사인 유사도로 관련 문서 검색
3. 유사도 점수 기준으로 필터링 (기본 0.7)
4. 상위 N개 문서 조각 반환

#### 💬 질의응답 (Question Answering)

```java
String answerQuestion(String question)
```

**전체 RAG 파이프라인**:

1. `searchRelevantContent()` 호출하여 관련 문서 검색
2. 검색된 문서를 `RagAssistant`의 컨텍스트로 전달
3. AI가 문서 기반 답변 생성

---

### 4. REST API Controller

**파일**: `src/main/java/langhchainback/langchain/AI/RAG/RagController.java`

**제공 API**:

| 엔드포인트    | 메서드 | 설명                      |
| ------------- | ------ | ------------------------- |
| `/rag/ingest` | POST   | 문서 업로드 및 벡터화     |
| `/rag/ask`    | POST   | 문서 기반 질의응답        |
| `/rag/search` | POST   | 관련 문서 검색 (디버깅용) |

---

### 5. Spring Bean 설정

**파일**: `src/main/java/langhchainback/langchain/Config/LangChainConfig.java`

**3가지 Bean**:

#### 🧠 Embedding Model

```java
@Bean
public EmbeddingModel embeddingModel()
```

- **AllMiniLmL6V2** 경량 모델 (23MB)
- 로컬 실행 가능 (API 호출 없음)
- 384차원 벡터 생성
- 다국어 지원 (한국어 포함)

#### 💾 Embedding Store

```java
@Bean
public EmbeddingStore<TextSegment> embeddingStore()
```

- **InMemoryEmbeddingStore** (개발용)
- 빠른 속도, 메모리 기반
- ⚠️ 재시작 시 데이터 소실

#### 🤖 RAG Assistant

```java
@Bean
public RagAssistant ragAssistant()
```

- Gemini 2.5 Flash 모델
- Temperature 0.3 (정확성 우선)
- 문서 기반 답변 전문

---

### 6. 프론트엔드 UI

**파일**: `langchainfront/app/pages/rag/index.vue`

**주요 기능**:

- ✅ 파일 업로드 (드래그 & 드롭 지원)
- ✅ 채팅 인터페이스 (사용자/AI 메시지 구분)
- ✅ 실시간 처리 상태 표시
- ✅ 에러 핸들링 및 알림
- ✅ 반응형 레이아웃 (Vuetify)

---

## 🧪 테스트 방법

### 준비 사항

#### 1. 백엔드 실행

```bash
cd langchain
./gradlew bootRun
```

#### 2. 프론트엔드 실행

```bash
cd langchainfront
npm run dev
```

#### 3. 테스트 문서 준비

- PDF, DOCX, TXT 파일 준비
- 예: 회사 규정집, 제품 매뉴얼, FAQ 문서 등

---

## 📡 API 테스트 예제

### 1️⃣ 문서 업로드 (Document Ingestion)

```bash
curl -X POST http://localhost:8080/rag/ingest \
  -F "file=@company_handbook.pdf" \
  -F "documentId=handbook-2024"
```

**응답 예시**:

```json
{
  "success": true,
  "message": "문서가 성공적으로 처리되었습니다.",
  "chunkCount": 42,
  "documentId": "handbook-2024"
}
```

**해석**:

- 문서가 **42개의 청크**로 분할됨
- 각 청크는 약 300자 내외
- 모두 벡터 DB에 저장됨

---

### 2️⃣ 문서 기반 질의응답

```bash
curl -X POST http://localhost:8080/rag/ask \
  -H "Content-Type: application/json" \
  -d '{
    "question": "연차는 몇일인가요?"
  }'
```

**응답 예시**:

```json
{
  "success": true,
  "message": "답변이 생성되었습니다.",
  "answer": "답변: 근속연수에 따라 연차는 15~25일입니다.\n\n출처: 회사 복리후생 규정 3장 2절에 따르면...",
  "originalQuestion": "연차는 몇일인가요?"
}
```

**RAG 파이프라인 실행 과정**:

1. 질문 "연차는 몇일인가요?" 임베딩 생성
2. 벡터 DB에서 유사한 문서 검색 (코사인 유사도)
3. 검색된 문서: "복리후생 규정 3장...", "휴가 사용 안내..." 등
4. AI가 문서 기반으로 답변 생성
5. 출처 정보 포함하여 응답

---

### 3️⃣ 관련 문서 검색 (디버깅용)

```bash
curl -X POST http://localhost:8080/rag/search \
  -H "Content-Type: application/json" \
  -d '{
    "question": "퇴직금 계산 방법",
    "maxResults": 3,
    "minScore": 0.7
  }'
```

**응답 예시**:

```json
{
  "success": true,
  "message": "검색이 완료되었습니다.",
  "relevantContent": "[유사도: 0.89]\n퇴직금 계산 방법은 근속연수 × 평균임금 × 30일입니다...\n\n---\n\n[유사도: 0.76]\n퇴직금 지급 기준은 1년 이상 근무한 직원에게...",
  "resultCount": 2,
  "minScore": 0.7
}
```

**용도**:

- 어떤 문서가 검색되는지 확인
- 유사도 점수 검증
- 청크 분할이 적절한지 검토

---

## 🔑 핵심 개념 정리

### 1. RAG란?

**Retrieval Augmented Generation** - 검색 기반 증강 생성

```
기존 AI: 질문 → AI 모델 → 답변 (학습 데이터 기반)
RAG AI:  질문 → 문서 검색 → AI 모델 + 문서 → 답변
```

**장점**:

- ✅ **정확성**: 실제 문서 기반 답변
- ✅ **최신성**: 문서만 업데이트하면 OK
- ✅ **추적성**: 출처가 명확함
- ✅ **할루시네이션 방지**: 없는 정보 만들지 않음

---

### 2. 벡터 임베딩 (Vector Embedding)

#### 개념

텍스트를 고차원 숫자 배열(벡터)로 변환하는 기술

#### 예시

```
"퇴직금 계산"    → [0.123, -0.456, 0.789, ..., 0.234]  (384개 숫자)
"퇴직금 산정"    → [0.125, -0.450, 0.791, ..., 0.231]  (유사한 벡터!)
"날씨 예보"      → [0.891, 0.234, -0.567, ..., -0.123] (다른 벡터)
```

#### 특징

- 의미가 유사한 텍스트 = 유사한 벡터
- 벡터 간 거리로 유사도 측정 (코사인 유사도)
- 단어가 달라도 의미가 같으면 가까움

---

### 3. 코사인 유사도 (Cosine Similarity)

#### 수식

```
similarity = cos(θ) = (A · B) / (|A| × |B|)
```

#### 범위

- **1.0**: 완전히 동일
- **0.7 ~ 0.9**: 매우 유사 (일반적으로 사용)
- **0.5 ~ 0.7**: 약간 관련
- **< 0.5**: 관련 없음

#### 임계값 설정

```java
// 높은 정확도 필요 (법률, 의료)
double minScore = 0.85;

// 일반적인 사용
double minScore = 0.7;

// 폭넓은 검색
double minScore = 0.5;
```

---

### 4. 문서 분할 (Chunking)

#### 왜 필요한가?

1. **AI 토큰 제한**: GPT-3.5는 4096 토큰, Gemini는 32K 토큰
2. **정확한 검색**: 긴 문서 전체보다 관련 부분만 추출
3. **메모리 효율**: 큰 문서를 한번에 처리 불가

#### 분할 전략

```java
DocumentSplitter splitter = DocumentSplitters.recursive(
    300,  // maxSegmentSizeInChars: 청크 크기
    30    // maxOverlapSizeInChars: 청크 간 겹침
);
```

#### 겹침(Overlap)의 중요성

```
청크 1: "...퇴직금은 근속연수에 비례합니다. 계산 방법은..."
청크 2: "...계산 방법은 다음과 같습니다. 평균임금 × 근속연수..."
                    ↑ 겹치는 부분 (연속성 유지)
```

---

### 5. 전체 RAG 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                    RAG 시스템 흐름                       │
└─────────────────────────────────────────────────────────┘

[1단계: 문서 수집 (Offline)]
파일 업로드 (PDF, DOCX, TXT)
    ↓
파일 파싱 (Apache Tika)
    ↓
문서 분할 (300자 청크, 30자 겹침)
    ↓
임베딩 생성 (AllMiniLmL6V2)
    ↓
벡터 DB 저장 (InMemoryEmbeddingStore)

[2단계: 질의응답 (Online)]
사용자 질문
    ↓
질문 임베딩 생성
    ↓
벡터 유사도 검색 (코사인 유사도)
    ↓
상위 N개 문서 조각 추출
    ↓
AI 모델에 문서 + 질문 전달
    ↓
문서 기반 답변 생성
    ↓
사용자에게 응답
```

---

## 💡 실전 활용 시나리오

### 시나리오 1: 회사 내부 규정 Q&A

**업로드 문서**:

- 인사 규정집 (50 페이지)
- 복리후생 안내 (30 페이지)
- 휴가 사용 가이드 (20 페이지)

**질문 예시**:

```
Q: "육아휴직은 몇 개월까지 가능한가요?"
A: "육아휴직은 최대 12개월까지 가능합니다.
    인사 규정 7장 3절에 따르면..."

Q: "재택근무 신청 절차는?"
A: "재택근무는 다음 절차로 신청합니다:
    1. 통합 시스템 접속
    2. 재택근무 신청서 작성
    ..."
```

---

### 시나리오 2: 제품 기술 지원 챗봇

**업로드 문서**:

- 제품 사용 설명서
- 문제 해결 가이드
- FAQ 모음

**질문 예시**:

```
Q: "배터리가 빨리 닳는데 어떻게 해야 하나요?"
A: "배터리 소모가 빠른 경우 다음을 확인하세요:
    1. 백그라운드 앱 종료
    2. 화면 밝기 조절
    3. 절전 모드 활성화
    (출처: 문제 해결 가이드 3장)"
```

---

### 시나리오 3: 법률/의료 문서 검색

**업로드 문서**:

- 판례 모음
- 법률 조문
- 해석 자료

**질문 예시**:

```
Q: "근로기준법 상 연장근로 한도는?"
A: "근로기준법 제53조에 따르면
    연장근로는 주 12시간을 초과할 수 없습니다.
    단, 특별한 사정이 있는 경우..."
```

---

## 🎓 실전 연습 과제

### 과제 1: 다양한 문서 형식 테스트

**목표**: 다양한 파일 형식 업로드 및 검색 테스트

**단계**:

1. PDF 파일 업로드 (예: 논문, 보고서)
2. DOCX 파일 업로드 (예: 회의록)
3. TXT 파일 업로드 (예: 로그, 메모)
4. 각 문서에 대해 질문하고 정확도 확인

**검증 포인트**:

- [ ] 모든 형식이 정상적으로 파싱되는가?
- [ ] 한글이 깨지지 않는가?
- [ ] 표, 이미지 등은 어떻게 처리되는가?

---

### 과제 2: 청크 크기 최적화

**목표**: 문서 특성에 맞는 최적 청크 크기 찾기

**실험**:

```java
// 작은 청크 (정밀 검색)
DocumentSplitters.recursive(100, 10);

// 중간 청크 (균형)
DocumentSplitters.recursive(300, 30);

// 큰 청크 (문맥 중시)
DocumentSplitters.recursive(500, 50);
```

**비교 분석**:

- 각 설정으로 동일 문서 처리
- 동일 질문으로 답변 품질 비교
- 처리 시간 및 메모리 사용량 측정

---

### 과제 3: 유사도 임계값 실험

**목표**: 최적의 유사도 임계값 찾기

**테스트 케이스**:

```java
// 매우 엄격 (정확도 우선)
searchRelevantContent(question, 3, 0.9);

// 일반 (균형)
searchRelevantContent(question, 3, 0.7);

// 관대 (재현율 우선)
searchRelevantContent(question, 3, 0.5);
```

**평가 기준**:

- **정밀도**: 검색된 문서가 실제로 관련있는가?
- **재현율**: 관련 문서를 모두 찾았는가?
- **사용자 만족도**: 답변이 충분한가?

---

### 과제 4: 다국어 문서 지원

**목표**: 영어, 일본어 등 다국어 문서 처리

**과제 내용**:

1. 영어 PDF 업로드
2. 한국어로 질문
3. 답변이 적절한지 확인

**도전 과제**:

- 질문 언어와 문서 언어가 다른 경우 처리
- 번역 기능 추가 고려

---

## 🔧 성능 최적화 가이드

### 1. 벡터 저장소 업그레이드

#### 현재 (개발용)

```java
@Bean
public EmbeddingStore<TextSegment> embeddingStore() {
    return new InMemoryEmbeddingStore<>();
}
```

#### 프로덕션 (PostgreSQL + pgvector)

```java
@Bean
public EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource) {
    return PgVectorEmbeddingStore.builder()
            .dataSource(dataSource)
            .table("document_embeddings")
            .dimension(384)
            .build();
}
```

**장점**:

- ✅ 영구 저장 (재시작 후에도 유지)
- ✅ 대용량 문서 처리 가능
- ✅ 트랜잭션 지원
- ✅ 백업 및 복구 가능

---

### 2. 임베딩 모델 선택

| 모델           | 차원 | 성능       | 비용 | 용도        |
| -------------- | ---- | ---------- | ---- | ----------- |
| AllMiniLmL6V2  | 384  | ⭐⭐⭐     | 무료 | 개발/소규모 |
| OpenAI ada-002 | 1536 | ⭐⭐⭐⭐⭐ | 유료 | 프로덕션    |
| Google PaLM    | 768  | ⭐⭐⭐⭐   | 유료 | 중대형      |

**OpenAI 임베딩 사용 예시**:

```gradle
implementation "dev.langchain4j:langchain4j-open-ai:${langchain4jVersion}"
```

```java
@Bean
public EmbeddingModel embeddingModel(@Value("${openai.api.key}") String apiKey) {
    return OpenAiEmbeddingModel.builder()
            .apiKey(apiKey)
            .modelName("text-embedding-ada-002")
            .build();
}
```

---

### 3. 캐싱 전략

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    @Cacheable(value = "ragAnswers", key = "#question")
    public String answerQuestion(String question) {
        // 동일 질문은 캐시에서 즉시 반환
        // ...
    }
}
```

**설정**:

```properties
spring.cache.type=redis
spring.redis.host=localhost
spring.redis.port=6379
```

---

## 📊 학습 체크리스트

### 기초 개념

- [ ] RAG가 무엇인지 설명할 수 있다
- [ ] 벡터 임베딩의 원리를 이해한다
- [ ] 코사인 유사도 계산 방법을 안다
- [ ] 문서 청킹이 왜 필요한지 이해한다

### 구현 능력

- [ ] 문서를 업로드하고 벡터화할 수 있다
- [ ] API를 통해 질의응답을 수행할 수 있다
- [ ] 프론트엔드 UI로 RAG 시스템을 사용할 수 있다
- [ ] 검색 결과를 디버깅할 수 있다

### 최적화

- [ ] 청크 크기를 조정할 수 있다
- [ ] 유사도 임계값을 설정할 수 있다
- [ ] 다양한 파일 형식을 처리할 수 있다
- [ ] 프로덕션 벡터 DB로 전환할 수 있다

### 실전 응용

- [ ] 회사 문서 Q&A 시스템을 구축할 수 있다
- [ ] 제품 매뉴얼 챗봇을 만들 수 있다
- [ ] 성능 문제를 진단하고 해결할 수 있다
- [ ] 사용자 피드백을 반영하여 개선할 수 있다

---

## 🚀 다음 단계: Phase 2.2 - AI 에이전트

Phase 2.1 RAG를 완료했다면 다음으로 배울 내용:

### Phase 2.2 주요 내용:

1. **AI Tools** - AI가 함수/도구를 호출하도록 하기
2. **Function Calling** - 날씨 조회, DB 쿼리 등
3. **Multi-Agent System** - 여러 AI가 협업하는 시스템
4. **Autonomous Agent** - 목표를 달성하기 위해 자율적으로 행동

**예시**:

```
사용자: "오늘 서울 날씨 알려주고, 비 오면 우산 추천해줘"

AI 에이전트:
1. [Tool: 날씨 API 호출] → 서울 날씨 = 비
2. [Tool: 상품 검색] → 우산 목록 조회
3. [응답 생성] → "서울은 비가 옵니다. 추천 우산: ..."
```

---

## 🛠️ 문제 해결 가이드

### 문제 1: 문서 업로드 실패

**증상**:

```json
{
  "success": false,
  "message": "문서 처리 중 오류 발생: Could not parse document"
}
```

**원인**: 지원하지 않는 파일 형식 또는 손상된 파일

**해결책**:

1. 파일 형식 확인 (PDF, DOCX, TXT만 지원)
2. 파일 크기 확인 (Spring Boot max file size 설정)
3. 파일 인코딩 확인 (UTF-8 권장)

```properties
# application.properties
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

---

### 문제 2: 검색 결과 없음

**증상**:

```
"관련 문서를 찾을 수 없습니다."
```

**원인**: 유사도 임계값이 너무 높음

**해결책**:

```java
// 임계값을 낮춰서 재검색
searchRelevantContent(question, 5, 0.5);  // 0.7 → 0.5
```

---

### 문제 3: 답변 품질 저하

**증상**: AI가 문서와 관련 없는 답변 생성

**원인**: 검색된 문서가 부적절함

**해결책**:

1. `/rag/search` API로 검색 결과 확인
2. 청크 크기 조정 (300 → 500)
3. 유사도 임계값 상향 (0.7 → 0.8)
4. 문서 품질 개선 (명확한 제목, 구조화된 내용)

---

### 문제 4: 메모리 부족

**증상**:

```
java.lang.OutOfMemoryError: Java heap space
```

**원인**: 대용량 문서 처리 또는 너무 많은 벡터 저장

**해결책**:

```bash
# JVM 힙 메모리 증가
./gradlew bootRun -Dspring-boot.run.jvmArguments="-Xmx2g"
```

또는 프로덕션 벡터 DB로 전환 (PostgreSQL + pgvector)

---

## 📚 참고 자료

### 공식 문서

- [LangChain4j RAG Tutorial](https://docs.langchain4j.dev/tutorials/rag)
- [Apache Tika Documentation](https://tika.apache.org/)
- [Vector Database Comparison](https://www.datacamp.com/blog/the-top-5-vector-databases)

### 임베딩 모델

- [Sentence Transformers](https://www.sbert.net/)
- [OpenAI Embeddings](https://platform.openai.com/docs/guides/embeddings)
- [HuggingFace Models](https://huggingface.co/models?pipeline_tag=sentence-similarity)

### 벡터 DB

- [PostgreSQL pgvector](https://github.com/pgvector/pgvector)
- [Pinecone](https://www.pinecone.io/)
- [Weaviate](https://weaviate.io/)
- [Chroma](https://www.trychroma.com/)

### 논문

- [Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks](https://arxiv.org/abs/2005.11401)
- [Dense Passage Retrieval](https://arxiv.org/abs/2004.04906)

---

## 🎉 축하합니다!

RAG 시스템 구축을 완료했습니다! 🎊

이제 여러분은:

- ✅ 문서 기반 AI 시스템을 구축할 수 있습니다
- ✅ 벡터 임베딩과 유사도 검색을 이해합니다
- ✅ 실전 프로젝트에 RAG를 적용할 수 있습니다
- ✅ 프로덕션 환경으로 확장할 준비가 되었습니다

**다음 도전**:

- Phase 2.2: AI 에이전트 구축
- Phase 3: 멀티모달 AI (이미지, 오디오)
- Phase 4: 프로덕션 배포 (Docker, Kubernetes)

---

**구현 완료 날짜**: 2025-01-23  
**학습 소요 시간**: 2-3시간  
**난이도**: ⭐⭐⭐⭐☆  
**다음 학습**: Phase 2.2 - AI 에이전트 & Function Calling
