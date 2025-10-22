# LangChain4j Phase 1.3: 스트리밍 응답 ✅

## 📚 학습 목표

- `StreamingChatLanguageModel`: 스트리밍 모델 사용
- `TokenStream`: 토큰 단위 실시간 응답
- **Server-Sent Events (SSE)**: 브라우저와 실시간 통신
- **Reactor Flux**: 리액티브 스트림 처리

---

## 🎯 구현 완료 내용

### 1. StreamingAssistant 인터페이스

**파일**: `src/main/java/langhchainback/langchain/AI/StreamingAssistant.java`

총 **7가지 실전 예제** 구현:

1. **기본 스트리밍 채팅** - ChatGPT 스타일
2. **블로그 포스트 생성** - 긴 형식 콘텐츠
3. **코드 생성** - 실시간 코드 작성
4. **스토리 생성** - 창작 콘텐츠
5. **문서 분석** - 상세한 분석
6. **강의 자료 생성** - 교육 콘텐츠
7. **긴 텍스트 번역** - 대용량 번역

### 2. LangChainConfig 설정

**파일**: `src/main/java/langhchainback/langchain/Config/LangChainConfig.java`

```java
@Bean
public StreamingAssistant streamingAssistant() {
    GoogleAiGeminiStreamingChatModel streamingModel =
        GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName("gemini-2.5-flash")
            .temperature(0.7)
            .build();

    return AiServices.create(StreamingAssistant.class, streamingModel);
}
```

### 3. StreamingController

**파일**: `src/main/java/langhchainback/langchain/AI/StreamingController.java`

- SSE (Server-Sent Events) 엔드포인트
- Reactor Flux를 사용한 리액티브 스트림
- TokenStream → Flux 변환

### 4. build.gradle 의존성 추가

```gradle
implementation 'org.springframework.boot:spring-boot-starter-webflux'
```

---

## 🔑 핵심 개념

### 1. 스트리밍 vs 일반 응답

**일반 응답 (Phase 1.1-1.2)**:

```
사용자: 스프링부트에 대해 설명해줘
[3초 대기...]
AI: 스프링부트는 자바 기반의 프레임워크입니다...
```

**스트리밍 응답 (Phase 1.3)**:

```
사용자: 스프링부트에 대해 설명해줘
AI: 스프링
AI: 부트는
AI: 자바
AI: 기반의
AI: 프레임워크입니다
AI: ...
```

**장점**:

- ✅ 사용자 경험 향상 (즉각적인 피드백)
- ✅ 긴 응답에 유용
- ✅ ChatGPT와 동일한 경험
- ✅ 응답 취소 가능

---

### 2. TokenStream

```java
public interface StreamingAssistant {
    TokenStream chat(String message);
}
```

**TokenStream API**:

```java
tokenStream
    .onNext(token -> {
        // 각 토큰이 생성될 때마다 호출
        System.out.print(token);
    })
    .onComplete(response -> {
        // 스트리밍 완료
        System.out.println("\n완료!");
    })
    .onError(error -> {
        // 에러 발생
        System.err.println("오류: " + error.getMessage());
    })
    .start();  // 스트리밍 시작!
```

---

### 3. Server-Sent Events (SSE)

**HTTP 스트리밍 프로토콜**:

- 서버 → 클라이언트 단방향 통신
- `text/event-stream` Content-Type
- 자동 재연결
- 간단한 구현

**Spring Boot Controller**:

```java
@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> stream(@RequestParam String message) {
    return createFluxFromTokenStream(assistant.chat(message));
}
```

---

### 4. Reactor Flux

**Reactive Streams**:

```java
Flux<String> flux = Flux.create(sink -> {
    // 데이터 발행
    sink.next("Hello");
    sink.next("World");
    sink.complete();
});

// 구독
flux.subscribe(
    data -> System.out.println(data),
    error -> System.err.println(error),
    () -> System.out.println("완료")
);
```

---

## 🧪 테스트 방법

### 준비 사항

1. **의존성 설치 (WebFlux 추가됨)**

```bash
cd langchain
./gradlew clean build
```

2. **애플리케이션 실행**

```bash
./gradlew bootRun
```

3. **API 베이스 URL**: `http://localhost:8080`

---

## 📡 API 테스트 예제

### 1️⃣ curl로 스트리밍 테스트

**중요**: `-N` 또는 `--no-buffer` 옵션 필수!

```bash
curl -N "http://localhost:8080/ai/stream/chat?message=스프링부트에%20대해%20자세히%20설명해줘"
```

**출력**:

```
스프링
부트는
자바
기반의
프레임워크로
...
(실시간으로 토큰이 출력됨)
```

---

### 2️⃣ 블로그 포스트 생성

```bash
curl -N "http://localhost:8080/ai/stream/blog?topic=LangChain4j%20시작하기"
```

**예상 출력** (실시간):

```
#
 LangChain4j
 시작하기

##
 소개

LangChain4j는
자바
개발자를
위한
...
(긴 블로그 포스트가 실시간으로 생성됨)
```

---

### 3️⃣ 코드 생성 (POST)

```bash
curl -N -X POST http://localhost:8080/ai/stream/code \
  -H "Content-Type: application/json" \
  -d '{
    "language": "Java",
    "description": "사용자 인증을 위한 JWT 토큰 생성기"
  }'
```

**출력**:

```java
import
 io.jsonwebtoken
.Jwts
;
import
 io.jsonwebtoken
.SignatureAlgorithm
;

public
 class
 JwtTokenGenerator
 {
...
(코드가 실시간으로 생성됨)
```

---

### 4️⃣ 스토리 생성

```bash
curl -N "http://localhost:8080/ai/stream/story?genre=SF&topic=시간여행"
```

**출력**:

```
2157년
,
지구의
마지막
시간
여행자
김민준은
...
(약 1000단어의 SF 스토리가 실시간으로 생성됨)
```

---

### 5️⃣ 문서 분석

```bash
curl -N -X POST http://localhost:8080/ai/stream/analyze \
  -H "Content-Type: application/json" \
  -d '{
    "text": "인공지능 기술의 발전으로 우리 삶이 크게 변화하고 있습니다. 특히 자연어 처리 분야에서는 놀라운 성과를 보이고 있으며..."
  }'
```

**출력**:

```
##
 주요
 내용
 요약

이
 텍스트는
 AI
 기술
,
 특히
 자연어
 처리
의
 발전에
 대해
...
(상세한 분석이 실시간으로 생성됨)
```

---

### 6️⃣ 강의 자료 생성

```bash
curl -N "http://localhost:8080/ai/stream/lecture?topic=Java%20스트림%20API&audience=초급%20개발자"
```

**출력**:

```
#
 Java
 스트림
 API
 강의

##
 학습
 목표
-
 스트림의
 개념
 이해
-
 중간
 연산과
 최종
 연산
 구분
...
(강의 자료가 실시간으로 생성됨)
```

---

### 7️⃣ 긴 텍스트 번역

```bash
curl -N -X POST http://localhost:8080/ai/stream/translate \
  -H "Content-Type: application/json" \
  -d '{
    "text": "긴 한국어 텍스트...",
    "targetLang": "영어"
  }'
```

---

## 🌐 브라우저에서 테스트

### HTML + JavaScript 예제

```html
<!DOCTYPE html>
<html>
  <head>
    <title>LangChain4j 스트리밍 테스트</title>
    <style>
      body {
        font-family: Arial, sans-serif;
        max-width: 800px;
        margin: 50px auto;
        padding: 20px;
      }
      #output {
        border: 1px solid #ccc;
        padding: 15px;
        min-height: 200px;
        background: #f5f5f5;
        white-space: pre-wrap;
        font-family: monospace;
      }
      input {
        width: 70%;
        padding: 10px;
        font-size: 16px;
      }
      button {
        padding: 10px 20px;
        font-size: 16px;
        cursor: pointer;
      }
      .loading {
        color: #666;
        font-style: italic;
      }
    </style>
  </head>
  <body>
    <h1>🤖 LangChain4j 스트리밍 채팅</h1>

    <div>
      <input type="text" id="message" placeholder="질문을 입력하세요..." />
      <button onclick="sendMessage()">전송</button>
      <button onclick="clearOutput()">초기화</button>
    </div>

    <h3>응답:</h3>
    <div id="output"></div>

    <script>
      let eventSource = null;

      function sendMessage() {
        const message = document.getElementById("message").value;
        if (!message.trim()) {
          alert("메시지를 입력하세요!");
          return;
        }

        const output = document.getElementById("output");
        output.innerHTML = '<span class="loading">AI가 생각 중...</span>\n\n';

        // 이전 연결 종료
        if (eventSource) {
          eventSource.close();
        }

        // SSE 연결
        const url = `http://localhost:8080/ai/stream/chat?message=${encodeURIComponent(
          message
        )}`;
        eventSource = new EventSource(url);

        eventSource.onmessage = function (event) {
          // 토큰 수신
          const token = event.data;
          if (output.querySelector(".loading")) {
            output.innerHTML = ""; // loading 메시지 제거
          }
          output.textContent += token;

          // 자동 스크롤
          output.scrollTop = output.scrollHeight;
        };

        eventSource.onerror = function (error) {
          console.error("SSE 에러:", error);
          eventSource.close();

          if (output.textContent.trim() === "") {
            output.innerHTML =
              '<span style="color: red;">오류가 발생했습니다.</span>';
          } else {
            output.textContent += "\n\n✅ 완료";
          }
        };
      }

      function clearOutput() {
        document.getElementById("output").innerHTML = "";
        document.getElementById("message").value = "";
        if (eventSource) {
          eventSource.close();
        }
      }

      // Enter 키로 전송
      document
        .getElementById("message")
        .addEventListener("keypress", function (e) {
          if (e.key === "Enter") {
            sendMessage();
          }
        });
    </script>
  </body>
</html>
```

**사용 방법**:

1. 위 HTML을 `test-streaming.html`로 저장
2. 브라우저에서 파일 열기
3. 질문 입력 후 전송 버튼 클릭
4. 실시간으로 답변이 생성되는 것을 확인!

---

## 🎨 Vue.js와 통합 (Nuxt 프론트엔드용)

### Composable 작성

```typescript
// composables/useStreamingChat.ts
export const useStreamingChat = () => {
  const response = ref("");
  const isStreaming = ref(false);
  let eventSource: EventSource | null = null;

  const sendMessage = async (message: string) => {
    response.value = "";
    isStreaming.value = true;

    if (eventSource) {
      eventSource.close();
    }

    const url = `http://localhost:8080/ai/stream/chat?message=${encodeURIComponent(
      message
    )}`;
    eventSource = new EventSource(url);

    eventSource.onmessage = (event) => {
      response.value += event.data;
    };

    eventSource.onerror = () => {
      isStreaming.value = false;
      eventSource?.close();
    };
  };

  const stop = () => {
    eventSource?.close();
    isStreaming.value = false;
  };

  return {
    response,
    isStreaming,
    sendMessage,
    stop,
  };
};
```

### Vue 컴포넌트

```vue
<template>
  <div class="chat-container">
    <div class="messages">
      <div class="message ai">
        {{ response || "AI 응답을 기다리는 중..." }}
      </div>
    </div>

    <div class="input-area">
      <input
        v-model="message"
        @keyup.enter="send"
        placeholder="메시지를 입력하세요..."
        :disabled="isStreaming"
      />
      <button @click="send" :disabled="isStreaming">
        {{ isStreaming ? "생성 중..." : "전송" }}
      </button>
      <button v-if="isStreaming" @click="stop">중지</button>
    </div>
  </div>
</template>

<script setup lang="ts">
const { response, isStreaming, sendMessage, stop } = useStreamingChat();
const message = ref("");

const send = () => {
  if (message.value.trim()) {
    sendMessage(message.value);
    message.value = "";
  }
};
</script>
```

---

## 💡 고급 활용 팁

### 1. 진행률 표시

```java
@GetMapping(value = "/stream-with-progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> chatWithProgress(@RequestParam String message) {
    AtomicInteger tokenCount = new AtomicInteger(0);

    return createFluxFromTokenStream(assistant.chat(message))
        .map(token -> {
            int count = tokenCount.incrementAndGet();
            return ServerSentEvent.<String>builder()
                .id(String.valueOf(count))
                .event("message")
                .data(token)
                .build();
        });
}
```

---

### 2. 스트리밍 취소

```javascript
// 브라우저에서
const controller = new AbortController();

fetch("/ai/stream/chat?message=test", {
  signal: controller.signal,
});

// 5초 후 취소
setTimeout(() => {
  controller.abort(); // 스트리밍 중단!
}, 5000);
```

---

### 3. 토큰 필터링 (욕설/민감 정보 제거)

```java
private Flux<String> createFluxFromTokenStream(TokenStream tokenStream) {
    return Flux.create(sink -> {
        tokenStream
            .onNext(token -> {
                // 토큰 필터링
                String filtered = filterToken(token);
                if (!filtered.isEmpty()) {
                    sink.next(filtered);
                }
            })
            .onComplete(response -> sink.complete())
            .onError(sink::error)
            .start();
    });
}

private String filterToken(String token) {
    // 욕설 필터링
    if (containsProfanity(token)) {
        return "***";
    }
    // 이메일/전화번호 마스킹
    token = maskSensitiveInfo(token);
    return token;
}
```

---

### 4. 메모리 + 스트리밍 조합

```java
// 대화 메모리를 가진 스트리밍 Assistant
public interface MemoryStreamingAssistant {
    TokenStream chat(@MemoryId String userId, String message);
}

@Bean
public MemoryStreamingAssistant memoryStreamingAssistant() {
    return AiServices.builder(MemoryStreamingAssistant.class)
        .streamingChatLanguageModel(streamingModel)
        .chatMemoryProvider(memoryId ->
            MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .build())
        .build();
}
```

---

## 📊 성능 비교

| 항목               | 일반 응답            | 스트리밍 응답   |
| ------------------ | -------------------- | --------------- |
| **초기 응답 시간** | 3-5초 (전체 생성 후) | 0.5초 (첫 토큰) |
| **사용자 경험**    | 대기 시간 체감       | 즉각적 피드백   |
| **취소 가능**      | ❌                   | ✅              |
| **네트워크 효율**  | 한 번에 전송         | 점진적 전송     |
| **적합한 용도**    | 짧은 답변, API       | 긴 답변, UI     |
| **구현 복잡도**    | 낮음                 | 중간            |

---

## ⚠️ 주의사항

### 1. CORS 설정 (프론트엔드 연동 시)

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/ai/stream/**")
                .allowedOrigins("http://localhost:3000")
                .allowedMethods("GET", "POST")
                .allowCredentials(true);
    }
}
```

---

### 2. 타임아웃 설정

```java
GoogleAiGeminiStreamingChatModel.builder()
    .timeout(Duration.ofSeconds(60))  // 타임아웃 설정
    .build()
```

---

### 3. 에러 처리

```java
tokenStream
    .onError(error -> {
        log.error("Streaming error", error);
        sink.tryEmitNext("죄송합니다. 오류가 발생했습니다.");
        sink.tryEmitComplete();
    })
```

---

### 4. 메모리 누수 방지

```java
@PreDestroy
public void cleanup() {
    // 앱 종료 시 모든 스트리밍 연결 정리
    if (eventSource != null) {
        eventSource.close();
    }
}
```

---

## 🎓 실전 연습 과제

### 과제 1: 진행률이 있는 파일 분석

**요구사항**:

- 파일 업로드 → 분석
- 분석 진행률 표시 (0-100%)
- 스트리밍으로 결과 출력

---

### 과제 2: 멀티플 스트림 비교

**요구사항**:

- 같은 질문을 여러 모델에 동시 전송
- 각 모델의 응답을 스트리밍으로 병렬 표시
- 응답 속도 비교

---

### 과제 3: 대화형 코드 리뷰

**요구사항**:

- 코드 입력 → 스트리밍 리뷰
- 각 문제점마다 수정 제안
- 사용자가 수정 수락/거부 가능

---

## 🚀 다음 단계: Phase 2 - RAG (Retrieval Augmented Generation)

Phase 1 (1.1 + 1.2 + 1.3)을 완료했다면:

### Phase 2 주요 내용:

1. **문서 로딩** - PDF, TXT, Markdown
2. **문서 분할** - Chunking 전략
3. **임베딩** - 벡터 변환
4. **유사도 검색** - 관련 문서 찾기
5. **RAG 파이프라인** - 문서 기반 Q&A

**예시**:

```
회사 문서 업로드 → 임베딩 생성 → 저장
사용자: "휴가 정책이 어떻게 되나요?"
→ 관련 문서 검색
→ 문서 + 질문을 AI에 전달
→ "연차는 입사 1년 후 15일이 부여됩니다..."
```

---

## 📚 참고 자료

- [LangChain4j Streaming Documentation](https://docs.langchain4j.dev/tutorials/streaming)
- [Server-Sent Events (MDN)](https://developer.mozilla.org/en-US/docs/Web/API/Server-sent_events)
- [Project Reactor](https://projectreactor.io/)
- [Spring WebFlux](https://docs.spring.io/spring-framework/reference/web/webflux.html)

---

**구현 완료 날짜**: 2025-10-17  
**다음 학습**: Phase 2 - RAG  
**예상 소요 시간**: 2시간

**Phase 1 전체 완료! 🎉**
