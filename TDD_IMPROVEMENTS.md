# TDD 형식으로 테스트 코드 개선

## 🔄 개선 전후 비교

### ❌ 개선 전 (일반 테스트)

```java
@DisplayName("PointHistory 테스트")
class PointHistoryTest {

    @Test
    @DisplayName("충전 히스토리 생성 테스트")
    void createChargeHistory() {
        // given
        long id = 1L;
        long userId = 100L;
        long amount = 1000L;
        TransactionType type = TransactionType.CHARGE;
        long updateMillis = System.currentTimeMillis();

        // when
        PointHistory history = new PointHistory(id, userId, amount, type, updateMillis);

        // then
        assertThat(history.id()).isEqualTo(id);
        assertThat(history.userId()).isEqualTo(userId);
        // ... 기타 검증
    }
}
```

**문제점:**
- ❌ TDD 사이클이 명확하지 않음
- ❌ 테스트 의도가 불분명
- ❌ 실패 시 원인 파악이 어려움
- ❌ 테스트 간 관계가 보이지 않음

---

### ✅ 개선 후 (TDD 형식)

```java
/**
 * PointHistory 도메인 객체 TDD 테스트
 *
 * TDD 사이클:
 * 🔴 RED: 테스트 작성 (실패)
 * 🟢 GREEN: 최소한의 코드로 테스트 통과
 * 🔧 REFACTOR: 코드 개선
 */
@DisplayName("PointHistory 도메인 테스트 (TDD)")
class PointHistoryTest {

    /**
     * 🔴 RED 단계: PointHistory 생성 테스트
     *
     * 목적: PointHistory record가 올바르게 생성되는지 검증
     * 예상 동작: 모든 필드가 생성자 인자와 동일하게 설정됨
     */
    @Nested
    @DisplayName("포인트 히스토리 생성")
    class CreatePointHistory {

        @Test
        @DisplayName("🟢 충전 타입 히스토리를 생성한다")
        void createChargeHistory() {
            // ===== Given (준비) =====
            // 충전 히스토리에 필요한 데이터 준비
            long historyId = 1L;
            long userId = 100L;
            long chargeAmount = 1000L;
            TransactionType chargeType = TransactionType.CHARGE;
            long timestamp = System.currentTimeMillis();

            // ===== When (실행) =====
            // PointHistory 객체 생성 (record 생성자 호출)
            PointHistory history = new PointHistory(
                    historyId,
                    userId,
                    chargeAmount,
                    chargeType,
                    timestamp
            );

            // ===== Then (검증) =====
            // 생성된 객체의 모든 필드가 예상값과 일치하는지 확인
            assertThat(history.id())
                    .as("히스토리 ID가 생성자 인자와 같아야 함")
                    .isEqualTo(historyId);

            assertThat(history.userId())
                    .as("유저 ID가 생성자 인자와 같아야 함")
                    .isEqualTo(userId);

            // ... 기타 검증
        }
    }
}
```

**개선 사항:**
- ✅ TDD 사이클을 명확히 표시 (🔴 RED, 🟢 GREEN, 🔧 REFACTOR)
- ✅ `@Nested`로 테스트를 논리적으로 그룹화
- ✅ 상세한 주석으로 각 단계의 목적 설명
- ✅ `.as()`로 실패 시 명확한 메시지 제공
- ✅ Given-When-Then 구분을 시각적으로 명확히

---

## 🎯 TDD 형식의 핵심 원칙

### 1. 🔴 RED 단계: 실패하는 테스트 먼저 작성

```java
/**
 * 🔴 RED 단계: 잔액 부족 시 예외 발생 테스트
 *
 * 목적: 포인트가 부족할 때 적절한 예외를 던지는지 검증
 * 현재 상태: 아직 구현되지 않음 (테스트 실패 예상)
 */
@Test
@DisplayName("🔴 잔액 부족 시 예외가 발생해야 한다")
void shouldThrowExceptionWhenInsufficientBalance() {
    // given
    long userId = 1L;
    long balance = 500L;
    long useAmount = 1000L;

    // when & then
    assertThatThrownBy(() -> pointService.usePoint(userId, useAmount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("포인트 잔액이 부족합니다.");
}
```

**이 단계에서:**
- ❌ 테스트 실행 → **실패** (기능이 없으므로)
- ✅ 요구사항을 테스트로 명확히 정의
- ✅ "무엇을 만들어야 하는가?"가 명확해짐

---

### 2. 🟢 GREEN 단계: 최소한의 코드로 테스트 통과

```java
/**
 * 🟢 GREEN 단계: 테스트를 통과시키는 최소한의 구현
 */
public UserPoint usePoint(long userId, long amount) {
    UserPoint current = userPointTable.selectById(userId);

    // 테스트를 통과시키기 위한 최소 구현
    if (current.point() < amount) {
        throw new IllegalArgumentException("포인트 잔액이 부족합니다.");
    }

    // ... 포인트 차감 로직
    return updatedPoint;
}
```

**이 단계에서:**
- ✅ 테스트 실행 → **성공**
- ✅ 과도한 설계 없이 간단하게 구현
- ✅ "일단 동작하게 만든다"

---

### 3. 🔧 REFACTOR 단계: 코드 개선

```java
/**
 * 🔧 REFACTOR 단계: 중복 제거 및 코드 품질 개선
 */

// Before (중복)
public UserPoint chargePoint(long userId, long amount) {
    if (amount <= 0) {
        throw new IllegalArgumentException("포인트는 0보다 커야 합니다.");
    }
    // ...
}

public UserPoint usePoint(long userId, long amount) {
    if (amount <= 0) {
        throw new IllegalArgumentException("포인트는 0보다 커야 합니다.");
    }
    // ...
}

// After (리팩토링)
public UserPoint chargePoint(long userId, long amount) {
    validateAmount(amount);  // 중복 제거
    // ...
}

public UserPoint usePoint(long userId, long amount) {
    validateAmount(amount);  // 중복 제거
    // ...
}

private void validateAmount(long amount) {
    if (amount <= 0) {
        throw new IllegalArgumentException("포인트는 0보다 커야 합니다.");
    }
}
```

**이 단계에서:**
- ✅ 테스트는 여전히 통과
- ✅ 중복 제거
- ✅ 가독성 향상
- ✅ 유지보수성 개선

---

## 📊 TDD 개선 효과

### 1. 테스트 가독성 향상

| 항목 | 개선 전 | 개선 후 |
|------|---------|---------|
| **구조** | 평면적 | @Nested로 계층화 |
| **주석** | 간단한 주석 | 상세한 TDD 사이클 설명 |
| **실패 메시지** | 기본 메시지 | `.as()`로 명확한 메시지 |
| **단계 구분** | 주석만 | 시각적 구분선 (=====) |

### 2. 테스트 실행 결과

```
PointHistory 도메인 테스트 (TDD)
  포인트 히스토리 생성
    ✅ 🟢 충전 타입 히스토리를 생성한다
    ✅ 🟢 사용 타입 히스토리를 생성한다
  동등성 검증
    ✅ 🟢 같은 값을 가진 두 히스토리는 동등하다
    ✅ 🟢 다른 TransactionType을 가진 히스토리는 다르다
    ✅ 🟢 다른 금액을 가진 히스토리는 다르다
  불변성 검증
    ✅ 🟢 생성 후 값을 변경할 수 없다 (getter만 존재)
    ✅ 🟢 toString()이 자동으로 생성된다
```

**장점:**
- ✅ 테스트 구조가 한눈에 보임
- ✅ 각 테스트의 목적이 명확함
- ✅ TDD 단계(RED/GREEN/REFACTOR)를 이모지로 표시

---

## 🔍 주요 개선 포인트

### 1. @Nested를 활용한 테스트 그룹화

```java
@DisplayName("PointHistory 도메인 테스트 (TDD)")
class PointHistoryTest {

    @Nested
    @DisplayName("포인트 히스토리 생성")
    class CreatePointHistory {
        // 생성 관련 테스트
    }

    @Nested
    @DisplayName("동등성 검증")
    class Equality {
        // 동등성 관련 테스트
    }

    @Nested
    @DisplayName("불변성 검증")
    class Immutability {
        // 불변성 관련 테스트
    }
}
```

**효과:**
- 관련 테스트를 논리적으로 그룹화
- 테스트 리포트에서 계층 구조로 표시
- 새로운 테스트 추가 시 어디에 넣을지 명확

---

### 2. 명확한 Given-When-Then 구분

```java
@Test
void example() {
    // ===== Given (준비) =====
    // 이 구분선으로 시각적 구분
    long userId = 1L;

    // ===== When (실행) =====
    // 실제 테스트할 동작
    UserPoint result = service.getUserPoint(userId);

    // ===== Then (검증) =====
    // 결과 검증
    assertThat(result).isNotNull();
}
```

**효과:**
- 테스트의 흐름이 명확
- 각 단계의 역할이 분명
- 코드 리뷰 시 이해하기 쉬움

---

### 3. assertThat().as()로 실패 메시지 명확화

```java
// Before (기본 메시지)
assertThat(history.id()).isEqualTo(historyId);
// 실패 시: Expected: 1L but was: 2L

// After (명확한 메시지)
assertThat(history.id())
    .as("히스토리 ID가 생성자 인자와 같아야 함")
    .isEqualTo(historyId);
// 실패 시: [히스토리 ID가 생성자 인자와 같아야 함] Expected: 1L but was: 2L
```

**효과:**
- 테스트 실패 시 원인을 즉시 파악
- 디버깅 시간 단축
- 테스트 의도가 명확

---

### 4. TDD 사이클을 이모지로 표시

```java
/**
 * 🔴 RED: 테스트 먼저 작성
 * 🟢 GREEN: 구현
 * 🔧 REFACTOR: 개선
 */

@Test
@DisplayName("🔴 아직 구현되지 않은 기능")
void notImplementedYet() { ... }

@Test
@DisplayName("🟢 구현 완료된 기능")
void implemented() { ... }
```

**효과:**
- 현재 TDD 단계를 한눈에 파악
- 어떤 테스트가 미완성인지 명확
- 팀원과 협업 시 진행 상황 공유 용이

---

## 📝 TDD 테스트 작성 가이드

### 1. 테스트 클래스 구조

```java
/**
 * 클래스 레벨 JavaDoc: TDD 사이클 설명
 */
@DisplayName("도메인명 테스트 (TDD)")
class DomainTest {

    /**
     * 그룹 레벨 JavaDoc: 이 그룹의 목적
     * 🔴 RED 단계 표시
     */
    @Nested
    @DisplayName("기능 그룹명")
    class FeatureGroup {

        @Test
        @DisplayName("🟢 구체적인 테스트 케이스")
        void testCase() {
            // ===== Given =====

            // ===== When =====

            // ===== Then =====
        }
    }
}
```

### 2. Given-When-Then 작성 규칙

```java
@Test
void example() {
    // ===== Given (준비) =====
    // 1. 테스트에 필요한 데이터 준비
    // 2. Mock 설정 (필요 시)
    // 3. 전제 조건 설정

    // ===== When (실행) =====
    // 1. 테스트할 메서드 호출
    // 2. 단 하나의 동작만 수행

    // ===== Then (검증) =====
    // 1. 결과 검증
    // 2. Mock 호출 검증 (필요 시)
    // 3. 부작용 검증 (필요 시)
}
```

### 3. 테스트 이름 규칙

```java
// ✅ 좋은 예
@Test
@DisplayName("🟢 잔액 부족 시 포인트 사용이 실패한다")
void usePointFailsWhenInsufficientBalance() { ... }

// ❌ 나쁜 예
@Test
@DisplayName("테스트1")
void test1() { ... }
```

**규칙:**
- 동사로 시작 (creates, validates, throws 등)
- 구체적인 시나리오 설명
- 한글 DisplayName 권장 (가독성)

---

## 🎓 TDD의 장점 재확인

### 1. 버그 사전 방지

```java
// 🔴 RED: 먼저 테스트 작성
@Test
void shouldNotAllowNegativeAmount() {
    assertThatThrownBy(() -> service.charge(1L, -100L))
        .isInstanceOf(IllegalArgumentException.class);
}

// 🟢 GREEN: 구현
public void charge(long userId, long amount) {
    if (amount <= 0) {  // 테스트가 강제한 검증
        throw new IllegalArgumentException();
    }
}
```

### 2. 리팩토링 안정성

```java
// 테스트가 있으므로 안전하게 리팩토링 가능
@Test
void chargePoint() {
    UserPoint result = service.chargePoint(1L, 100L);
    assertThat(result.point()).isEqualTo(100L);
}

// 내부 구현을 바꿔도 테스트가 보호
```

### 3. 문서 역할

```java
// 테스트 자체가 실행 가능한 문서
@DisplayName("포인트 충전 API")
class ChargePointTests {

    @Test
    @DisplayName("정상적으로 포인트를 충전한다")
    void chargePoint() { ... }

    @Test
    @DisplayName("음수 금액은 실패한다")
    void negativeAmount() { ... }
}
```

---

## 🚀 다음 단계

### 1. 나머지 테스트 파일에도 적용
- [ ] `UserPointTest.java`
- [ ] `PointServiceTest.java`
- [ ] `PointServiceIntegrationTest.java`
- [ ] `PointServiceConcurrencyTest.java`

### 2. 추가 개선 사항
- [ ] 테스트 데이터 빌더 패턴 적용
- [ ] Parameterized Test 활용
- [ ] Custom Assertion 작성

---

## 📚 참고 자료

- [Test-Driven Development by Kent Beck](https://www.amazon.com/Test-Driven-Development-Kent-Beck/dp/0321146530)
- [JUnit 5 Nested Tests](https://junit.org/junit5/docs/current/user-guide/#writing-tests-nested)
- [AssertJ .as() Documentation](https://assertj.github.io/doc/#assertj-core-assertion-description)

---

## ✅ 체크리스트

TDD 형식으로 테스트를 작성할 때:

- [x] 클래스 레벨에 TDD 사이클 설명 추가
- [x] @Nested로 테스트 그룹화
- [x] Given-When-Then 시각적 구분
- [x] .as()로 실패 메시지 명확화
- [x] 🔴🟢🔧 이모지로 TDD 단계 표시
- [x] 상세한 JavaDoc 주석
- [x] 테스트 이름을 구체적으로 작성
