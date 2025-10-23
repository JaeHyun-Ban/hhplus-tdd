# 포인트 관리 시스템 - TDD 및 동시성 제어

## 프로젝트 개요

이 프로젝트는 **Test-Driven Development (TDD)** 방법론을 적용하여 개발된 포인트 관리 시스템입니다.
사용자의 포인트 충전, 사용, 조회 기능을 제공하며, **동시성 제어**를 통해 멀티스레드 환경에서도 데이터 일관성을 보장합니다.

## 목차

1. [기술 스택](#기술-스택)
2. [주요 기능](#주요-기능)
3. [동시성 제어 방식 분석](#동시성-제어-방식-분석)
4. [테스트 전략](#테스트-전략)
5. [프로젝트 구조](#프로젝트-구조)
6. [실행 방법](#실행-방법)

---

## 기술 스택

- **Java 17**: 최신 LTS 버전의 Java 사용
- **Spring Boot 3.2.0**: 애플리케이션 프레임워크
- **Gradle 8.4**: 빌드 도구
- **JUnit 5**: 단위 및 통합 테스트 프레임워크
- **AssertJ**: 가독성 높은 테스트 어설션 라이브러리
- **Mockito**: Mock 객체 생성 및 검증 라이브러리
- **JaCoCo**: 코드 커버리지 측정 도구

---

## 주요 기능

### 1. 포인트 충전 (Charge Point)
- 사용자가 포인트를 충전할 수 있습니다
- 충전 금액은 반드시 양수여야 합니다
- 충전 내역은 히스토리에 기록됩니다

### 2. 포인트 사용 (Use Point)
- 사용자가 보유한 포인트를 사용할 수 있습니다
- 잔액보다 많은 포인트는 사용할 수 없습니다
- 사용 내역은 히스토리에 기록됩니다

### 3. 포인트 조회 (Get Point)
- 사용자의 현재 포인트 잔액을 조회할 수 있습니다
- 신규 사용자는 0원으로 초기화됩니다

### 4. 히스토리 조회 (Get History)
- 사용자의 포인트 충전/사용 내역을 시간순으로 조회할 수 있습니다
- 각 내역에는 금액, 타입(충전/사용), 타임스탬프가 포함됩니다

---

## 동시성 제어 방식 분석

### 📌 동시성 문제 (Concurrency Problem)

포인트 관리 시스템에서는 **여러 스레드가 동시에 같은 사용자의 포인트를 수정**할 때 다음과 같은 문제가 발생할 수 있습니다:

#### Race Condition 시나리오 예시

```
현재 사용자 A의 포인트: 1000원

[스레드 1]                    [스레드 2]
1. 포인트 조회 (1000원)       1. 포인트 조회 (1000원)
2. 500원 충전 계산            2. 300원 충전 계산
   (1000 + 500 = 1500)           (1000 + 300 = 1300)
3. 포인트 저장 (1500원)       3. 포인트 저장 (1300원)

최종 결과: 1300원 (잘못된 결과!)
예상 결과: 1800원 (1000 + 500 + 300)
```

위 시나리오에서 **스레드 2가 스레드 1의 충전 결과를 덮어씌워** 500원의 충전이 손실됩니다.
이를 **Race Condition** 또는 **Lost Update Problem**이라고 합니다.

---

### 🔒 적용된 동시성 제어 방식

이 프로젝트에서는 **사용자별 Lock (Per-User Locking)** 방식을 사용하여 동시성 문제를 해결했습니다.

#### 핵심 구현 방법

```java
// 사용자별 Lock을 관리하는 Thread-Safe Map
private final ConcurrentHashMap<Long, Lock> userLocks;

// 특정 사용자의 Lock을 가져오는 메서드
private Lock getUserLock(long userId) {
    // computeIfAbsent: 해당 userId의 Lock이 없으면 새로 생성
    // true 파라미터: 공정성(fairness) 보장 - 대기 시간이 긴 스레드가 우선권
    return userLocks.computeIfAbsent(userId, id -> new ReentrantLock(true));
}

// 포인트 충전 메서드 (동시성 제어 적용)
public UserPoint chargePoint(long userId, long amount) {
    validateAmount(amount); // Lock 획득 전에 검증 (성능 최적화)

    Lock lock = getUserLock(userId);
    lock.lock(); // Lock 획득 - Critical Section 시작
    try {
        // 포인트 조회 → 계산 → 저장 (원자적 실행 보장)
        UserPoint currentPoint = userPointTable.selectById(userId);
        long newPoint = currentPoint.point() + amount;
        UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, updatedPoint.updateMillis());
        return updatedPoint;
    } finally {
        lock.unlock(); // 반드시 Lock 해제 (예외 발생 시에도)
    }
}
```

---

### 🎯 설계 결정 (Design Decisions)

#### 1️⃣ 왜 ReentrantLock을 선택했는가?

| 비교 항목 | synchronized | ReentrantLock |
|----------|--------------|---------------|
| **공정성(Fairness)** | 미보장 (기아 상태 가능) | 공정성 모드 지원 ✅ |
| **타임아웃** | 지원 안 함 | `tryLock(timeout)` 지원 |
| **인터럽트** | 지원 안 함 | `lockInterruptibly()` 지원 |
| **조건 변수** | `wait()`/`notify()` (단순) | `Condition` (복잡한 조건 지원) |
| **성능** | 약간 빠름 | 약간 느림 (기능 많음) |

**선택 이유**:
- ✅ **공정성 보장**: 먼저 대기한 스레드가 우선권을 가져 기아 상태 방지
- ✅ **명시적 Lock 관리**: try-finally로 확실한 Lock 해제 보장
- ✅ **확장 가능성**: 향후 타임아웃, 인터럽트 기능 추가 가능

#### 2️⃣ 왜 사용자별 Lock인가?

```java
// ❌ 나쁜 예: 전역 Lock (모든 사용자가 같은 Lock 사용)
private final Lock globalLock = new ReentrantLock();

public UserPoint chargePoint(long userId, long amount) {
    globalLock.lock(); // 사용자 A, B, C 모두 대기
    try {
        // ...
    } finally {
        globalLock.unlock();
    }
}
```

**문제점**: 사용자 A의 작업이 사용자 B, C, D... 모든 사용자의 작업을 블로킹합니다.
**처리량(Throughput)** 이 크게 감소하여 성능이 나빠집니다.

```java
// ✅ 좋은 예: 사용자별 Lock (각 사용자마다 독립적인 Lock)
private final ConcurrentHashMap<Long, Lock> userLocks;

public UserPoint chargePoint(long userId, long amount) {
    Lock lock = getUserLock(userId); // 사용자별 Lock
    lock.lock(); // 같은 사용자끼리만 대기
    try {
        // ...
    } finally {
        lock.unlock();
    }
}
```

**장점**:
- ✅ 사용자 A의 작업이 사용자 B의 작업을 블로킹하지 않음
- ✅ **병렬 처리 가능**: 여러 사용자의 작업이 동시에 처리됨
- ✅ **확장성(Scalability)** 향상: 사용자가 많아져도 성능 저하 최소화

#### 3️⃣ 왜 ConcurrentHashMap을 사용했는가?

```java
// ❌ 나쁜 예: 일반 HashMap (Thread-Unsafe)
private final HashMap<Long, Lock> userLocks = new HashMap<>();

private Lock getUserLock(long userId) {
    // 여러 스레드가 동시에 put()하면 데이터 손실 또는 무한 루프 발생!
    if (!userLocks.containsKey(userId)) {
        userLocks.put(userId, new ReentrantLock(true));
    }
    return userLocks.get(userId);
}
```

**문제점**: HashMap은 Thread-Safe하지 않아 동시 접근 시 **데이터 손실**, **NullPointerException**, **무한 루프** 발생 가능

```java
// ✅ 좋은 예: ConcurrentHashMap (Thread-Safe)
private final ConcurrentHashMap<Long, Lock> userLocks;

private Lock getUserLock(long userId) {
    // computeIfAbsent는 원자적 연산 보장
    return userLocks.computeIfAbsent(userId, id -> new ReentrantLock(true));
}
```

**장점**:
- ✅ **Thread-Safe**: 여러 스레드가 동시에 접근해도 안전
- ✅ **원자적 연산**: `computeIfAbsent()`는 중복 생성 방지
- ✅ **고성능**: 세그먼트별 Lock으로 읽기 성능 우수

#### 4️⃣ 읽기 작업은 왜 Lock을 사용하지 않는가?

```java
// 읽기 전용 작업: Lock 없음
public UserPoint getUserPoint(long userId) {
    return userPointTable.selectById(userId); // Lock 없이 바로 조회
}
```

**이유**:
- ✅ **읽기는 동시 실행 가능**: 여러 스레드가 동시에 읽어도 문제없음
- ✅ **성능 최적화**: 읽기 작업은 매우 빈번하므로 Lock 오버헤드 제거
- ✅ **Eventual Consistency 허용**: 약간의 지연된 데이터는 허용 가능 (비즈니스 요구사항에 따라)

---

### 🔄 대안 방식 비교

#### 방식 1: 데이터베이스 Lock (Pessimistic Lock)

```sql
SELECT * FROM user_point WHERE user_id = ? FOR UPDATE;
```

**장점**:
- ✅ 여러 애플리케이션 인스턴스 간에도 동작 (분산 환경)
- ✅ 트랜잭션 커밋 시 자동으로 Lock 해제

**단점**:
- ❌ DB 성능 저하 (Lock 대기로 인한 Connection Pool 고갈)
- ❌ Deadlock 위험 (여러 테이블 Lock 시)
- ❌ 현재 프로젝트는 단일 인스턴스이므로 과한 방식

**결론**: 현재 프로젝트에는 과도하게 무겁고 불필요

#### 방식 2: Optimistic Lock (낙관적 Lock)

```java
@Version
private Long version; // JPA의 @Version 어노테이션 사용

// 업데이트 시 version 체크
UPDATE user_point SET point = ?, version = version + 1
WHERE user_id = ? AND version = ?;
```

**장점**:
- ✅ Lock을 걸지 않아 성능 우수
- ✅ 읽기 작업이 많은 경우 효율적

**단점**:
- ❌ 충돌 발생 시 재시도 로직 필요 (복잡도 증가)
- ❌ 충돌이 빈번하면 오히려 성능 저하
- ❌ 현재 프로젝트는 충돌이 빈번할 가능성 높음 (동시 충전/사용)

**결론**: 충돌 재시도 복잡도 증가, 현재 요구사항에 부적합

#### 방식 3: synchronized 메서드

```java
public synchronized UserPoint chargePoint(long userId, long amount) {
    // 모든 사용자가 이 메서드에 순차 접근
}
```

**장점**:
- ✅ 간단한 구현

**단점**:
- ❌ 사용자별 Lock이 아닌 메서드 전체 Lock (성능 최악)
- ❌ 공정성 보장 안 됨
- ❌ 확장성 없음

**결론**: 성능이 너무 낮아 실무에서 사용 불가능

#### 방식 4: Atomic 클래스 (AtomicLong)

```java
private final ConcurrentHashMap<Long, AtomicLong> points;

public UserPoint chargePoint(long userId, long amount) {
    AtomicLong point = points.computeIfAbsent(userId, id -> new AtomicLong(0));
    long newPoint = point.addAndGet(amount);
    return new UserPoint(userId, newPoint, System.currentTimeMillis());
}
```

**장점**:
- ✅ Lock-Free 알고리즘으로 매우 빠름
- ✅ 간단한 구현

**단점**:
- ❌ 단일 값 연산만 가능 (복합 연산 불가)
- ❌ 히스토리 기록과 포인트 업데이트를 원자적으로 처리 불가능
- ❌ 현재 프로젝트는 "포인트 업데이트 + 히스토리 기록"이 하나의 트랜잭션

**결론**: 복합 연산이 필요한 현재 프로젝트에 부적합

---

### ✅ 최종 선택: 사용자별 ReentrantLock + ConcurrentHashMap

| 평가 항목 | 점수 | 설명 |
|----------|------|------|
| **정확성** | ⭐⭐⭐⭐⭐ | Race Condition 완벽 차단 |
| **성능** | ⭐⭐⭐⭐ | 사용자별 병렬 처리 가능 |
| **확장성** | ⭐⭐⭐⭐ | 사용자 증가에도 성능 유지 |
| **구현 복잡도** | ⭐⭐⭐ | 적절한 수준의 복잡도 |
| **유지보수성** | ⭐⭐⭐⭐ | 명확한 Lock 획득/해제 패턴 |

**종합 평가**: 현재 프로젝트의 요구사항(단일 인스턴스, 복합 연산, 높은 동시성)에 가장 적합한 방식

---

### 📊 동시성 테스트 결과

모든 동시성 테스트가 성공적으로 통과했습니다:

#### 1. 동시 충전 테스트
- ✅ **10개 스레드가 동시에 1000원씩 충전** → 정확히 10,000원
- ✅ **50개 스레드가 동시에 200원씩 충전** → 정확히 10,000원

#### 2. 동시 사용 테스트
- ✅ **10개 스레드가 동시에 500원씩 사용** → 정확히 5,000원 잔액
- ✅ **잔액 부족 시 예외 발생** → 5,000원으로 1,000원씩 10번 사용 시도 → 5번 성공, 5번 실패

#### 3. 충전과 사용 동시 발생 테스트
- ✅ **충전 5회(1000원) + 사용 3회(500원)** → 최소 3,500원 (일부 사용 실패 가능)

#### 4. 여러 사용자 동시 작업 테스트
- ✅ **10명의 사용자가 각각 10개 스레드로 충전** → 모든 사용자가 정확히 10,000원
- ✅ **Lock 격리 검증** → 사용자 A와 B의 작업이 서로 블로킹하지 않음 (200ms 이내 동시 완료)

**테스트 코드 위치**: `src/test/java/io/hhplus/tdd/point/PointServiceConcurrencyTest.java`

---

### 🚀 성능 특성

#### Lock Contention (Lock 경합)

```
동일 사용자에 대한 100개의 동시 요청:
순차 처리 시간 = Lock 대기 시간 합계

다른 사용자에 대한 100개의 동시 요청:
병렬 처리 시간 = max(각 사용자 처리 시간)
```

**실제 성능**:
- 같은 사용자: 순차 처리 (Lock으로 인한 대기 발생)
- 다른 사용자: 병렬 처리 (Lock 격리로 인한 동시 실행)

#### 메모리 사용량

```java
// 사용자 100만 명 가정
ConcurrentHashMap<Long, Lock> userLocks; // 약 32MB (Lock 객체 32 bytes * 1,000,000)
```

**메모리 최적화 고려사항**:
- 장기간 미사용 사용자의 Lock은 제거 가능 (Weak Reference 또는 LRU Cache 사용)
- 현재는 단순성을 위해 무제한 보관 (실무에서는 주기적 정리 필요)

---

### 🔧 향후 개선 방안

#### 1. 분산 환경 지원

현재는 **단일 애플리케이션 인스턴스**를 가정하고 있습니다.
여러 서버 인스턴스에서 실행되는 경우 **분산 Lock**이 필요합니다:

**옵션 A: Redis 분산 Lock**
```java
@Component
public class RedisLockPointService {
    @Autowired
    private RedissonClient redissonClient;

    public UserPoint chargePoint(long userId, long amount) {
        RLock lock = redissonClient.getLock("user:point:" + userId);
        lock.lock(); // 모든 서버 인스턴스에서 공유되는 Lock
        try {
            // ...
        } finally {
            lock.unlock();
        }
    }
}
```

**옵션 B: DB Pessimistic Lock**
```java
@Transactional
public UserPoint chargePoint(long userId, long amount) {
    // SELECT ... FOR UPDATE
    UserPoint currentPoint = userPointRepository.findByIdWithLock(userId);
    // ...
}
```

#### 2. Lock 타임아웃 추가

현재는 무한 대기합니다. 타임아웃을 추가하여 **Deadlock 방지** 가능:

```java
Lock lock = getUserLock(userId);
if (lock.tryLock(5, TimeUnit.SECONDS)) { // 최대 5초 대기
    try {
        // ...
    } finally {
        lock.unlock();
    }
} else {
    throw new TimeoutException("포인트 처리 중 타임아웃 발생");
}
```

#### 3. 읽기-쓰기 Lock 분리

읽기가 쓰기보다 훨씬 많은 경우 **ReadWriteLock** 사용:

```java
private final ConcurrentHashMap<Long, ReadWriteLock> userLocks;

public UserPoint getUserPoint(long userId) {
    ReadWriteLock rwLock = getUserReadWriteLock(userId);
    rwLock.readLock().lock(); // 여러 읽기 스레드 동시 실행 가능
    try {
        return userPointTable.selectById(userId);
    } finally {
        rwLock.readLock().unlock();
    }
}

public UserPoint chargePoint(long userId, long amount) {
    ReadWriteLock rwLock = getUserReadWriteLock(userId);
    rwLock.writeLock().lock(); // 쓰기는 배타적 실행
    try {
        // ...
    } finally {
        rwLock.writeLock().unlock();
    }
}
```

---

## 테스트 전략

이 프로젝트는 **TDD (Test-Driven Development)** 방법론을 엄격하게 따랐습니다.

### 🔴 RED - 🟢 GREEN - 🔧 REFACTOR 사이클

1. **🔴 RED**: 실패하는 테스트 작성
2. **🟢 GREEN**: 최소한의 코드로 테스트 통과
3. **🔧 REFACTOR**: 코드 개선 및 리팩토링

### 테스트 구조

```
src/test/java/io/hhplus/tdd/point/
├── PointHistoryTest.java                    # 도메인 객체 테스트 (7 tests)
├── PointControllerTest.java                 # Controller 단위 테스트 (14 tests)
├── PointServiceTest.java                    # Service 단위 테스트 (Mock 사용)
├── PointServiceExceptionTest.java           # 예외 케이스 테스트 (15 tests)
├── PointFeatureIntegrationTest.java         # 기능별 통합 테스트 (20+ tests)
└── PointServiceConcurrencyTest.java         # 동시성 통합 테스트 (7 tests)
```

### 테스트 커버리지

- **총 테스트 개수**: 80+ 테스트
- **코드 커버리지**: JaCoCo로 측정 (목표: 80% 이상)
- **테스트 유형**:
  - 단위 테스트 (Unit Tests): Mock을 사용한 빠른 테스트
  - 통합 테스트 (Integration Tests): 실제 Spring 컨텍스트 사용
  - 동시성 테스트 (Concurrency Tests): 멀티스레드 환경 테스트

### 주요 테스트 시나리오

#### 1. 예외 케이스 테스트 (PointServiceExceptionTest)
- ✅ 0원 충전/사용 시 예외 발생
- ✅ 음수 금액 충전/사용 시 예외 발생
- ✅ 잔액 부족 시 예외 발생
- ✅ 경계값 테스트 (Long.MAX_VALUE, 최소값)

#### 2. 기능별 통합 테스트 (PointFeatureIntegrationTest)
- ✅ 신규 유저 첫 충전
- ✅ 기존 유저 추가 충전
- ✅ 포인트 일부 사용
- ✅ 포인트 전체 사용
- ✅ 복합 시나리오 (충전 → 사용 → 재충전 → 재사용)
- ✅ 여러 사용자 독립적 작업

#### 3. 동시성 테스트 (PointServiceConcurrencyTest)
- ✅ 10개 스레드 동시 충전
- ✅ 50개 스레드 동시 충전 (대용량)
- ✅ 10개 스레드 동시 사용
- ✅ 잔액 부족 시 일부만 성공
- ✅ 충전과 사용 동시 발생
- ✅ 여러 사용자 동시 작업 (Lock 격리 검증)

---

## 프로젝트 구조

```
src/
├── main/
│   └── java/io/hhplus/tdd/
│       ├── TddApplication.java              # Spring Boot 메인 클래스
│       ├── ApiControllerAdvice.java         # 전역 예외 처리
│       ├── ErrorResponse.java               # 에러 응답 DTO
│       ├── database/                        # 영속성 레이어
│       │   ├── PointHistoryTable.java       # 히스토리 데이터 접근
│       │   └── UserPointTable.java          # 포인트 데이터 접근
│       └── point/                           # 포인트 도메인
│           ├── PointController.java         # REST API 컨트롤러
│           ├── PointService.java            # 비즈니스 로직 + 동시성 제어
│           ├── UserPoint.java               # 사용자 포인트 도메인 객체
│           ├── PointHistory.java            # 포인트 히스토리 도메인 객체
│           └── TransactionType.java         # 트랜잭션 타입 (CHARGE/USE)
└── test/
    └── java/io/hhplus/tdd/point/
        ├── PointHistoryTest.java            # 도메인 객체 테스트
        ├── PointControllerTest.java         # Controller 테스트
        ├── PointServiceExceptionTest.java   # 예외 케이스 테스트
        ├── PointFeatureIntegrationTest.java # 기능별 통합 테스트
        └── PointServiceConcurrencyTest.java # 동시성 테스트
```

---

## 실행 방법

### 1. 프로젝트 빌드

```bash
./gradlew build
```

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

### 3. 테스트 실행

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트 클래스만 실행
./gradlew test --tests "PointServiceConcurrencyTest"

# 테스트 캐시 무시하고 재실행
./gradlew test --rerun-tasks

# 클린 빌드 후 테스트
./gradlew clean test
```

### 4. 코드 커버리지 확인

```bash
./gradlew jacocoTestReport

# 리포트 확인 (브라우저로 열기)
open build/reports/jacoco/test/html/index.html
```

### 5. API 테스트 (애플리케이션 실행 후)

```bash
# 포인트 조회
curl http://localhost:8080/point/1

# 포인트 충전
curl -X PATCH http://localhost:8080/point/1/charge \
  -H "Content-Type: application/json" \
  -d "1000"

# 포인트 사용
curl -X PATCH http://localhost:8080/point/1/use \
  -H "Content-Type: application/json" \
  -d "500"

# 히스토리 조회
curl http://localhost:8080/point/1/histories
```

---

## 학습 포인트

이 프로젝트를 통해 다음을 학습할 수 있습니다:

1. **TDD 방법론**
   - Red-Green-Refactor 사이클
   - Given-When-Then 패턴
   - 테스트 가독성 향상 (@Nested, @DisplayName)

2. **동시성 제어**
   - Race Condition 이해 및 해결
   - ReentrantLock vs synchronized
   - 사용자별 Lock 패턴
   - ConcurrentHashMap 활용

3. **테스트 전략**
   - 단위 테스트 vs 통합 테스트
   - Mock 객체 사용 (Mockito)
   - 동시성 테스트 작성 (CountDownLatch, ExecutorService)

4. **Spring Boot**
   - REST API 설계
   - 전역 예외 처리 (@RestControllerAdvice)
   - 의존성 주입 (Constructor Injection)

5. **클린 코드**
   - 상세한 주석 작성
   - 명확한 변수명/메서드명
   - 책임 분리 (Controller-Service-Repository 패턴)

---

## 라이선스

이 프로젝트는 학습 목적으로 작성되었습니다.

---

## 작성자

- TDD 및 동시성 제어 구현
- 모든 코드에 한줄씩 상세한 주석 추가
- 80+ 통합 테스트 작성
