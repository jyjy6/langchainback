# LangChain4j Phase 1.2: 대화 메모리 ✅

## 📚 학습 목표

- `@MemoryId`: 사용자/세션별 대화 이력 구분
- `ChatMemory`: 대화 이력 저장 및 관리
- `MessageWindowChatMemory`: 최근 N개 메시지만 유지
- `InMemoryChatMemoryStore`: 메모리 저장소

---

## 🎯 구현 완료 내용

### 1. ConversationalAssistant 인터페이스

**파일**: `src/main/java/langhchainback/langchain/AI/ConversationalAssistant.java`

총 **7가지 실전 예제** 구현:

1. **기본 대화 메모리** - 이전 대화 기억
2. **개인 비서** - 이름, 선호도, 일정 기억
3. **기술 지원 챗봇** - 문제 해결 과정 기억
4. **언어 학습 튜터** - 학습 진도와 실수 기억
5. **쇼핑 어시스턴트** - 장바구니와 선호도 기억
6. **스토리텔링 봇** - 줄거리와 캐릭터 기억
7. **다국어 대화** - 언어 선호도 기억

### 2. LangChainConfig 설정

**파일**: `src/main/java/langhchainback/langchain/Config/LangChainConfig.java`

```java
@Bean
public ConversationalAssistant conversationalAssistant() {
    InMemoryChatMemoryStore store = new InMemoryChatMemoryStore();

    return AiServices.builder(ConversationalAssistant.class)
        .chatLanguageModel(model)
        .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
            .id(memoryId)        // 사용자/세션 ID
            .maxMessages(20)     // 최근 20개 메시지만 유지
            .chatMemoryStore(store)
            .build())
        .build();
}
```

### 3. ConversationalController

**파일**: `src/main/java/langhchainback/langchain/AI/ConversationalController.java`

모든 대화 메모리 기능에 대응하는 API 엔드포인트 제공

---

## 🔑 핵심 개념

### 1. @MemoryId 어노테이션

```java
String chat(@MemoryId String userId, String message);
```

**역할**:

- 사용자/세션별로 대화 이력을 **구분**
- 같은 `memoryId`를 사용하면 **이전 대화 기억**
- 다른 `memoryId`는 완전히 **독립적인 대화**


- 💡 1️⃣ @MemoryId의 역할  
LangChain4j는 다음 두 가지를 기본적으로 관리합니다.
- | 항목                | 설명                              |
  | ----------------- | ------------------------------- |
  | `MemoryId`        | 특정 사용자 또는 세션을 구분하는 키            |
  | `ChatMemoryStore` | `MemoryId`별로 대화 이력을 저장하는 실제 저장소 |

즉, @MemoryId는 단순히 “이 사용자의 메모리를 찾아라”라는 신호이고,
**실제 대화 내용(이전 메시지들)**은 ChatMemoryStore라는 객체가 담당합니다.
- 💾 2️⃣ 그럼 “어디에” 저장되나?
  기본적으로 LangChain4j는 **인메모리 저장소(In-Memory MemoryStore)**를 사용합니다.
  즉, 애플리케이션이 살아 있는 동안 JVM 내부 메모리에 보관되고,
  서버 재시작 시 날아갑니다.

기본 구현체는 다음과 같습니다 👇

ChatMemoryStore memoryStore = new InMemoryChatMemoryStore();

즉, 메모리가 사라지지 않으려면 (예: 사용자별 대화 지속 유지)
다음과 같은 영속 저장소 구현체를 직접 연결해야 합니다:

| 구현체                                | 저장 위치         | 설명              |
| ---------------------------------- | ------------- | --------------- |
| `InMemoryChatMemoryStore`          | JVM 힙         | 기본값, 서버 꺼지면 사라짐 |
| `RedisChatMemoryStore`             | Redis         | 여러 서버 간 공유 가능   |
| `MongoChatMemoryStore`             | MongoDB       | 영구 저장 가능        |
| `JdbcChatMemoryStore`              | RDB (MySQL 등) | SQL 기반 저장       |
| 직접 구현 (`ChatMemoryStore` 인터페이스 구현) | 자유            | 예: 파일, S3 등     |

- 3️⃣ 코드 예시 는 LangChaingConfig에 정의됨.




- 🧠 4️⃣정리하자면

| 질문                   | 답변                                                    |
| -------------------- | ----------------------------------------------------- |
| Q. MemoryId는 뭐임?     | 사용자의 대화를 구분하는 키                                       |
| Q. 이전 대화 내용은 어디 저장됨? | `ChatMemoryStore` (기본은 InMemoryChatMemoryStore)       |
| Q. LLM은 어떻게 맥락을 파악함? | LangChain4j가 MemoryStore에서 과거 대화 목록을 꺼내서 프롬프트에 함께 넣어줌 |
| Q. 서버 재시작하면?         | InMemory면 날아감. Redis나 DB Store 써야 영속 가능               |






**사용 예시**:

```java
// user1의 대화
assistant.chat("user1", "내 이름은 철수야");
assistant.chat("user1", "내 이름이 뭐지?"); // → "철수님이라고 하셨습니다"

// user2의 대화 (독립적)
assistant.chat("user2", "내 이름은 영희야");
assistant.chat("user2", "내 이름이 뭐지?"); // → "영희님이라고 하셨습니다"
```

---

### 2. ChatMemory & MessageWindowChatMemory

**MessageWindowChatMemory**:

- 최근 N개의 메시지만 유지
- 오래된 메시지는 자동 삭제
- 토큰 제한에 유용

```java
MessageWindowChatMemory.builder()
    .id("user123")           // 메모리 ID
    .maxMessages(20)         // 최근 20개만 유지
    .chatMemoryStore(store)  // 저장소
    .build()
```

**TokenWindowChatMemory**:

- 토큰 수 기반 제한
- 더 정확한 비용 관리

```java
TokenWindowChatMemory.builder()
    .id("user123")
    .maxTokens(1000)         // 최대 1000 토큰
    .chatMemoryStore(store)
    .build()
```

---

### 3. InMemoryChatMemoryStore

**특징**:

- 메모리에 대화 이력 저장
- 서버 재시작 시 **데이터 소실**
- 개발/테스트용으로 적합

**프로덕션 대안**:

```java
// 1. 파일 기반 저장 (간단)
FileChatMemoryStore fileStore = new FileChatMemoryStore("/path/to/memory");

// 2. 데이터베이스 저장 (권장)
// Custom ChatMemoryStore 구현
public class DatabaseChatMemoryStore implements ChatMemoryStore {
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        // DB에서 메시지 조회
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // DB에 메시지 저장
    }
}
```

---

## 🧪 테스트 방법

### 준비 사항

1. **의존성 설치**

```bash
cd langchain
./gradlew build
```

2. **애플리케이션 실행**

```bash
./gradlew bootRun
```

3. **API 베이스 URL**: `http://localhost:8080`

---

## 📡 API 테스트 예제

### 1️⃣ 기본 대화 메모리 - 이름 기억하기

**Step 1: 이름 알려주기**

```bash
curl "http://localhost:8080/ai/memory/chat?userId=user1&message=내%20이름은%20철수야"
```

**응답**:

```json
{
  "response": "안녕하세요, 철수님! 반갑습니다. 무엇을 도와드릴까요?",
  "memoryId": "user1",
  "durationMs": 1234
}
```

**Step 2: 이름 확인하기 (메모리 테스트)**

```bash
curl "http://localhost:8080/ai/memory/chat?userId=user1&message=내%20이름이%20뭐였지?"
```

**응답**:

```json
{
  "response": "철수님이라고 하셨습니다!",
  "memoryId": "user1",
  "durationMs": 987
}
```

**Step 3: 다른 사용자 (독립적인 대화)**

```bash
curl "http://localhost:8080/ai/memory/chat?userId=user2&message=내%20이름이%20뭐였지?"
```

**응답**:

```json
{
  "response": "죄송하지만 아직 이름을 들어보지 못했습니다. 이름을 알려주시겠어요?",
  "memoryId": "user2",
  "durationMs": 876
}
```

---

### 2️⃣ 개인 비서 - 일정 기억하기

**Step 1: 일정 등록**

```bash
curl "http://localhost:8080/ai/memory/assistant?userId=user1&message=내일%209시에%20회의%20있어"
```

**응답**:

```
알겠습니다! 내일 9시에 회의가 있으시군요. 기억해두겠습니다.
회의와 관련하여 준비해야 할 사항이 있으신가요?
```

**Step 2: 일정 확인**

```bash
curl "http://localhost:8080/ai/memory/assistant?userId=user1&message=내일%20일정%20뭐였지?"
```

**응답**:

```
내일 9시에 회의가 있으시다고 하셨습니다!
```

**Step 3: 추가 정보**

```bash
curl "http://localhost:8080/ai/memory/assistant?userId=user1&message=회의%20자료%20준비해야%20해"
```

**응답**:

```
네, 내일 9시 회의를 위한 자료 준비가 필요하시군요.
오늘 중에 준비하시는 게 좋겠습니다!
```

---

### 3️⃣ 기술 지원 - 문제 해결 과정 기억

**POST 요청 예제**:

```bash
curl -X POST http://localhost:8080/ai/memory/support \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "support123",
    "message": "인터넷이 안 돼요"
  }'
```

**응답**:

```json
{
  "response": "인터넷 연결 문제를 겪고 계시는군요. 먼저 확인해볼까요?\n1. 모뎀과 라우터의 전원이 켜져 있나요?\n2. 랜선이 제대로 연결되어 있나요?",
  "memoryId": "support123",
  "durationMs": null
}
```

**Step 2: 답변 및 추가 질문**

```bash
curl -X POST http://localhost:8080/ai/memory/support \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "support123",
    "message": "네, 다 켜져 있어요"
  }'
```

**응답**:

```
알겠습니다. 모뎀과 라우터는 정상이군요.
그럼 다음 단계로, 컴퓨터를 재부팅해보시겠어요?
```

---

### 4️⃣ 언어 학습 튜터 - 실수 기억

```bash
# Step 1: 첫 문장
curl "http://localhost:8080/ai/memory/tutor?studentId=student1&language=영어&message=How%20is%20you?"

# 응답: "Good try! 하지만 'How is you?'가 아니라 'How are you?'가 맞습니다."

# Step 2: 같은 실수 반복
curl "http://localhost:8080/ai/memory/tutor?studentId=student1&language=영어&message=How%20is%20he?"

# 응답: "아까도 배웠듯이 be동사는 주어에 따라 변해요. 'is'는 he/she/it에만 쓰입니다!"
```

---

### 5️⃣ 쇼핑 어시스턴트 - 선호도 기억

```bash
# Step 1: 예산 알리기
curl "http://localhost:8080/ai/memory/shopping?userId=user1&message=200만원%20정도%20예산으로%20노트북%20찾아줘"

# Step 2: 선호도 추가
curl "http://localhost:8080/ai/memory/shopping?userId=user1&message=게임은%20안하고%20프로그래밍만%20해"

# Step 3: 추천 받기
curl "http://localhost:8080/ai/memory/shopping?userId=user1&message=추천해줘"

# AI가 예산(200만원), 용도(프로그래밍), 선호도(게임 X)를 모두 고려한 추천 제공
```

---

### 6️⃣ 스토리텔링 - 줄거리 기억

```bash
# Step 1: 이야기 시작
curl "http://localhost:8080/ai/memory/story?sessionId=story1&message=우주선을%20타고%20여행을%20떠났어요"

# Step 2: 이야기 전개
curl "http://localhost:8080/ai/memory/story?sessionId=story1&message=갑자기%20외계인을%20만났어요"

# Step 3: 이야기 계속
curl "http://localhost:8080/ai/memory/story?sessionId=story1&message=외계인과%20친구가%20됐어요.%20이제%20어떻게%20되나요?"

# AI가 지금까지의 줄거리(우주선 → 외계인 만남 → 친구)를 모두 기억하고 이야기 전개
```

---

### 7️⃣ 다국어 대화 - 언어 전환 기억

```bash
# Step 1: 영어로 시작
curl "http://localhost:8080/ai/memory/multilingual?userId=user1&message=Hello"

# 응답: "Hello! How can I help you?"

# Step 2: 한국어로 전환
curl "http://localhost:8080/ai/memory/multilingual?userId=user1&message=안녕하세요"

# 응답: "안녕하세요! 한국어로 도와드리겠습니다."

# Step 3: 계속 한국어 사용
curl "http://localhost:8080/ai/memory/multilingual?userId=user1&message=날씨%20어때요?"

# AI가 한국어 선호를 기억하고 한국어로 계속 대화
```

---

## 💡 고급 활용 팁

### 1. 메모리 크기 조정

```java
// 짧은 대화 (간단한 Q&A)
.maxMessages(5)

// 일반 대화
.maxMessages(20)

// 긴 대화 (복잡한 문맥)
.maxMessages(50)

// 토큰 기반 (더 정확)
TokenWindowChatMemory.builder()
    .maxTokens(2000)
    .build()
```

---

### 2. 컨텍스트 초기화

```java
// 대화 이력 삭제 API 추가
@DeleteMapping("/memory/clear")
public void clearMemory(@RequestParam String userId) {
    chatMemoryStore.deleteMessages(userId);
}
```

---

### 3. 대화 이력 조회

```java
@GetMapping("/memory/history")
public List<ChatMessage> getHistory(@RequestParam String userId) {
    return chatMemoryStore.getMessages(userId);
}
```

---

### 4. 세션 타임아웃 구현

```java
public class ExpiringChatMemoryStore implements ChatMemoryStore {
    private final Map<Object, TimestampedMessages> store = new ConcurrentHashMap<>();
    private final Duration timeout = Duration.ofHours(1);

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        TimestampedMessages messages = store.get(memoryId);
        if (messages != null && !messages.isExpired(timeout)) {
            return messages.getMessages();
        }
        return List.of();
    }
}
```

---

## 🎓 실전 연습 과제

### 과제 1: 주문 추적 챗봇

**요구사항**:

- 사용자가 주문 번호를 물어보면 기억
- 배송 상태 업데이트 시 이전 대화 참고
- 같은 주문에 대한 반복 질문 인지

<details>
<summary>정답 예시</summary>

```java
@SystemMessage("""
    당신은 배송 추적 전문가입니다.
    사용자의 주문 번호와 이전 질문 내역을 기억하여,
    맥락에 맞는 답변을 제공합니다.
    """)
String trackOrder(@MemoryId String userId, String message);
```

**테스트**:

```
사용자: 주문번호 12345 어디쯤 왔나요?
AI: 주문번호 12345는 현재 배송 중입니다.

사용자: 언제 도착해요?
AI: 주문번호 12345는 내일 도착 예정입니다. (← 주문번호 기억!)
```

</details>

---

### 과제 2: 멀티턴 설문조사

**요구사항**:

- 단계별로 질문 (이름 → 나이 → 직업 → 관심사)
- 이전 답변을 종합하여 추천
- 답변 수정 가능

<details>
<summary>정답 예시</summary>

```java
@SystemMessage("""
    당신은 설문조사 진행자입니다.
    단계별로 질문하고 모든 답변을 기억합니다.
    1. 이름
    2. 나이
    3. 직업
    4. 관심사
    모든 정보를 수집한 후 개인화된 추천을 제공합니다.
    """)
String survey(@MemoryId String sessionId, String message);
```

</details>

---

### 과제 3: 디버깅 어시스턴트

**요구사항**:

- 코드와 에러 메시지 기억
- 시도한 해결 방법 추적
- 새로운 제안 시 이전 시도 고려

---

## 📊 메모리 vs 비메모리 비교

| 특징            | 메모리 없음 (Phase 1.1) | 메모리 있음 (Phase 1.2) |
| --------------- | ----------------------- | ----------------------- |
| **대화 이력**   | 매 요청마다 독립적      | 이전 대화 기억          |
| **사용자 구분** | 불가능                  | @MemoryId로 구분        |
| **적합한 용도** | 단순 Q&A, 번역, 요약    | 상담, 학습, 개인 비서   |
| **비용**        | 낮음 (짧은 프롬프트)    | 높음 (긴 프롬프트)      |
| **구현 복잡도** | 낮음                    | 중간                    |

---

## ⚠️ 주의사항

### 1. 메모리 관리

- 메모리가 너무 길면 **토큰 비용 증가**
- `maxMessages`를 적절히 설정
- 불필요한 대화는 정리

### 2. 프로덕션 고려사항

```java
// ❌ 나쁜 예: 무제한 메모리
MessageWindowChatMemory.builder()
    .maxMessages(Integer.MAX_VALUE)  // 비용 폭발!
    .build()

// ✅ 좋은 예: 적절한 제한
MessageWindowChatMemory.builder()
    .maxMessages(20)  // 적절한 크기
    .build()
```

### 3. 개인정보 보호

- 민감한 정보는 메모리에서 제거
- GDPR 등 규정 준수
- 사용자 메모리 삭제 기능 제공

---

## 🚀 다음 단계: Phase 1.3 - 스트리밍 응답

Phase 1.2를 완료했다면 다음으로 배울 내용:

### Phase 1.3 주요 내용:

1. **StreamingChatLanguageModel** - 실시간 응답
2. **TokenStream** - 토큰 단위 스트리밍
3. **Server-Sent Events (SSE)** - 브라우저 연동
4. **Reactive Programming** - Flux/Mono

**예시**:

```
사용자: 스프링부트에 대해 설명해줘
AI: 스프링... 부트는... 자바... 프레임워크... (실시간 생성)
```

---

## 📚 참고 자료

- [LangChain4j Memory Documentation](https://docs.langchain4j.dev/tutorials/chat-memory)
- [Spring Session](https://spring.io/projects/spring-session) - 분산 세션 관리

---

**구현 완료 날짜**: 2025-10-17  
**다음 학습**: Phase 1.3 - 스트리밍 응답  
**예상 소요 시간**: 30분
