package io.hhplus.tdd.point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(history.amount()).isEqualTo(amount);
        assertThat(history.type()).isEqualTo(TransactionType.CHARGE);
        assertThat(history.updateMillis()).isEqualTo(updateMillis);
    }

    @Test
    @DisplayName("사용 히스토리 생성 테스트")
    void createUseHistory() {
        // given
        long id = 2L;
        long userId = 100L;
        long amount = 500L;
        TransactionType type = TransactionType.USE;
        long updateMillis = System.currentTimeMillis();

        // when
        PointHistory history = new PointHistory(id, userId, amount, type, updateMillis);

        // then
        assertThat(history.id()).isEqualTo(id);
        assertThat(history.userId()).isEqualTo(userId);
        assertThat(history.amount()).isEqualTo(amount);
        assertThat(history.type()).isEqualTo(TransactionType.USE);
        assertThat(history.updateMillis()).isEqualTo(updateMillis);
    }

    @Test
    @DisplayName("PointHistory 동등성 테스트")
    void pointHistoryEquality() {
        // given
        long updateMillis = System.currentTimeMillis();
        PointHistory history1 = new PointHistory(1L, 100L, 1000L, TransactionType.CHARGE, updateMillis);
        PointHistory history2 = new PointHistory(1L, 100L, 1000L, TransactionType.CHARGE, updateMillis);

        // when & then
        assertThat(history1).isEqualTo(history2);
    }

    @Test
    @DisplayName("다른 TransactionType을 가진 히스토리는 다르다")
    void differentTransactionType() {
        // given
        long updateMillis = System.currentTimeMillis();
        PointHistory chargeHistory = new PointHistory(1L, 100L, 1000L, TransactionType.CHARGE, updateMillis);
        PointHistory useHistory = new PointHistory(1L, 100L, 1000L, TransactionType.USE, updateMillis);

        // when & then
        assertThat(chargeHistory).isNotEqualTo(useHistory);
    }
}