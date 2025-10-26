# 문서 메타데이터 PostgreSQL 영구 저장

## 📚 개요

기존에는 메모리(`ConcurrentHashMap`)에만 저장되어 서버 재시작 시 문서 목록이 사라지는 문제가 있었습니다.
이제 **PostgreSQL에 문서 메타데이터를 영구 저장**하여 프로덕션 환경에 적합한 구조로 개선했습니다.

## 🔄 변경 사항

### Before (메모리 저장)

```java
// ❌ 메모리에만 저장 → 서버 재시작 시 소실
private final Map<String, DocumentInfo> documentRegistry = new ConcurrentHashMap<>();

public DocumentInfo ingestDocument(MultipartFile file, String documentId) {
    // ... 벡터 저장 ...

    // 메모리에 저장
    documentRegistry.put(documentId, docInfo);
    return docInfo;
}

public List<DocumentInfo> getUploadedDocuments() {
    // 메모리에서 조회
    return new ArrayList<>(documentRegistry.values());
}
```

### After (PostgreSQL 저장)

```java
// ✅ PostgreSQL에 영구 저장
private final DocumentMetadataRepository documentMetadataRepository;

public DocumentInfo ingestDocument(MultipartFile file, String documentId) {
    // ... 벡터 저장 ...

    // PostgreSQL에 저장
    DocumentMetadata metadata = DocumentMetadata.builder()
            .documentId(documentId)
            .fileName(fileName)
            .chunkCount(count)
            .fileSize(fileSize)
            .build();
    documentMetadataRepository.save(metadata);
    return metadata.toDocumentInfo();
}

public List<DocumentInfo> getUploadedDocuments() {
    // PostgreSQL에서 조회 (최신순)
    return documentMetadataRepository
            .findByIsActiveTrueOrderByUploadedAtDesc()
            .stream()
            .map(DocumentMetadata::toDocumentInfo)
            .toList();
}
```

## 📊 데이터베이스 스키마

### `document_metadata` 테이블

```sql
CREATE TABLE document_metadata (
    id BIGSERIAL PRIMARY KEY,
    document_id VARCHAR(255) NOT NULL UNIQUE,
    file_name VARCHAR(500) NOT NULL,
    chunk_count INTEGER NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(50),
    description VARCHAR(1000),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    uploaded_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,

    -- 인덱스
    INDEX idx_document_id (document_id),
    INDEX idx_uploaded_at (uploaded_at)
);
```

### 컬럼 설명

| 컬럼          | 타입          | 설명                      | 예시                  |
| ------------- | ------------- | ------------------------- | --------------------- |
| `id`          | BIGSERIAL     | 기본키 (자동 증가)        | 1, 2, 3...            |
| `document_id` | VARCHAR(255)  | 문서 고유 식별자 (UNIQUE) | `회사규정_2024`       |
| `file_name`   | VARCHAR(500)  | 원본 파일명               | `회사규정.pdf`        |
| `chunk_count` | INTEGER       | 생성된 청크 개수          | 42                    |
| `file_size`   | BIGINT        | 파일 크기 (bytes)         | 1024000               |
| `file_type`   | VARCHAR(50)   | 파일 확장자               | `pdf`, `docx`         |
| `description` | VARCHAR(1000) | 문서 설명 (옵션)          | `2024년 인사규정`     |
| `is_active`   | BOOLEAN       | 활성화 여부 (논리 삭제)   | `true`, `false`       |
| `uploaded_at` | TIMESTAMP     | 업로드 일시 (자동)        | `2025-01-15 10:30:00` |
| `updated_at`  | TIMESTAMP     | 수정 일시 (자동)          | `2025-01-16 14:20:00` |

## 🏗️ 아키텍처 (일반 MVC 패턴)

### 1. Entity (DocumentMetadata.java)

```java
@Entity
@Table(name = "document_metadata")
public class DocumentMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false, unique = true)
    private String documentId;

    // ... 기타 필드 ...

    @CreatedDate  // 자동으로 생성 시간 기록
    private LocalDateTime uploadedAt;

    @LastModifiedDate  // 자동으로 수정 시간 기록
    private LocalDateTime updatedAt;
}
```

### 2. Repository (DocumentMetadataRepository.java)

```java
@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, Long> {

    // documentId로 조회
    Optional<DocumentMetadata> findByDocumentId(String documentId);

    // documentId 존재 여부 확인
    boolean existsByDocumentId(String documentId);

    // 활성화된 문서 목록 (최신순)
    List<DocumentMetadata> findByIsActiveTrueOrderByUploadedAtDesc();

    // 파일 타입별 조회
    List<DocumentMetadata> findByFileType(String fileType);

    // 전체 청크 개수 합계
    @Query("SELECT SUM(d.chunkCount) FROM DocumentMetadata d WHERE d.isActive = true")
    Long sumChunkCount();
}
```

### 3. Service (RagService.java)

```java
@Service
@RequiredArgsConstructor
public class RagService {
    private final DocumentMetadataRepository documentMetadataRepository;

    // 문서 업로드 시 저장
    public DocumentInfo ingestDocument(MultipartFile file, String documentId) {
        // ... 벡터 저장 로직 ...

        // PostgreSQL에 메타데이터 저장
        DocumentMetadata metadata = DocumentMetadata.builder()
                .documentId(documentId)
                .fileName(fileName)
                .chunkCount(count)
                .fileSize(fileSize)
                .build();

        DocumentMetadata saved = documentMetadataRepository.save(metadata);
        return saved.toDocumentInfo();
    }

    // 문서 목록 조회
    public List<DocumentInfo> getUploadedDocuments() {
        return documentMetadataRepository
                .findByIsActiveTrueOrderByUploadedAtDesc()
                .stream()
                .map(DocumentMetadata::toDocumentInfo)
                .toList();
    }

    // 문서 삭제 (논리 삭제)
    @Transactional
    public void deleteDocument(String documentId) {
        DocumentMetadata document = documentMetadataRepository
                .findByDocumentId(documentId)
                .orElseThrow();

        document.setIsActive(false);
        documentMetadataRepository.save(document);
    }
}
```

### 4. Controller (RagController.java)

```java
// Controller는 변경 없음 - Service만 Repository 사용하도록 변경
@GetMapping("/documents")
public ResponseEntity<DocumentListResponse> getDocuments() {
    List<RagService.DocumentInfo> documents = ragService.getUploadedDocuments();
    // ...
}
```

## 🔧 설정 (필수)

### 1. `LangchainApplication.java`

```java
@SpringBootApplication
@EnableJpaAuditing  // ⭐ 필수! @CreatedDate, @LastModifiedDate 작동
public class LangchainApplication {
    // ...
}
```

### 2. `application-dev.properties`

```properties
# PostgreSQL 설정 (기존)
spring.datasource.url=jdbc:postgresql://localhost:5432/langchain_dev
spring.datasource.username=postgres
spring.datasource.password=your_password

# JPA 설정
spring.jpa.hibernate.ddl-auto=update  # 개발 환경: 테이블 자동 생성
spring.jpa.show-sql=true
```

### 3. `application-prod.properties`

```properties
# 프로덕션 환경
spring.jpa.hibernate.ddl-auto=validate  # 프로덕션: 스키마 검증만
spring.jpa.show-sql=false
```

## 🚀 사용 방법

### 1. 문서 업로드

```bash
curl -X POST http://localhost:8080/rag/ingest \
  -F "file=@회사규정.pdf" \
  -F "documentId=회사규정_2024"
```

**PostgreSQL에 저장됨:**

```sql
INSERT INTO document_metadata (
    document_id, file_name, chunk_count, file_size,
    file_type, is_active, uploaded_at
) VALUES (
    '회사규정_2024', '회사규정.pdf', 42, 1024000,
    'pdf', true, '2025-01-15 10:30:00'
);
```

### 2. 문서 목록 조회

```bash
curl http://localhost:8080/rag/documents
```

**PostgreSQL 쿼리:**

```sql
SELECT * FROM document_metadata
WHERE is_active = true
ORDER BY uploaded_at DESC;
```

### 3. 문서 삭제

```java
// 논리 삭제 (데이터는 유지)
ragService.deleteDocument("회사규정_2024");
```

**PostgreSQL 쿼리:**

```sql
UPDATE document_metadata
SET is_active = false
WHERE document_id = '회사규정_2024';
```

## 💡 장점

### 1. 영구 저장

- ✅ 서버 재시작 시에도 문서 목록 유지
- ✅ 데이터 백업 및 복구 가능

### 2. 확장성

- ✅ 문서 개수 제한 없음 (메모리 한계 X)
- ✅ 복잡한 쿼리 가능 (날짜별, 타입별 등)

### 3. 관리 편의성

- ✅ SQL로 직접 조회/수정 가능
- ✅ 논리 삭제로 데이터 복구 가능
- ✅ 감사(Audit) 추적 가능 (created_at, updated_at)

### 4. 성능

- ✅ 인덱스로 빠른 검색 (document_id, uploaded_at)
- ✅ 페이징 처리 가능

## 🔍 DBeaver로 확인

```sql
-- 모든 문서 조회
SELECT * FROM document_metadata ORDER BY uploaded_at DESC;

-- 활성화된 문서만 조회
SELECT * FROM document_metadata WHERE is_active = true;

-- 전체 청크 개수
SELECT SUM(chunk_count) FROM document_metadata WHERE is_active = true;

-- 파일 타입별 개수
SELECT file_type, COUNT(*) FROM document_metadata
WHERE is_active = true
GROUP BY file_type;

-- 최근 1주일 업로드 문서
SELECT * FROM document_metadata
WHERE uploaded_at >= NOW() - INTERVAL '7 days';
```

## 🎯 현업 활용

### 시나리오 1: 문서 버전 관리

```sql
-- 같은 문서의 여러 버전 저장
INSERT INTO document_metadata (document_id, file_name, ...)
VALUES ('회사규정_v1', '회사규정_v1.pdf', ...);

INSERT INTO document_metadata (document_id, file_name, ...)
VALUES ('회사규정_v2', '회사규정_v2.pdf', ...);
```

### 시나리오 2: 통계 및 리포트

```sql
-- 월별 업로드 통계
SELECT
    DATE_TRUNC('month', uploaded_at) as month,
    COUNT(*) as document_count,
    SUM(chunk_count) as total_chunks,
    SUM(file_size) as total_size
FROM document_metadata
WHERE is_active = true
GROUP BY month
ORDER BY month DESC;
```

### 시나리오 3: 문서 복구

```sql
-- 삭제된 문서 복구
UPDATE document_metadata
SET is_active = true
WHERE document_id = '회사규정_2024';
```

## 📝 마이그레이션 가이드

기존 메모리 데이터가 있다면:

1. **서버 재시작 전에 문서 목록 백업**

   - 현재 업로드된 문서 목록을 기록

2. **코드 배포 후 서버 재시작**

   - 테이블 자동 생성 (`ddl-auto=update`)

3. **문서 재업로드**
   - 백업한 문서들을 다시 업로드
   - PostgreSQL에 자동 저장됨

## 🎉 결론

이제 **프로덕션 레벨의 문서 관리 시스템**을 갖추었습니다!

- ✅ 메모리 저장 → **PostgreSQL 영구 저장**
- ✅ 임시 데이터 → **영구 데이터**
- ✅ 일반 MVC 패턴 → **Spring Boot 베스트 프랙티스**

---

## 🔄 현재 프로젝트 설정 (application-dev.properties)

### 단일 DB 구조 (PostgreSQL Only)

```properties
# ==================== Spring Boot 메인 데이터소스 (JPA Entity용) ====================
# DocumentMetadata 등 일반 JPA Entity를 위한 PostgreSQL 설정
spring.datasource.url=jdbc:postgresql://localhost:5432/vector_db
spring.datasource.username=postgres
spring.datasource.password=1234!@
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA 설정
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# ==================== PostgreSQL pgvector for RAG ====================
pgvector.datasource.url=jdbc:postgresql://localhost:5432/vector_db
pgvector.datasource.username=postgres
pgvector.datasource.password=1234!@
pgvector.datasource.driver-class-name=org.postgresql.Driver
pgvector.table.name=embeddings
pgvector.dimension=768
```

**구조:**

```
PostgreSQL (localhost:5432/vector_db)
├── document_metadata 테이블  (Spring Boot 메인 데이터소스)
└── embeddings 테이블         (pgvector - 벡터 저장)
```

---

## 🎯 대안: MySQL + PostgreSQL 혼용 (권장)

### 왜 분리하는가?

현업에서는 **일반 데이터는 MySQL**, **벡터 데이터는 PostgreSQL**로 분리하는 것이 일반적입니다.

| 구분       | MySQL                                 | PostgreSQL pgvector             |
| ---------- | ------------------------------------- | ------------------------------- |
| **용도**   | 일반 비즈니스 로직                    | 벡터 검색 전용                  |
| **데이터** | User, Order, DocumentMetadata 등      | Embeddings (벡터)               |
| **장점**   | 기존 인프라 활용, 익숙함              | pgvector 확장, 벡터 검색 최적화 |
| **예시**   | 회원 관리, 주문 관리, 문서 메타데이터 | RAG 문서 청크 벡터              |

### 설정 방법

#### 1. `application-dev.properties`

```properties
# ==================== MySQL (메인 데이터소스) ====================
# 일반 JPA Entity용 - DocumentMetadata, User, Order 등
spring.datasource.url=jdbc:mysql://localhost:3306/langchain_dev
spring.datasource.username=root
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA 설정
spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# ==================== PostgreSQL pgvector (벡터 전용) ====================
# 벡터 임베딩 저장용 - LangChain4j EmbeddingStore
pgvector.datasource.url=jdbc:postgresql://localhost:5432/vector_db
pgvector.datasource.username=postgres
pgvector.datasource.password=1234!@
pgvector.datasource.driver-class-name=org.postgresql.Driver
pgvector.table.name=embeddings
pgvector.dimension=768
```

#### 2. 데이터베이스 구조

```
MySQL (localhost:3306/langchain_dev)
├── document_metadata  ⭐ 문서 메타데이터
├── users              ⭐ 회원 정보
├── orders             ⭐ 주문 정보
└── ...                ⭐ 기타 비즈니스 데이터

PostgreSQL (localhost:5432/vector_db)
└── embeddings         ⭐ 벡터 임베딩 (pgvector)
```

#### 3. 코드 변경 없음!

```java
// DocumentMetadata는 자동으로 MySQL로 감
@Entity
@Table(name = "document_metadata")
public class DocumentMetadata {
    // Spring Boot 메인 데이터소스(MySQL) 사용
}

// EmbeddingStore는 자동으로 PostgreSQL로 감
@Bean
public EmbeddingStore<TextSegment> embeddingStore() {
    // pgvector.datasource.* 설정 사용
}
```

#### 4. 장점

✅ **기존 MySQL 인프라 유지**

- 기존 회원, 주문 등 데이터는 MySQL 그대로 사용
- MySQL 백업/복구 프로세스 그대로 유지
- DBA가 익숙한 MySQL 계속 관리

✅ **벡터 검색은 최적화된 PostgreSQL**

- pgvector 확장으로 벡터 검색 최적화
- 벡터 데이터만 분리하여 성능 향상
- 벡터 DB 스케일링 독립적으로 가능

✅ **관심사 분리 (Separation of Concerns)**

- 비즈니스 데이터 ↔ 벡터 데이터 분리
- 각각 독립적으로 백업/복구
- 장애 격리

#### 5. DBeaver 확인

**MySQL 연결:**

```
langchain_dev
└── Tables
    ├── document_metadata  ✅
    ├── users
    └── orders
```

**PostgreSQL 연결:**

```
vector_db
└── Tables
    └── embeddings  ✅
```

---

## 📊 구조 비교

### 옵션 1: PostgreSQL Only (현재)

```
PostgreSQL
├── document_metadata  (JPA)
└── embeddings         (pgvector)
```

**장점:**

- 단일 DB 관리
- 설정 간단

**단점:**

- 기존 MySQL 인프라 못 씀
- 모든 데이터를 PostgreSQL로 마이그레이션 필요

### 옵션 2: MySQL + PostgreSQL (권장)

```
MySQL
├── document_metadata  (JPA)
├── users
└── orders

PostgreSQL
└── embeddings         (pgvector)
```

**장점:**

- ✅ 기존 MySQL 인프라 활용
- ✅ 벡터 검색 최적화 (pgvector)
- ✅ 관심사 분리
- ✅ 독립적 스케일링

**단점:**

- 두 개의 DB 관리 (사실상 이점임)

---

## 🚀 MySQL + PostgreSQL 전환 가이드

### 현재 설정에서 전환하기

#### 1. MySQL 준비

```sql
-- MySQL에서 데이터베이스 생성
CREATE DATABASE langchain_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 2. application-dev.properties 수정

```properties
# PostgreSQL → MySQL로 변경
spring.datasource.url=jdbc:mysql://localhost:3306/langchain_dev
spring.datasource.username=root
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect

# pgvector는 그대로 PostgreSQL 유지
pgvector.datasource.url=jdbc:postgresql://localhost:5432/vector_db
# ... (변경 없음)
```

#### 3. build.gradle (MySQL 드라이버 추가)

```gradle
dependencies {
    // PostgreSQL (pgvector용)
    implementation 'org.postgresql:postgresql:42.7.4'

    // MySQL (메인 데이터소스용)
    implementation 'mysql:mysql-connector-java:8.0.33'  // 추가
}
```

#### 4. 서버 재시작

```bash
./gradlew bootRun
```

**MySQL에 테이블 자동 생성:**

```
Hibernate: create table document_metadata (...) engine=InnoDB
```

#### 5. 확인

```sql
-- MySQL에서
USE langchain_dev;
SHOW TABLES;  -- document_metadata ✅

-- PostgreSQL에서
\c vector_db
\dt  -- embeddings ✅
```

---

## 💡 결론

### 단일 DB (PostgreSQL)

- 간단한 프로젝트
- 새 프로젝트 (레거시 없음)
- PostgreSQL 인프라 이미 있음

### Multi DB (MySQL + PostgreSQL) ⭐ 권장

- 기존 MySQL 인프라 활용
- 현업 베스트 프랙티스
- 대규모 서비스
- 관심사 분리

**답변: 네, MySQL로 DocumentMetadata 관리하고 PostgreSQL은 벡터만 쓰는 게 더 좋습니다!** 🎉
