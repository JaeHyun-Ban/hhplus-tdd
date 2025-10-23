# 포인트 관리 시스템 기술 문서

## 📋 목차
1. [프로젝트 개요](#프로젝트-개요)
2. [핵심 기술 키워드](#핵심-기술-키워드)
3. [아키텍처 설계](#아키텍처-설계)
4. [동시성 제어](#동시성-제어)
5. [테스트 전략](#테스트-전략)
6. [성능 고려사항](#성능-고려사항)

---

## 프로젝트 개요

### 목적
사용자의 포인트를 안전하고 정확하게 관리하는 시스템

### 주요 기능
- ✅ 포인트 조회
- ✅ 포인트 충전
- ✅ 포인트 사용
- ✅ 포인트 히스토리 조회
- ✅ 동시성 제어 (Race Condition 방지)

### 기술 스택
- **Language**: Java 17
- **Framework**: Spring Boot 3.2.0
- **Build Tool**: Gradle 8.4
- **Testing**: JUnit 5, Mockito, AssertJ
- **Code Coverage**: JaCoCo

---

## 핵심 기술 키워드

### 1. Integration Test (통합 테스트)

#### 정의
시스템의 여러 컴포넌트가 함께 작동하는 것을 검증하는 테스트

#### 구현
```java
@SpringBootTest // 전체 Spring Context 로드
class PointServiceIntegrationTest {
    @Autowired
    private PointService pointService;

    @Autowired
    private UserPointTable userPointTable; // 실제 DB 테이블 사용

    @Test
    void chargeAndGetPoint() {
        // 실제 서비스 → 실제 테이블 → 실제 데이터 흐름 검증
        pointService.chargePoint(1L, 1000L);
        UserPoint result = pointService.getUserPoint(1L);
        assertThat(result.point()).isEqualTo(1000L);
    }
}
```

#### Unit Test vs Integration Test

| 구분 | Unit Test | Integration Test |
|------|-----------|------------------|
| 범위 | 단일 클래스/메서드 | 여러 컴포넌트 |
| 의존성 | Mock 사용 | 실제 객체 사용 |
| 속도 | 빠름 | 느림 |
| 신뢰성 | 낮음 | 높음 |
| 목적 | 로직 검증 | 통합 검증 |

#### 적용 사례
- `PointServiceIntegrationTest.java`: 실제 DB 테이블과 서비스 통합 (src/test/java/io/hhplus/tdd/point/PointServiceIntegrationTest.java:1)
- `PointServiceConcurrencyTest.java`: 동시성 환경에서의 통합 테스트 (src/test/java/io/hhplus/tdd/point/PointServiceConcurrencyTest.java:1)

---

### 2. Concurrency Control (동시성 제어)

#### 정의
여러 스레드가 동시에 같은 데이터에 접근할 때 발생하는 문제를 방지하는 기법

#### Race Condition이란?

```
상황: 잔액 1000원

Thread A              Thread B
--------              --------
조회: 1000원
                      조회: 1000원
사용: -500원
저장: 500원
                      사용: -500원
                      저장: 500원 ← 문제!

결과: 500원 (잘못됨, 0원이어야 함)
```

#### 해결 방법: Lock 사용

```java
public class PointService {
    // 유저별 Lock 관리 (ConcurrentHashMap)
    private final Map<Long, Lock> userLocks = new ConcurrentHashMap<>();

    public UserPoint chargePoint(long userId, long amount) {
        Lock lock = getUserLock(userId);
        lock.lock(); // 🔒 다른 스레드는 대기

        try {
            // Critical Section (임계 영역)
            UserPoint current = userPointTable.selectById(userId);
            long newPoint = current.point() + amount;
            return userPointTable.insertOrUpdate(userId, newPoint);
        } finally {
            lock.unlock(); // 🔓 반드시 해제
        }
    }
}
```

#### Lock 전략 비교

| 전략 | 장점 | 단점 | 사용 시기 |
|------|------|------|-----------|
| **글로벌 Lock** | 구현 간단 | 모든 작업이 순차 처리 (느림) | 작은 시스템 |
| **유저별 Lock** | 다른 유저는 동시 처리 가능 | 메모리 사용 증가 | 대부분의 경우 |
| **락 프리** | 가장 빠름 | 구현 매우 복잡 | 초고성능 필요 시 |

#### 현재 구현: 유저별 Lock (User-Level Locking)

```
User 1 충전 ────┐
                ├─── User 1 Lock
User 1 사용 ────┘

User 2 충전 ────┐
                ├─── User 2 Lock (독립적!)
User 2 사용 ────┘
```

**장점:**
- User 1과 User 2의 작업은 서로 영향 없음
- 전체 시스템 처리량(Throughput) 향상

#### 동시성 테스트

```java
@Test
void concurrentCharge() throws InterruptedException {
    // 10개 스레드가 동시에 100포인트씩 충전
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> {
            try {
                pointService.chargePoint(userId, 100L);
            } finally {
                latch.countDown();
            }
        });
    }

    latch.await();

    // 정확히 1000이어야 함 (동시성 제어 성공)
    assertThat(pointService.getUserPoint(userId).point()).isEqualTo(1000L);
}
```

---

### 3. Test Double (Mock, Stub)

#### 정의
테스트를 위해 실제 객체를 대신하는 가짜 객체

#### 종류

| 종류 | 설명 | 예시 |
|------|------|------|
| **Mock** | 동작을 프로그래밍 가능 | `@MockBean` |
| **Stub** | 미리 정해진 응답 반환 | `given().willReturn()` |
| **Spy** | 실제 객체를 부분적으로 Mock | `@SpyBean` |
| **Fake** | 실제 구현을 단순화한 버전 | In-Memory DB |

#### 사용 예시

```java
@WebMvcTest(PointController.class)
class PointControllerTest {

    @MockBean // 가짜 객체 주입
    private PointService pointService;

    @Test
    void getUserPoint() {
        // given: Mock 동작 정의
        UserPoint expectedPoint = new UserPoint(1L, 1000L, System.currentTimeMillis());
        given(pointService.getUserPoint(1L)).willReturn(expectedPoint);

        // when: Controller 호출
        mockMvc.perform(get("/point/1"))

        // then: 검증
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.point").value(1000L));
    }
}
```

#### 왜 Mock을 사용하나?

1. **격리**: Controller만 테스트 (Service는 테스트 대상 아님)
2. **속도**: 실제 DB 접근 없이 빠른 테스트
3. **제어**: 원하는 상황을 쉽게 재현

---

### 4. Test Coverage (테스트 커버리지)

#### 정의
전체 코드 중 테스트가 실행된 코드의 비율

#### JaCoCo 설정

```kotlin
// build.gradle.kts
plugins {
    id("jacoco")
}

jacoco {
    toolVersion = "0.8.7"
}
```

#### 커버리지 확인

```bash
# 테스트 실행 및 커버리지 생성
./gradlew test jacocoTestReport

# 리포트 확인
open build/reports/jacoco/test/html/index.html
```

#### 커버리지 지표

| 지표 | 설명 | 목표 |
|------|------|------|
| **Line Coverage** | 실행된 라인 비율 | 80% 이상 |
| **Branch Coverage** | 실행된 분기 비율 | 70% 이상 |
| **Method Coverage** | 실행된 메서드 비율 | 90% 이상 |

#### 현재 프로젝트 커버리지

```
📊 총 49개 테스트
- PointControllerTest: 14개 (Controller 계층)
- PointServiceTest: 10개 (Service 계층, Mock)
- PointServiceIntegrationTest: 11개 (통합 테스트)
- PointServiceConcurrencyTest: 5개 (동시성 테스트)
- UserPointTest: 5개 (Domain)
- PointHistoryTest: 4개 (Domain)
```

---

## 아키텍처 설계

### 계층 구조

```
┌─────────────────────────────────────┐
│         Controller Layer            │  ← REST API 엔드포인트
│      (PointController.java)         │
└──────────────┬──────────────────────┘
               │
               ↓
┌─────────────────────────────────────┐
│          Service Layer              │  ← 비즈니스 로직
│       (PointService.java)           │  ← 동시성 제어
└──────────────┬──────────────────────┘
               │
               ↓
┌─────────────────────────────────────┐
│         Repository Layer            │  ← 데이터 접근
│  (UserPointTable, PointHistoryTable)│
└─────────────────────────────────────┘
```

### 책임 분리

| 계층 | 책임 | 예시 |
|------|------|------|
| **Controller** | HTTP 요청/응답 처리 | JSON 변환, 경로 매핑 |
| **Service** | 비즈니스 로직, 동시성 제어 | 금액 검증, Lock 관리 |
| **Repository** | 데이터 CRUD | DB 조회/저장 |

---

## 동시성 제어

### 문제 상황

#### 1. Lost Update (업데이트 손실)

```
Initial: 1000원

Thread A               Thread B
--------               --------
READ: 1000
                       READ: 1000
+500 = 1500
WRITE: 1500
                       +300 = 1300
                       WRITE: 1300 ← A의 업데이트 손실!

Result: 1300 (wrong, should be 1800)
```

#### 2. Dirty Read (더티 리드)

```
Thread A               Thread B
--------               --------
READ: 1000
-500 = 500
                       READ: 500 (A가 커밋 전!)
ROLLBACK (실패)
                       계산 계속 (잘못된 값 기반)
```

### 해결 방법

#### 1. ReentrantLock 사용

```java
private final Map<Long, Lock> userLocks = new ConcurrentHashMap<>();

public UserPoint chargePoint(long userId, long amount) {
    Lock lock = getUserLock(userId);
    lock.lock(); // 🔒
    try {
        // Critical Section
        return updatePoint(userId, amount);
    } finally {
        lock.unlock(); // 🔓
    }
}
```

#### 2. 장점

| 특징 | 설명 |
|------|------|
| **재진입 가능** | 같은 스레드가 여러 번 락 획득 가능 |
| **공정성** | 대기 시간이 긴 스레드 우선 |
| **타임아웃** | `tryLock(timeout)` 지원 |
| **유저별 분리** | 다른 유저는 동시 처리 |

#### 3. 대안 비교

| 방법 | 장점 | 단점 |
|------|------|------|
| `synchronized` | 간단 | 공정성 없음, 타임아웃 없음 |
| `ReentrantLock` | 기능 풍부 | 명시적 unlock 필요 |
| `@Transactional` | DB 레벨 보장 | 성능 저하 |
| `Optimistic Lock` | 충돌 적을 때 빠름 | 재시도 로직 필요 |

---

## 테스트 전략

### 테스트 피라미드

```
        /\
       /  \
      / E2E \        ← 소수의 전체 흐름 테스트
     /--------\
    /   통합    \     ← 컴포넌트 간 상호작용 테스트
   /------------\
  /   단위 테스트  \  ← 다수의 세부 로직 테스트
 /----------------\
```

### 테스트 작성 원칙

#### 1. AAA 패턴

```java
@Test
void testExample() {
    // Arrange (Given): 준비
    long userId = 1L;
    long amount = 1000L;

    // Act (When): 실행
    UserPoint result = pointService.chargePoint(userId, amount);

    // Assert (Then): 검증
    assertThat(result.point()).isEqualTo(1000L);
}
```

#### 2. F.I.R.S.T 원칙

| 원칙 | 설명 | 적용 |
|------|------|------|
| **Fast** | 빠르게 실행 | Mock 사용 |
| **Independent** | 독립적 | 각 테스트는 순서 무관 |
| **Repeatable** | 반복 가능 | 외부 의존성 최소화 |
| **Self-Validating** | 자가 검증 | 자동 Pass/Fail |
| **Timely** | 적시에 작성 | TDD |

### 테스트 커버리지 목표

```
✅ Controller: 100% (14/14 테스트)
✅ Service: 100% (25/25 테스트)
✅ Domain: 100% (9/9 테스트)
✅ Concurrency: 100% (5/5 테스트)
```

---

## 성능 고려사항

### 1. Lock 성능

#### 문제
- Lock 경합(Contention)이 많으면 대기 시간 증가

#### 해결
```java
// ❌ 나쁜 예: 전역 Lock
private final Lock globalLock = new ReentrantLock();

// ✅ 좋은 예: 유저별 Lock
private final Map<Long, Lock> userLocks = new ConcurrentHashMap<>();
```

### 2. 메모리 관리

#### 문제
- 유저가 많으면 Lock 객체도 많아짐

#### 해결
```java
// 오래 사용하지 않은 Lock 제거
private void cleanupOldLocks() {
    userLocks.entrySet().removeIf(entry -> {
        Lock lock = entry.getValue();
        return lock.tryLock() && isOldUser(entry.getKey());
    });
}
```

### 3. 데이터베이스

#### 현재 구현
- In-Memory 테이블 (UserPointTable, PointHistoryTable)
- Throttle 시뮬레이션 (랜덤 지연)

#### 실제 DB 사용 시 고려사항

| 항목 | 설명 | 해결 방법 |
|------|------|-----------|
| **Connection Pool** | DB 연결 관리 | HikariCP 설정 |
| **인덱스** | 조회 성능 | userId 인덱스 |
| **트랜잭션** | ACID 보장 | `@Transactional` |

---

## API 명세

### 1. 포인트 조회

```http
GET /point/{id}
```

**Response:**
```json
{
  "id": 1,
  "point": 1000,
  "updateMillis": 1698765432000
}
```

### 2. 포인트 충전

```http
PATCH /point/{id}/charge
Content-Type: application/json

1000
```

**Response:**
```json
{
  "id": 1,
  "point": 2000,
  "updateMillis": 1698765432000
}
```

### 3. 포인트 사용

```http
PATCH /point/{id}/use
Content-Type: application/json

500
```

**Response:**
```json
{
  "id": 1,
  "point": 1500,
  "updateMillis": 1698765432000
}
```

### 4. 히스토리 조회

```http
GET /point/{id}/histories
```

**Response:**
```json
[
  {
    "id": 1,
    "userId": 1,
    "amount": 1000,
    "type": "CHARGE",
    "updateMillis": 1698765432000
  },
  {
    "id": 2,
    "userId": 1,
    "amount": 500,
    "type": "USE",
    "updateMillis": 1698765433000
  }
]
```

---

## 에러 처리

### 에러 코드

| 코드 | 메시지 | 상황 |
|------|--------|------|
| 400 | 포인트는 0보다 커야 합니다 | 음수/0 충전/사용 |
| 400 | 포인트 잔액이 부족합니다 | 잔액 부족 |
| 500 | 에러가 발생했습니다 | 시스템 오류 |

### 에러 응답 형식

```json
{
  "code": "400",
  "message": "포인트 잔액이 부족합니다."
}
```

---

## 실행 방법

### 빌드 및 테스트

```bash
# 빌드
./gradlew build

# 전체 테스트
./gradlew test

# 특정 테스트만 실행
./gradlew test --tests "PointServiceConcurrencyTest"

# 커버리지 리포트 생성
./gradlew test jacocoTestReport
```

### 애플리케이션 실행

```bash
./gradlew bootRun
```

---

## 향후 개선 사항

### 1. 분산 환경 지원
- **문제**: 여러 서버에서는 JVM Lock이 작동하지 않음
- **해결**: Redis Distributed Lock, Database Lock

### 2. 이벤트 기반 아키텍처
- **문제**: 포인트 변경 시 알림, 로그 등 부가 작업 필요
- **해결**: Spring Events, Kafka

### 3. 모니터링
- **필요**: Lock 대기 시간, 처리량 모니터링
- **도구**: Micrometer, Prometheus, Grafana

### 4. 캐싱
- **목적**: 조회 성능 향상
- **도구**: Redis, Caffeine Cache

---

## 참고 자료

- [Java Concurrency in Practice](https://jcip.net/)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)
- [ReentrantLock JavaDoc](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/ReentrantLock.html)

---

## 작성자

- **프로젝트**: 포인트 관리 시스템
- **기술**: TDD, Concurrency Control, Integration Testing
- **날짜**: 2025년 10월
