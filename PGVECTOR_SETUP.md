# PostgreSQL pgvector 설정 가이드

## 🎯 개요

LangChain4j RAG 시스템에서 PostgreSQL pgvector를 사용하기 위한 완전한 설정 가이드입니다.

## 📋 사전 준비

### 1. Docker Compose 실행

```bash
# Docker Compose로 PostgreSQL 시작
docker-compose -f docker-compose.dev.yml up -d

# 컨테이너 확인
docker ps | grep pgvector
```

예상 출력:

```
pgvector-db   ankane/pgvector   Up   0.0.0.0:5432->5432/tcp
```

## 🔧 pgvector Extension 활성화

### 방법 1: DBeaver 사용 (추천)

1. **PostgreSQL 연결 생성**

   - Host: `localhost`
   - Port: `5432`
   - Database: `vector_db`
   - Username: `postgres`
   - Password: `1234!@`

2. **SQL 편집기에서 실행**

```sql
-- pgvector extension 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- 확인
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
```

예상 결과:

```
extname | extversion
--------+-----------
vector  | 0.5.1
```

### 방법 2: psql 터미널 사용

```bash
# Docker 컨테이너 내부 접속
docker exec -it pgvector-db psql -U postgres -d vector_db

# SQL 실행
CREATE EXTENSION IF NOT EXISTS vector;

# 확인
\dx vector

# 종료
\q
```

### 방법 3: 초기화 SQL 스크립트 (자동화)

`docker-compose.dev.yml` 수정:

```yaml
services:
  pgvector:
    image: ankane/pgvector
    container_name: pgvector-db
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: "1234!@"
      POSTGRES_DB: vector_db
      POSTGRES_INITDB_ARGS: "-c shared_preload_libraries=vector"
    ports:
      - "5432:5432"
    volumes:
      - C:/langchainproject/pgdata:/var/lib/postgresql/data
      - ./init-pgvector.sql:/docker-entrypoint-initdb.d/init.sql # 추가
    restart: unless-stopped
```

`init-pgvector.sql` 생성:

```sql
-- pgvector extension 자동 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- 확인용 메시지
DO $$
BEGIN
    RAISE NOTICE 'pgvector extension activated successfully!';
END $$;
```

## ✅ 설정 확인

### 1. Extension 확인

```sql
-- Extension 버전 확인
SELECT extname, extversion
FROM pg_extension
WHERE extname = 'vector';

-- Vector 데이터 타입 확인
SELECT typname
FROM pg_type
WHERE typname = 'vector';
```

### 2. 테이블 확인 (Spring Boot 실행 후)

```sql
-- LangChain4j가 자동 생성한 embeddings 테이블 확인
\dt

-- 테이블 구조 확인
\d embeddings

-- 또는
SELECT column_name, data_type
FROM information_schema.columns
WHERE table_name = 'embeddings';
```

예상 테이블 구조:

```
Column      | Type
------------+-----------------
id          | uuid (primary key)
embedding   | vector(768)
text        | text
metadata    | jsonb
```

### 3. 샘플 벡터 삽입 테스트

```sql
-- 테스트 벡터 삽입 (768차원 벡터)
INSERT INTO embeddings (id, embedding, text)
VALUES (
    gen_random_uuid(),
    array_fill(0.1, ARRAY[768])::vector(768),
    'Test document'
);

-- 확인
SELECT id, text, embedding <-> array_fill(0.2, ARRAY[768])::vector(768) AS distance
FROM embeddings
ORDER BY distance
LIMIT 5;
```

## 🚀 Spring Boot 애플리케이션 실행

### 1. Gradle 빌드

```bash
cd langchain
./gradlew clean build
```

### 2. 애플리케이션 시작

```bash
# 개발 프로파일로 실행
./gradlew bootRun --args='--spring.profiles.active=dev'

# 또는 IDE에서 LangchainApplication.java 실행
```

### 3. 로그 확인

성공적으로 연결되면 다음 로그가 표시됩니다:

```
🐘 PostgreSQL pgvector DataSource 초기화 - jdbc:postgresql://localhost:5432/vector_db
🔗 PostgreSQL 연결 정보 - host: localhost, port: 5432, database: vector_db
✅ PostgreSQL pgvector 연결 성공
💾 Embedding Store 초기화 - PostgreSQL pgvector (프로덕션용)
📊 테이블: embeddings, 차원: 768
```

## ⚠️ 일반적인 문제 해결

### 문제 1: Extension을 찾을 수 없음

```
ERROR: extension "vector" is not available
```

**해결책**: `ankane/pgvector` 이미지를 사용하는지 확인

```bash
# 현재 이미지 확인
docker inspect pgvector-db | grep Image

# 올바른 이미지로 재시작
docker-compose -f docker-compose.dev.yml down
docker-compose -f docker-compose.dev.yml up -d
```

### 문제 2: 권한 부족

```
ERROR: permission denied to create extension "vector"
```

**해결책**: superuser 권한으로 실행

```sql
-- postgres 사용자로 실행 (superuser)
CREATE EXTENSION IF NOT EXISTS vector;
```

### 문제 3: 연결 실패

```
Connection refused: localhost:5432
```

**해결책**:

```bash
# 컨테이너 상태 확인
docker ps -a | grep pgvector

# 로그 확인
docker logs pgvector-db

# 재시작
docker restart pgvector-db
```

### 문제 4: 차원 불일치

```
ERROR: vector dimension mismatch
```

**해결책**: Embedding Model 차원과 DB 테이블 차원이 일치하는지 확인

- **Google AI text-embedding-004**: 768 차원
- **AllMiniLmL6V2**: 384 차원

`application-dev.properties` 확인:

```properties
pgvector.dimension=768  # Embedding Model 차원과 일치해야 함
```

## 📊 성능 최적화 (선택사항)

### 1. 인덱스 생성 (대용량 데이터용)

```sql
-- IVFFlat 인덱스 (빠른 근사 검색)
CREATE INDEX ON embeddings
USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

-- 또는 HNSW 인덱스 (더 정확한 검색)
CREATE INDEX ON embeddings
USING hnsw (embedding vector_cosine_ops);
```

### 2. PostgreSQL 설정 최적화

`postgresql.conf` 수정:

```conf
shared_buffers = 256MB          # 벡터 캐싱
effective_cache_size = 1GB      # 쿼리 최적화
maintenance_work_mem = 128MB    # 인덱스 생성
max_connections = 100
```

## 🧪 테스트 시나리오

### 1. API 테스트

```bash
# 문서 업로드 (Postman/cURL)
curl -X POST http://localhost:8080/api/rag/ingest \
  -F "file=@document.pdf" \
  -F "documentId=doc123"

# 질문하기
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "퇴직금은 어떻게 계산하나요?"}'
```

### 2. 데이터베이스 직접 확인

```sql
-- 저장된 문서 조각 개수
SELECT COUNT(*) FROM embeddings;

-- 최근 추가된 문서
SELECT id, text, metadata
FROM embeddings
ORDER BY id DESC
LIMIT 10;

-- 유사도 검색 테스트
WITH query AS (
    SELECT embedding FROM embeddings LIMIT 1
)
SELECT
    e.text,
    e.embedding <-> q.embedding AS distance
FROM embeddings e, query q
ORDER BY distance
LIMIT 5;
```

## 📚 참고 자료

- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [PostgreSQL 공식 문서](https://www.postgresql.org/docs/)

## ✅ 체크리스트

- [ ] Docker Compose로 PostgreSQL 실행
- [ ] DBeaver/psql로 연결 확인
- [ ] `CREATE EXTENSION vector` 실행
- [ ] Extension 활성화 확인
- [ ] Spring Boot 애플리케이션 실행
- [ ] 로그에서 pgvector 연결 성공 확인
- [ ] 테이블 자동 생성 확인
- [ ] API 테스트 (문서 업로드 & 질문)

---

**💡 Tip**: 개발 중에는 DBeaver를 열어두고 실시간으로 `embeddings` 테이블을 모니터링하면 디버깅에 도움이 됩니다!
