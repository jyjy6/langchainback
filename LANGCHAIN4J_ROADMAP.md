# LangChain4j 학습 로드맵 🗺️

> **목표**: Gemini 기반 LangChain4j 전문가 되기  
> **기간**: 약 2-3주 (하루 1-2시간 학습 기준)  
> **현재 진행**: ✅ Phase 1 완료

---

## 📍 전체 로드맵 개요

```
Phase 1: 기본 대화 강화 ✅
    ↓
Phase 2: 고급 기능
    ↓
Phase 3: 실전 애플리케이션
    ↓
Phase 4: 프로덕션 배포
```

---

## 🎯 Phase 1: 기본 대화 강화 (완료 ✅)

### 1.1 시스템 메시지 & 프롬프트 템플릿 ✅

**학습 시간**: 15분  
**난이도**: ⭐☆☆☆☆

**학습 내용**:

- `@SystemMessage` - AI 역할 정의
- `@UserMessage` - 프롬프트 템플릿
- `{{변수}}` 바인딩

**구현 완료**:

- ✅ 7가지 실전 예제 (코드 리뷰, 번역, SQL 생성 등)
- ✅ REST API 엔드포인트
- ✅ 테스트 가이드 문서

**다음 단계**: `LANGCHAIN4J_PHASE1_GUIDE.md` 참고

---

### 1.2 메모리 추가 (대화 컨텍스트 유지) 🔜

**학습 시간**: 20분  
**난이도**: ⭐⭐☆☆☆

**학습 목표**:

- 대화 이력을 기억하는 AI 구현
- 세션별 대화 관리
- 메모리 크기 제한 설정

**구현 내용**:

```java
// ChatMemory 인터페이스
interface ConversationalAssistant {
    String chat(String userId, String message);
}

// Config 설정
@Bean
public ConversationalAssistant conversationalAssistant() {
    return AiServices.builder(ConversationalAssistant.class)
        .chatLanguageModel(model)
        .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
        .build();
}
```

**예제 시나리오**:

```
사용자: 내 이름은 철수야
AI: 안녕하세요, 철수님!

사용자: 내 이름이 뭐지?
AI: 철수님이라고 하셨습니다! ← 이전 대화 기억!
```

**메모리 타입**:

1. **MessageWindowChatMemory** - 최근 N개 메시지 저장
2. **TokenWindowChatMemory** - 토큰 기반 제한
3. **Custom Memory** - 데이터베이스 영구 저장

---

### 1.3 Streaming 응답 🔜

**학습 시간**: 30분  
**난이도**: ⭐⭐⭐☆☆

**학습 목표**:

- 실시간 스트리밍 응답 구현
- Server-Sent Events (SSE)
- WebFlux/Reactive 패턴

**구현 내용**:

```java
interface StreamingAssistant {
    TokenStream chat(String message);
}

@GetMapping(value = "/ai/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> streamChat(@RequestParam String message) {
    return Flux.create(sink -> {
        assistant.chat(message)
            .onNext(sink::next)
            .onComplete(c -> sink.complete())
            .onError(sink::error)
            .start();
    });
}
```

**활용 예시**:

- ChatGPT처럼 답변이 실시간으로 생성되는 UI
- 긴 답변의 사용자 경험 개선
- 프로그레스 표시

---

## 🚀 Phase 2: 고급 기능 (예정)

### 2.1 RAG (Retrieval Augmented Generation) 🔥

**학습 시간**: 2시간  
**난이도**: ⭐⭐⭐⭐☆

**학습 목표**:

- 문서 기반 질의응답 시스템
- 벡터 임베딩 이해
- 유사도 검색 구현

**핵심 개념**:

```
사용자 질문 → 관련 문서 검색 → 문서 + 질문을 AI에 전달 → 답변 생성
```

**구현 단계**:

#### 2.1.1 문서 로딩

```java
// PDF, TXT, Markdown 등 다양한 포맷 지원
Document document = FileSystemDocumentLoader.loadDocument("/path/to/doc.pdf");
```

#### 2.1.2 문서 분할 (Chunking)

```java
DocumentSplitter splitter = DocumentSplitters.recursive(
    300,  // chunk size
    0     // overlap
);
List<TextSegment> segments = splitter.split(document);
```

#### 2.1.3 임베딩 & 저장

```java
EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

for (TextSegment segment : segments) {
    Embedding embedding = embeddingModel.embed(segment).content();
    embeddingStore.add(embedding, segment);
}
```

#### 2.1.4 질의 & 답변

```java
@SystemMessage("다음 정보를 바탕으로 질문에 답변하세요: {{information}}")
String answer(String question, String information);

// 사용
String answer = assistant.answer(
    question,
    retrievedDocuments
);
```

**실전 활용**:

- 회사 문서 기반 Q&A 시스템
- 제품 매뉴얼 챗봇
- 법률/의료 문서 검색

**벡터 저장소 옵션**:

- `InMemoryEmbeddingStore` - 개발/테스트용
- `PostgreSQL pgvector` - 프로덕션 (추천)
- `Pinecone` - 클라우드 벡터 DB
- `Chroma` - 오픈소스

---

### 2.2 도구(Tools) & 함수 호출 🛠️

**학습 시간**: 1시간  
**난이도**: ⭐⭐⭐☆☆

**학습 목표**:

- AI가 외부 함수 호출 가능하도록 구현
- 실시간 데이터 제공
- 복잡한 작업 자동화

**핵심 개념**:

```
AI가 "날씨가 어때?"라는 질문을 받으면
→ AI가 판단: "날씨 조회 함수를 호출해야겠다"
→ getWeather("서울") 함수 실행
→ 결과를 받아서 자연어로 답변
```

**구현 예제**:

```java
public class WeatherService {

    @Tool("현재 날씨를 조회합니다")
    public String getWeather(
        @P("도시 이름") String city
    ) {
        // 실제 날씨 API 호출
        return "서울의 현재 날씨: 맑음, 기온 23도";
    }

    @Tool("환율을 조회합니다")
    public double getExchangeRate(
        @P("통화 코드") String currency
    ) {
        // 환율 API 호출
        return 1300.5;
    }
}

// Assistant 설정
@Bean
public Assistant assistantWithTools() {
    return AiServices.builder(Assistant.class)
        .chatLanguageModel(model)
        .tools(new WeatherService())
        .build();
}
```

**대화 예시**:

```
사용자: 서울 날씨 어때?
AI: [내부적으로 getWeather("서울") 호출]
AI: 서울은 현재 맑고 기온이 23도입니다!

사용자: 미국 달러 환율 알려줘
AI: [내부적으로 getExchangeRate("USD") 호출]
AI: 현재 미국 달러 환율은 1,300.5원입니다.
```

**실전 도구 예제**:

- 데이터베이스 쿼리 실행
- 이메일 발송
- 캘린더 일정 추가
- 파일 저장/읽기
- 외부 API 호출

---

### 2.3 구조화된 출력 (Structured Output) 📊

**학습 시간**: 45분  
**난이도**: ⭐⭐⭐☆☆

**학습 목표**:

- AI 응답을 Java 객체로 직접 변환
- JSON 파싱 없이 타입 안전한 결과
- 복잡한 데이터 추출

**구현 예제**:

```java
// DTO 정의
record Person(
    String name,
    int age,
    String email,
    List<String> skills
) {}

// Assistant 인터페이스
interface PersonExtractor {
    @UserMessage("다음 텍스트에서 인물 정보를 추출하세요: {{text}}")
    Person extractPerson(String text);
}

// 사용
String text = "제 이름은 김철수이고, 30세입니다. 이메일은 chulsoo@example.com이며, Java와 Python을 다룰 수 있습니다.";
Person person = extractor.extractPerson(text);

// 결과
// Person[name=김철수, age=30, email=chulsoo@example.com, skills=[Java, Python]]
```

**고급 예제**:

```java
record Invoice(
    String invoiceNumber,
    LocalDate date,
    String customerName,
    List<LineItem> items,
    BigDecimal totalAmount
) {}

record LineItem(
    String productName,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal totalPrice
) {}

interface InvoiceExtractor {
    Invoice extractInvoice(String invoiceText);
}
```

**실전 활용**:

- PDF 청구서 데이터 추출
- 이메일 내용 파싱
- 양식 자동 입력
- 데이터 마이그레이션

---

### 2.4 이미지 처리 (Multimodal) 🖼️

**학습 시간**: 30분  
**난이도**: ⭐⭐☆☆☆

**학습 목표**:

- Gemini Vision 활용
- 이미지 분석 및 설명
- OCR (텍스트 추출)

**구현 예제**:

```java
interface VisionAssistant {
    @UserMessage("이 이미지를 분석해주세요")
    String analyzeImage(ImageContent image);

    @UserMessage("이 이미지의 텍스트를 추출해주세요")
    String extractText(ImageContent image);
}

// 사용
ImageContent image = ImageContent.from("/path/to/image.jpg");
String description = assistant.analyzeImage(image);
```

**실전 활용**:

- 제품 이미지 자동 태깅
- 영수증 OCR
- 차량 번호판 인식
- 의료 이미지 분석 (보조)

---

## 💼 Phase 3: 실전 애플리케이션 (예정)

### 3.1 프로젝트: 고객 지원 챗봇

**기능**:

- RAG로 FAQ 문서 검색
- 주문 조회 (Tool)
- 대화 이력 유지 (Memory)
- 감정 분석으로 불만 감지

**기술 스택**:

```
LangChain4j + Spring Boot + MySQL + Vue.js
```

---

### 3.2 프로젝트: 문서 분석 시스템

**기능**:

- PDF/Word 문서 업로드
- 자동 요약 생성
- 키워드 추출
- Q&A 인터페이스

---

### 3.3 프로젝트: 코드 어시스턴트

**기능**:

- GitHub 저장소 분석
- 코드 리뷰 자동화
- 버그 탐지
- 리팩토링 제안

---

## 🏭 Phase 4: 프로덕션 배포 (예정)

### 4.1 성능 최적화

**주요 내용**:

- 응답 캐싱
- 배치 처리
- 비동기 처리
- 병렬 처리

---

### 4.2 비용 관리

**전략**:

- 토큰 사용량 모니터링
- 레이트 리미팅
- 요청 우선순위 관리
- 캐싱으로 API 호출 감소

**예제**:

```java
@Bean
public ChatLanguageModel geminiWithLimit() {
    return GoogleAiGeminiChatModel.builder()
        .apiKey(apiKey)
        .modelName("gemini-2.5-flash")
        .maxTokens(1000)  // 토큰 제한
        .timeout(Duration.ofSeconds(30))  // 타임아웃
        .build();
}
```

---

### 4.3 에러 처리 & 재시도

```java
@Bean
public ChatLanguageModel resilientModel() {
    return GoogleAiGeminiChatModel.builder()
        .apiKey(apiKey)
        .modelName("gemini-2.5-flash")
        .maxRetries(3)  // 자동 재시도
        .logRequests(true)
        .logResponses(true)
        .build();
}

// 커스텀 에러 핸들링
@ControllerAdvice
public class LangChainExceptionHandler {

    @ExceptionHandler(ModelException.class)
    public ResponseEntity<ErrorResponse> handleModelException(ModelException e) {
        log.error("AI model error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorResponse("AI 서비스 일시적 오류"));
    }
}
```

---

### 4.4 모니터링 & 로깅

**주요 메트릭**:

- 응답 시간
- 토큰 사용량
- 에러율
- 비용

**구현**:

```java
@Aspect
@Component
@Slf4j
public class AIMetricsAspect {

    @Around("@annotation(AiService)")
    public Object logMetrics(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;

            log.info("AI Request completed in {}ms", duration);
            // Prometheus 메트릭 전송

            return result;
        } catch (Exception e) {
            log.error("AI Request failed", e);
            throw e;
        }
    }
}
```

---

### 4.5 보안

**체크리스트**:

- [ ] API 키는 환경 변수로 관리
- [ ] 요청 인증/인가
- [ ] 입력 검증 (프롬프트 인젝션 방지)
- [ ] 출력 필터링 (민감 정보 마스킹)
- [ ] Rate limiting
- [ ] HTTPS 사용

**프롬프트 인젝션 방지**:

```java
public String sanitizeInput(String userInput) {
    // 악의적인 프롬프트 탐지
    if (userInput.contains("ignore previous instructions")) {
        throw new SecurityException("Invalid input detected");
    }
    return userInput;
}
```

---

## 📚 학습 리소스

### 공식 문서

- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [Google Gemini API](https://ai.google.dev/docs)
- [Spring AI](https://spring.io/projects/spring-ai)

### 튜토리얼

- [LangChain4j GitHub](https://github.com/langchain4j/langchain4j)
- [LangChain4j Examples](https://github.com/langchain4j/langchain4j-examples)

### 커뮤니티

- [LangChain4j Discord](https://discord.gg/langchain4j)
- [Stack Overflow](https://stackoverflow.com/questions/tagged/langchain4j)

---

## 🎯 학습 진행 체크리스트

### Phase 1: 기본 (1주차)

- [x] 기본 Assistant 연결
- [x] 시스템 메시지 & 프롬프트 템플릿
- [ ] 대화 메모리 추가
- [ ] 스트리밍 응답

### Phase 2: 고급 (2주차)

- [ ] RAG 구현
- [ ] Tools & 함수 호출
- [ ] 구조화된 출력
- [ ] 이미지 처리

### Phase 3: 실전 (3주차)

- [ ] 프로젝트 1 완성
- [ ] 프로젝트 2 완성
- [ ] 통합 테스트

### Phase 4: 배포 (4주차)

- [ ] 성능 최적화
- [ ] 에러 처리
- [ ] 모니터링 설정
- [ ] 프로덕션 배포

---

## 🏆 학습 목표

### 초급 (1-2주)

✅ LangChain4j 기본 개념 이해  
✅ 간단한 챗봇 구현  
⬜ 프롬프트 엔지니어링 기초

### 중급 (3-4주)

⬜ RAG 시스템 구축  
⬜ Tools 활용  
⬜ 실전 프로젝트 완성

### 고급 (5-6주)

⬜ 복잡한 에이전트 시스템  
⬜ 성능 최적화  
⬜ 프로덕션 배포

---

## 💪 다음 액션

### 지금 바로 할 수 있는 것:

1. **Phase 1.1 테스트** (15분)

   ```bash
   cd langchain
   ./gradlew bootRun

   # 브라우저나 curl로 API 테스트
   curl "http://localhost:8080/ai/chat?message=안녕하세요"
   ```

2. **LANGCHAIN4J_PHASE1_GUIDE.md 읽기** (10분)

   - 8가지 예제 모두 테스트
   - 실전 연습 과제 도전

3. **Phase 1.2 준비** (내일 목표)
   - ChatMemory 개념 학습
   - 대화형 챗봇 구현 계획

---

**현재 위치**: Phase 1.1 완료 ✅  
**다음 목표**: Phase 1.2 - 메모리 추가  
**최종 목표**: 실전 프로젝트 완성 및 배포

**화이팅! 🚀**
