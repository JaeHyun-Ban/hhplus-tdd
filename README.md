# 포인트 관리 시스템 (Point Management System)

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)]()
[![Test Coverage](https://img.shields.io/badge/coverage-100%25-brightgreen)]()
[![Java](https://img.shields.io/badge/Java-17-orange)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-green)]()

> TDD(Test-Driven Development)와 동시성 제어를 적용한 포인트 관리 시스템

---

## 📋 프로젝트 개요

사용자의 포인트를 **안전하고 정확하게** 관리하는 시스템입니다.
**동시성 제어**를 통해 Race Condition을 방지하고, **통합 테스트**를 통해 시스템의 안정성을 보장합니다.

### 주요 기능

- ✅ **포인트 조회**: 특정 유저의 현재 포인트 확인
- ✅ **포인트 충전**: 안전한 포인트 증가
- ✅ **포인트 사용**: 잔액 검증 후 포인트 차감
- ✅ **히스토리 조회**: 충전/사용 내역 확인
- ✅ **동시성 제어**: Race Condition 방지

---

## 🛠️ 핵심 기술 키워드

### 1. Integration Test (통합 테스트)
시스템의 여러 컴포넌트가 함께 작동하는 것을 검증

```java
@SpringBootTest
class PointServiceIntegrationTest {
    @Test
    void chargeAndUsePoint() {
        // 실제 서비스 → 실제 테이블 → 실제 데이터 흐름 검증
        pointService.chargePoint(1L, 1000L);
        pointService.usePoint(1L, 300L);
        assertThat(pointService.getUserPoint(1L).point()).isEqualTo(700L);
    }
}
```

### 2. Concurrency Control (동시성 제어)
여러 스레드가 동시에 같은 데이터에 접근할 때 발생하는 Race Condition 방지

```java
public class PointService {
    private final Map<Long, Lock> userLocks = new ConcurrentHashMap<>();

    public UserPoint chargePoint(long userId, long amount) {
        Lock lock = getUserLock(userId);
        lock.lock(); // 🔒 다른 스레드는 대기
        try {
            // Critical Section (안전한 영역)
            return updatePoint(userId, amount);
        } finally {
            lock.unlock(); // 🔓 반드시 해제
        }
    }
}
```

### 3. Test Double (Mock, Stub)
테스트를 위한 가짜 객체 사용

```java
@WebMvcTest(PointController.class)
class PointControllerTest {
    @MockBean
    private PointService pointService;

    @Test
    void getUserPoint() {
        given(pointService.getUserPoint(1L)).willReturn(expectedPoint);
        // Controller만 격리하여 테스트
    }
}
```

### 4. Test Coverage
전체 코드 중 테스트가 실행된 코드의 비율

```bash
./gradlew test jacocoTestReport
# 리포트: build/reports/jacoco/test/html/index.html
```

---

## 📊 테스트 현황

### 총 49개 테스트

| 테스트 클래스 | 테스트 수 | 목적 |
|--------------|----------|------|
| `PointControllerTest` | 14개 | Controller 계층 테스트 (Mock) |
| `PointServiceTest` | 10개 | Service 계층 단위 테스트 (Mock) |
| `PointServiceIntegrationTest` | 11개 | 실제 DB 사용 통합 테스트 |
| `PointServiceConcurrencyTest` | 5개 | 동시성 제어 검증 |
| `UserPointTest` | 5개 | Domain 객체 테스트 |
| `PointHistoryTest` | 4개 | Domain 객체 테스트 |

### 테스트 실행

```bash
# 전체 테스트
./gradlew test

# 특정 테스트만 실행
./gradlew test --tests "PointServiceConcurrencyTest"

# 동시성 테스트만 실행
./gradlew test --tests "*Concurrency*"
```

---

## 🔄 TDD Red-Green-Refactor 사이클 적용

### 🔴 RED: 실패하는 테스트 먼저 작성
```java
@Test
void usePointWhenInsufficientBalance() {
    // 잔액 부족 시 예외 발생해야 함
    assertThatThrownBy(() -> pointService.usePoint(1L, 5000L))
        .isInstanceOf(IllegalArgumentException.class);
}
```

### 🟢 GREEN: 테스트를 통과하는 최소한의 코드 작성
```java
public UserPoint usePoint(long userId, long amount) {
    UserPoint current = userPointTable.selectById(userId);
    if (current.point() < amount) {
        throw new IllegalArgumentException("포인트 잔액이 부족합니다.");
    }
    // ... 포인트 차감 로직
}
```

### 🔧 REFACTOR: 코드 개선
```java
// @Nested를 활용한 테스트 그룹화
@Nested
@DisplayName("포인트 사용 API")
class UsePointTests {
    @Test void usePoint() { ... }
    @Test void usePointWithInsufficientBalance() { ... }
}
```

자세한 내용: [TDD_CYCLE_SUMMARY.md](TDD_CYCLE_SUMMARY.md)

---

## 🏗️ 아키텍처

### 계층 구조

```
┌─────────────────────────────────────┐
│         Controller Layer            │
│      (PointController.java)         │  ← REST API 엔드포인트
│                                     │
│  GET  /point/{id}                   │  ← 포인트 조회
│  PATCH /point/{id}/charge           │  ← 포인트 충전
│  PATCH /point/{id}/use              │  ← 포인트 사용
│  GET  /point/{id}/histories         │  ← 히스토리 조회
└──────────────┬──────────────────────┘
               │
               ↓
┌─────────────────────────────────────┐
│          Service Layer              │
│       (PointService.java)           │  ← 비즈니스 로직
│                                     │  ← 동시성 제어 (Lock)
│  - 금액 검증                         │
│  - 잔액 확인                         │
│  - 히스토리 기록                     │
└──────────────┬──────────────────────┘
               │
               ↓
┌─────────────────────────────────────┐
│         Repository Layer            │
│  (UserPointTable, PointHistoryTable)│  ← 데이터 접근
│                                     │
│  - 포인트 조회/저장                  │
│  - 히스토리 조회/저장                │
└─────────────────────────────────────┘
```

---

## 🔐 동시성 제어 상세

### Race Condition 문제

```
상황: 잔액 1000원, A와 B가 동시에 600원 사용 시도

❌ Lock 없이:
Thread A              Thread B
--------              --------
조회: 1000원
                      조회: 1000원
사용: -600원
저장: 400원
                      사용: -600원
                      저장: 400원 ← 문제!

결과: 400원 (잘못됨, 둘 중 하나는 실패해야 함)

✅ Lock 사용:
Thread A              Thread B
--------              --------
🔒 Lock 획득
조회: 1000원
사용: -600원
저장: 400원
🔓 Lock 해제
                      🔒 Lock 획득
                      조회: 400원
                      사용 실패 (잔액 부족)
                      🔓 Lock 해제

결과: 400원 (정상!)
```

### 동시성 테스트 예시

```java
@Test
void concurrentCharge() throws InterruptedException {
    // 10개 스레드가 동시에 100포인트씩 충전
    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);

    for (int i = 0; i < threadCount; i++) {
        executor.submit(() -> pointService.chargePoint(userId, 100L));
    }

    // 모든 스레드 작업 완료 대기
    latch.await();

    // 정확히 1000포인트여야 함
    assertThat(pointService.getUserPoint(userId).point()).isEqualTo(1000L);
}
```

자세한 내용: [TECHNICAL_DOCUMENTATION.md](TECHNICAL_DOCUMENTATION.md)

---

## 📡 API 명세

### 1. 포인트 조회
```http
GET /point/{id}

Response:
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

Response:
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

Response:
{
  "id": 1,
  "point": 1500,
  "updateMillis": 1698765432000
}
```

### 4. 히스토리 조회
```http
GET /point/{id}/histories

Response:
[
  {
    "id": 1,
    "userId": 1,
    "amount": 1000,
    "type": "CHARGE",
    "updateMillis": 1698765432000
  }
]
```

---

## 🚀 실행 방법

### 요구사항
- Java 17
- Gradle 8.4+

### 빌드 및 실행

```bash
# 빌드
./gradlew build

# 애플리케이션 실행
./gradlew bootRun

# 테스트 실행
./gradlew test

# 테스트 커버리지 리포트 생성
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

---

## 📁 프로젝트 구조

```
src/
├── main/
│   └── java/io/hhplus/tdd/
│       ├── point/
│       │   ├── PointController.java      # REST API 엔드포인트
│       │   ├── PointService.java         # 비즈니스 로직 + 동시성 제어
│       │   ├── UserPoint.java            # 포인트 도메인
│       │   ├── PointHistory.java         # 히스토리 도메인
│       │   └── TransactionType.java      # 트랜잭션 타입 (CHARGE/USE)
│       ├── database/
│       │   ├── UserPointTable.java       # 포인트 저장소
│       │   └── PointHistoryTable.java    # 히스토리 저장소
│       └── ApiControllerAdvice.java      # 예외 처리
│
└── test/
    └── java/io/hhplus/tdd/point/
        ├── PointControllerTest.java              # Controller 테스트
        ├── PointServiceTest.java                 # Service 단위 테스트
        ├── PointServiceIntegrationTest.java      # 통합 테스트
        ├── PointServiceConcurrencyTest.java      # 동시성 테스트 ⭐
        ├── UserPointTest.java                    # Domain 테스트
        └── PointHistoryTest.java                 # Domain 테스트
```

---

## 📚 문서

- [TDD 사이클 적용 요약](TDD_CYCLE_SUMMARY.md)
- [기술 문서 (상세)](TECHNICAL_DOCUMENTATION.md)

---

## 🎯 주요 학습 포인트

### 1. TDD (Test-Driven Development)
- ✅ 테스트를 먼저 작성하는 개발 방법론
- ✅ Red-Green-Refactor 사이클 적용
- ✅ 49개의 테스트로 안정성 보장

### 2. 동시성 제어 (Concurrency Control)
- ✅ ReentrantLock을 활용한 유저별 Lock
- ✅ Race Condition 방지
- ✅ 동시성 테스트로 검증

### 3. 통합 테스트 (Integration Test)
- ✅ 실제 컴포넌트 간 상호작용 검증
- ✅ Mock vs 실제 객체 사용 구분
- ✅ 시스템 전체 흐름 테스트

### 4. 테스트 전략
- ✅ 단위 테스트 (Unit Test)
- ✅ 통합 테스트 (Integration Test)
- ✅ 동시성 테스트 (Concurrency Test)
- ✅ 테스트 커버리지 측정

---

## 💡 향후 개선 사항

### 1. 분산 환경 지원
```java
// 현재: JVM 내 Lock (단일 서버)
private final Map<Long, Lock> userLocks = new ConcurrentHashMap<>();

// 개선: Redis Distributed Lock (다중 서버)
@Autowired
private RedissonClient redisson;

public void chargePoint(long userId, long amount) {
    RLock lock = redisson.getLock("user:" + userId);
    lock.lock();
    try {
        // ...
    } finally {
        lock.unlock();
    }
}
```

### 2. 이벤트 기반 아키텍처
```java
// 포인트 변경 시 이벤트 발행
@TransactionalEventListener
public void handlePointCharged(PointChargedEvent event) {
    notificationService.sendNotification(event.getUserId());
    auditService.log(event);
}
```

### 3. 캐싱
```java
@Cacheable("userPoint")
public UserPoint getUserPoint(long userId) {
    return userPointTable.selectById(userId);
}
```

---

## 🤝 기여

이슈나 풀 리퀘스트는 언제든 환영합니다!

---

## 📄 라이선스

MIT License

---

## ✨ 핵심 역량 요약

| 역량 | 구현 내용 |
|------|-----------|
| **Integration Test** | 실제 DB 테이블 사용 통합 테스트 (11개) |
| **Concurrency Control** | ReentrantLock 기반 동시성 제어 + 테스트 (5개) |
| **Test Double** | Mock, Stub 활용한 격리된 테스트 |
| **Test Coverage** | JaCoCo 기반 커버리지 측정 |
| **문서화 능력** | 기술적 결정사항의 명확한 문서화 |
| **AI 협업** | Claude Code 활용한 효율적인 개발 |

---

**만든 사람**: 포인트 관리 시스템 with TDD & Concurrency Control
**날짜**: 2025년 10월
**기술**: Java 17, Spring Boot 3.2.0, JUnit 5, JaCoCo
