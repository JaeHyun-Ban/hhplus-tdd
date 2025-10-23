package io.hhplus.tdd.point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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

            assertThat(history.amount())
                    .as("금액이 생성자 인자와 같아야 함")
                    .isEqualTo(chargeAmount);

            assertThat(history.type())
                    .as("트랜잭션 타입이 CHARGE여야 함")
                    .isEqualTo(TransactionType.CHARGE);

            assertThat(history.updateMillis())
                    .as("타임스탬프가 생성자 인자와 같아야 함")
                    .isEqualTo(timestamp);
        }

        @Test
        @DisplayName("🟢 사용 타입 히스토리를 생성한다")
        void createUseHistory() {
            // ===== Given (준비) =====
            // 사용 히스토리에 필요한 데이터 준비
            long historyId = 2L;
            long userId = 100L;
            long useAmount = 500L;
            TransactionType useType = TransactionType.USE;
            long timestamp = System.currentTimeMillis();

            // ===== When (실행) =====
            // PointHistory 객체 생성
            PointHistory history = new PointHistory(
                    historyId,
                    userId,
                    useAmount,
                    useType,
                    timestamp
            );

            // ===== Then (검증) =====
            // 모든 필드 검증
            assertThat(history.id()).isEqualTo(historyId);
            assertThat(history.userId()).isEqualTo(userId);
            assertThat(history.amount()).isEqualTo(useAmount);
            assertThat(history.type())
                    .as("트랜잭션 타입이 USE여야 함")
                    .isEqualTo(TransactionType.USE);
            assertThat(history.updateMillis()).isEqualTo(timestamp);
        }
    }

    /**
     * 🔴 RED 단계: Record의 동등성 테스트
     *
     * 목적: Java record의 equals() 메서드가 올바르게 작동하는지 검증
     * Record는 자동으로 equals(), hashCode(), toString()을 생성함
     */
    @Nested
    @DisplayName("동등성 검증")
    class Equality {

        @Test
        @DisplayName("🟢 같은 값을 가진 두 히스토리는 동등하다")
        void equalHistories() {
            // ===== Given (준비) =====
            // 완전히 동일한 값으로 두 개의 PointHistory 생성
            long timestamp = System.currentTimeMillis();

            PointHistory history1 = new PointHistory(
                    1L,          // id
                    100L,        // userId
                    1000L,       // amount
                    TransactionType.CHARGE,
                    timestamp
            );

            PointHistory history2 = new PointHistory(
                    1L,          // 동일한 id
                    100L,        // 동일한 userId
                    1000L,       // 동일한 amount
                    TransactionType.CHARGE,  // 동일한 type
                    timestamp    // 동일한 timestamp
            );

            // ===== When & Then (실행 및 검증) =====
            // Record는 모든 필드가 같으면 equals()가 true를 반환해야 함
            assertThat(history1)
                    .as("모든 필드가 같은 두 히스토리는 동등해야 함")
                    .isEqualTo(history2);

            // hashCode도 같아야 함
            assertThat(history1.hashCode())
                    .as("동등한 객체는 같은 hashCode를 가져야 함")
                    .isEqualTo(history2.hashCode());
        }

        @Test
        @DisplayName("🟢 다른 TransactionType을 가진 히스토리는 다르다")
        void differentTransactionTypes() {
            // ===== Given (준비) =====
            // TransactionType만 다른 두 히스토리 생성
            long timestamp = System.currentTimeMillis();

            PointHistory chargeHistory = new PointHistory(
                    1L,
                    100L,
                    1000L,
                    TransactionType.CHARGE,  // 충전
                    timestamp
            );

            PointHistory useHistory = new PointHistory(
                    1L,
                    100L,
                    1000L,
                    TransactionType.USE,     // 사용 (다름!)
                    timestamp
            );

            // ===== When & Then (실행 및 검증) =====
            // TransactionType이 다르면 다른 객체로 판단되어야 함
            assertThat(chargeHistory)
                    .as("트랜잭션 타입이 다르면 다른 히스토리여야 함")
                    .isNotEqualTo(useHistory);
        }

        @Test
        @DisplayName("🟢 다른 금액을 가진 히스토리는 다르다")
        void differentAmounts() {
            // ===== Given (준비) =====
            // amount만 다른 두 히스토리
            long timestamp = System.currentTimeMillis();

            PointHistory history1000 = new PointHistory(
                    1L, 100L, 1000L, TransactionType.CHARGE, timestamp
            );

            PointHistory history500 = new PointHistory(
                    1L, 100L, 500L, TransactionType.CHARGE, timestamp  // 금액만 다름
            );

            // ===== When & Then (실행 및 검증) =====
            assertThat(history1000)
                    .as("금액이 다르면 다른 히스토리여야 함")
                    .isNotEqualTo(history500);
        }
    }

    /**
     * 🔴 RED 단계: Record의 불변성 테스트
     *
     * 목적: Record는 불변(immutable) 객체임을 검증
     * Record는 setter가 없고 모든 필드가 final
     */
    @Nested
    @DisplayName("불변성 검증")
    class Immutability {

        @Test
        @DisplayName("🟢 생성 후 값을 변경할 수 없다 (getter만 존재)")
        void immutableRecord() {
            // ===== Given (준비) =====
            long historyId = 1L;
            long userId = 100L;
            long amount = 1000L;
            TransactionType type = TransactionType.CHARGE;
            long timestamp = System.currentTimeMillis();

            // ===== When (실행) =====
            PointHistory history = new PointHistory(
                    historyId, userId, amount, type, timestamp
            );

            // ===== Then (검증) =====
            // Record는 불변이므로 생성 시점의 값이 그대로 유지됨
            assertThat(history.id()).isEqualTo(historyId);
            assertThat(history.userId()).isEqualTo(userId);
            assertThat(history.amount()).isEqualTo(amount);
            assertThat(history.type()).isEqualTo(type);
            assertThat(history.updateMillis()).isEqualTo(timestamp);

            // 참고: Record에는 setter가 없으므로
            // history.setAmount(2000L); // 컴파일 에러!
            // history.setType(TransactionType.USE); // 컴파일 에러!
        }

        @Test
        @DisplayName("🟢 toString()이 자동으로 생성된다")
        void autoGeneratedToString() {
            // ===== Given (준비) =====
            PointHistory history = new PointHistory(
                    1L, 100L, 1000L, TransactionType.CHARGE, 123456789L
            );

            // ===== When (실행) =====
            String result = history.toString();

            // ===== Then (검증) =====
            // Record는 자동으로 모든 필드를 포함하는 toString() 생성
            assertThat(result)
                    .as("toString()에 모든 필드 정보가 포함되어야 함")
                    .contains("1", "100", "1000", "CHARGE", "123456789");
        }
    }

    /**
     * 🔧 REFACTOR 단계
     *
     * 리팩토링 포인트:
     * 1. ✅ @Nested로 테스트 그룹화 (가독성 향상)
     * 2. ✅ 상세한 주석으로 TDD 각 단계 명확화
     * 3. ✅ assertThat().as()로 실패 메시지 명확화
     * 4. ✅ 테스트 이름을 더 명확하게 개선
     */
}
