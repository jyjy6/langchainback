# LangChain4j Phase 1: 시스템 메시지 & 프롬프트 템플릿 ✅

## 📚 학습 목표

- `@SystemMessage`: AI의 역할과 성격 정의
- `@UserMessage`: 프롬프트 템플릿 작성
- 변수 바인딩: `{{variable}}` 문법 활용
- 다양한 실전 예제 구현

---

## 🎯 구현 완료 내용

### 1. Assistant 인터페이스 확장

**파일**: `src/main/java/langhchainback/langchain/AI/Assistant.java`

총 **7가지 실전 예제** 구현:

1. **기본 채팅** - 시스템 메시지 없음
2. **친절한 AI 어시스턴트** - 한국어 답변 전문
3. **전문가 역할 기반 설명** - 동적 역할 할당
4. **코드 리뷰 어시스턴트** - 시니어 개발자 관점
5. **번역 어시스턴트** - 다국어 번역
6. **텍스트 요약** - 지정된 단어 수로 요약
7. **SQL 생성기** - 자연어 → SQL 쿼리
8. **감정 분석** - 텍스트 감정 분류

### 2. REST API 엔드포인트 구현

**파일**: `src/main/java/langhchainback/langchain/AI/AssistantController.java`

모든 Assistant 메서드에 대응하는 API 엔드포인트 제공

---

## 🧪 테스트 방법

### 준비 사항

1. **Spring Boot 애플리케이션 실행**

```bash
cd langchain
./gradlew bootRun
```

2. **API 베이스 URL**: `http://localhost:8080`

---

## 📡 API 테스트 예제

### 1️⃣ 기본 채팅

```bash
curl "http://localhost:8080/ai/chat?message=안녕하세요"
```

**특징**: 시스템 메시지 없이 기본 Gemini 응답

---

### 2️⃣ 시스템 메시지 적용된 채팅

```bash
curl "http://localhost:8080/ai/chat-with-system?message=스프링부트에%20대해%20알려줘"
```

**차이점**:

- 기본 채팅과 달리 **친절하고 전문적인 톤** 적용
- 항상 한국어로 답변
- 간결하고 이해하기 쉬운 설명

---

### 3️⃣ 전문가 역할 기반 설명

```bash
curl "http://localhost:8080/ai/explain?role=의사&topic=운동의%20효과"
```

**프롬프트 템플릿**:

```
당신은 {{role}} 전문가입니다.
다음 주제에 대해 {{role}}의 관점에서 설명해주세요:
주제: {{topic}}
```

**다른 예제**:

```bash
# 경제학자 관점
curl "http://localhost:8080/ai/explain?role=경제학자&topic=인플레이션"

# 역사학자 관점
curl "http://localhost:8080/ai/explain?role=역사학자&topic=산업혁명"
```

---

### 4️⃣ 코드 리뷰

```bash
curl -X POST http://localhost:8080/ai/code-review \
  -H "Content-Type: application/json" \
  -d '{
    "language": "Java",
    "code": "public void processData(List data) {\n  for (int i = 0; i < data.size(); i++) {\n    System.out.println(data.get(i));\n  }\n}"
  }'
```

**AI가 검토하는 관점**:

1. 코드 품질
2. 성능 최적화
3. 보안 취약점
4. 베스트 프랙티스

**예상 피드백**:

- `System.out.println` 대신 로깅 사용
- Enhanced for-loop 사용 권장
- 제네릭 타입 명시
- Null 체크 필요

---

### 5️⃣ 번역

```bash
# 한국어 → 영어
curl "http://localhost:8080/ai/translate?from=한국어&to=영어&text=안녕하세요.%20만나서%20반갑습니다."

# 영어 → 일본어
curl "http://localhost:8080/ai/translate?from=English&to=Japanese&text=Hello,%20how%20are%20you?"
```

---

### 6️⃣ 텍스트 요약

```bash
curl -X POST http://localhost:8080/ai/summarize \
  -H "Content-Type: application/json" \
  -d '{
    "text": "스프링 부트는 스프링 프레임워크를 기반으로 하는 오픈 소스 자바 기반 프레임워크입니다. 스프링 부트는 마이크로서비스 기반의 독립 실행형 프로덕션 수준의 스프링 기반 애플리케이션을 쉽게 만들 수 있도록 설계되었습니다. 복잡한 XML 설정 없이 자동 구성 기능을 제공하며, 내장 서버를 포함하고 있어 별도의 서버 설치 없이 바로 실행할 수 있습니다.",
    "maxWords": 30
  }'
```

**프롬프트**:

```
다음 텍스트를 {{maxWords}}단어 이내로 요약해주세요:
{{text}}
```

---

### 7️⃣ SQL 쿼리 생성

```bash
# 예제 1: 기본 조회
curl "http://localhost:8080/ai/generate-sql?table=users&request=30세%20이상%20사용자%20조회"

# 예제 2: 집계 쿼리
curl "http://localhost:8080/ai/generate-sql?table=orders&request=월별%20매출%20합계를%20구하고%20내림차순%20정렬"

# 예제 3: JOIN 쿼리
curl "http://localhost:8080/ai/generate-sql?table=users&request=주문%20내역이%20있는%20사용자의%20이름과%20주문%20개수"
```

**예상 출력**:

```sql
-- 30세 이상 사용자 조회
SELECT * FROM users
WHERE age >= 30;
```

---

### 8️⃣ 감정 분석

```bash
# 긍정적 텍스트
curl "http://localhost:8080/ai/sentiment?text=오늘%20정말%20기분이%20좋아요!"

# 부정적 텍스트
curl "http://localhost:8080/ai/sentiment?text=너무%20화가%20나고%20실망스러워요"

# 중립적 텍스트
curl "http://localhost:8080/ai/sentiment?text=오늘은%20평범한%20하루였습니다"
```

**출력 형식**:

```
감정: [긍정/부정/중립]
신뢰도: [0-100]%
이유: [간단한 설명]
```

---

## 🔑 핵심 개념 정리

### 1. @SystemMessage

```java
@SystemMessage("당신은 친절한 AI 어시스턴트입니다.")
String chat(String message);
```

**역할**:

- AI의 **성격, 역할, 행동 방식** 정의
- 모든 대화에서 **일관된 톤** 유지
- 전체 대화의 **맥락** 설정

**사용 예시**:

- "당신은 시니어 개발자입니다"
- "당신은 전문 번역가입니다"
- "당신은 친절한 고객 서비스 담당자입니다"

---

### 2. @UserMessage

```java
@UserMessage("다음 주제에 대해 설명해주세요: {{topic}}")
String explain(String topic);
```

**역할**:

- **프롬프트 템플릿** 정의
- 변수 바인딩을 통한 **동적 프롬프트** 생성
- 일관된 질문 형식 유지

**변수 바인딩**:

- `{{variableName}}` 형식 사용
- 메서드 파라미터 이름과 자동 매칭
- 여러 개의 변수 사용 가능

---

### 3. 조합 사용

```java
@SystemMessage("당신은 {{role}} 전문가입니다.")
@UserMessage("{{topic}}에 대해 설명해주세요.")
String explainAsExpert(String role, String topic);
```

**장점**:

- 시스템 메시지도 동적으로 변경 가능
- 하나의 메서드로 다양한 역할 처리
- 코드 재사용성 극대화

---

## 💡 프롬프트 엔지니어링 팁

### ✅ 좋은 프롬프트

```java
@SystemMessage("""
    당신은 시니어 개발자입니다.
    코드 리뷰를 수행하며, 다음 관점에서 피드백을 제공합니다:
    1. 코드 품질
    2. 성능 최적화
    3. 보안 취약점
    4. 베스트 프랙티스
    """)
```

**특징**:

- 명확한 역할 정의
- 구체적인 행동 지침
- 구조화된 출력 형식

---

### ❌ 나쁜 프롬프트

```java
@SystemMessage("당신은 AI입니다.")
@UserMessage("이것 좀 봐주세요.")
```

**문제점**:

- 역할이 모호함
- 지시사항이 불명확
- 기대하는 출력 형식 없음

---

## 🎓 실전 연습 과제

### 과제 1: 이력서 검토 어시스턴트

다음 조건의 Assistant 메서드를 작성하세요:

**요구사항**:

- 역할: HR 전문가
- 입력: 이력서 텍스트
- 출력: 개선 사항 (강점, 약점, 추천사항)

<details>
<summary>정답 예시</summary>

```java
@SystemMessage("""
    당신은 10년 경력의 HR 전문가입니다.
    이력서를 검토하고 다음 관점에서 피드백을 제공합니다:
    1. 강점
    2. 개선이 필요한 부분
    3. 구체적인 추천사항
    """)
@UserMessage("""
    다음 이력서를 검토해주세요:

    {{resume}}

    피드백을 다음 형식으로 작성해주세요:
    ## 강점
    - ...

    ## 개선 필요
    - ...

    ## 추천사항
    - ...
    """)
String reviewResume(String resume);
```

</details>

---

### 과제 2: 레시피 추천 시스템

**요구사항**:

- 역할: 요리사
- 입력: 재료 목록, 요리 시간, 난이도
- 출력: 레시피 (재료, 조리법, 팁)

<details>
<summary>정답 예시</summary>

```java
@SystemMessage("당신은 10년 경력의 전문 요리사입니다.")
@UserMessage("""
    다음 조건으로 레시피를 추천해주세요:
    재료: {{ingredients}}
    시간: {{time}}분 이내
    난이도: {{difficulty}}

    다음 형식으로 답변해주세요:
    ## 요리명
    [요리 이름]

    ## 재료
    - ...

    ## 조리법
    1. ...

    ## 꿀팁
    - ...
    """)
String recommendRecipe(String ingredients, int time, String difficulty);
```

</details>

---

## 📊 학습 체크리스트

- [ ] `@SystemMessage`의 역할 이해
- [ ] `@UserMessage`로 프롬프트 템플릿 작성
- [ ] `{{변수}}` 바인딩 문법 이해
- [ ] 시스템 메시지와 유저 메시지 조합 사용
- [ ] 8가지 예제 모두 테스트 완료
- [ ] 자신만의 Assistant 메서드 작성

---

## 🚀 다음 단계: Phase 2 - 메모리 (대화 컨텍스트)

Phase 1을 완료했다면 다음으로 배울 내용:

### Phase 2 주요 내용:

1. **ChatMemory** - 대화 이력 저장
2. **MessageWindowChatMemory** - 최근 N개 메시지 유지
3. **TokenWindowChatMemory** - 토큰 기반 메모리 관리
4. **대화형 챗봇** - 컨텍스트를 기억하는 AI

**예시**:

```
사용자: 내 이름은 철수야
AI: 안녕하세요, 철수님!

사용자: 내 이름이 뭐였지?
AI: 철수님이라고 하셨습니다! (← 이전 대화 기억!)
```

---

## 📚 참고 자료

- [LangChain4j 공식 문서](https://docs.langchain4j.dev/)
- [Prompt Engineering Guide](https://www.promptingguide.ai/)
- [Google Gemini API](https://ai.google.dev/docs)

---

**구현 완료 날짜**: 2025-10-17  
**다음 학습**: Phase 2 - 메모리 추가  
**예상 소요 시간**: 20분
