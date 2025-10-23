package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DisplayName("PointService 통합 테스트")
class PointServiceIntegrationTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private UserPointTable userPointTable;

    @Autowired
    private PointHistoryTable pointHistoryTable;

    private static long testUserId = 1L;

    @BeforeEach
    void setUp() {
        testUserId++;
    }

    @Test
    @DisplayName("신규 유저의 포인트를 조회하면 0포인트가 반환된다")
    void getNewUserPoint() {
        // given
        long userId = testUserId;

        // when
        UserPoint result = pointService.getUserPoint(userId);

        // then
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(0L);
    }

    @Test
    @DisplayName("포인트를 충전하고 조회하면 충전된 포인트가 조회된다")
    void chargeAndGetPoint() {
        // given
        long userId = testUserId;
        long chargeAmount = 1000L;

        // when
        pointService.chargePoint(userId, chargeAmount);
        UserPoint result = pointService.getUserPoint(userId);

        // then
        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.point()).isEqualTo(chargeAmount);
    }

    @Test
    @DisplayName("포인트를 여러번 충전하면 누적된다")
    void chargeMultipleTimes() {
        // given
        long userId = testUserId;

        // when
        pointService.chargePoint(userId, 1000L);
        pointService.chargePoint(userId, 500L);
        pointService.chargePoint(userId, 300L);
        UserPoint result = pointService.getUserPoint(userId);

        // then
        assertThat(result.point()).isEqualTo(1800L);
    }

    @Test
    @DisplayName("포인트를 충전하고 사용하면 차감된 포인트가 조회된다")
    void chargeAndUsePoint() {
        // given
        long userId = testUserId;
        long chargeAmount = 1000L;
        long useAmount = 300L;

        // when
        pointService.chargePoint(userId, chargeAmount);
        pointService.usePoint(userId, useAmount);
        UserPoint result = pointService.getUserPoint(userId);

        // then
        assertThat(result.point()).isEqualTo(700L);
    }

    @Test
    @DisplayName("포인트 충전과 사용 내역이 모두 조회된다")
    void getPointHistories() {
        // given
        long userId = testUserId;

        // when
        pointService.chargePoint(userId, 1000L);
        pointService.chargePoint(userId, 500L);
        pointService.usePoint(userId, 300L);
        List<PointHistory> histories = pointService.getPointHistories(userId);

        // then
        assertThat(histories).hasSize(3);
        assertThat(histories.get(0).type()).isEqualTo(TransactionType.CHARGE);
        assertThat(histories.get(0).amount()).isEqualTo(1000L);
        assertThat(histories.get(1).type()).isEqualTo(TransactionType.CHARGE);
        assertThat(histories.get(1).amount()).isEqualTo(500L);
        assertThat(histories.get(2).type()).isEqualTo(TransactionType.USE);
        assertThat(histories.get(2).amount()).isEqualTo(300L);
    }

    @Test
    @DisplayName("잔액보다 많은 포인트를 사용하면 예외가 발생한다")
    void usePointExceedsBalance() {
        // given
        long userId = testUserId;
        pointService.chargePoint(userId, 1000L);

        // when & then
        assertThatThrownBy(() -> pointService.usePoint(userId, 1500L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("포인트 잔액이 부족합니다.");
    }

    @Test
    @DisplayName("잔액이 0인 상태에서 포인트를 사용하면 예외가 발생한다")
    void usePointWithZeroBalance() {
        // given
        long userId = testUserId;

        // when & then
        assertThatThrownBy(() -> pointService.usePoint(userId, 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("포인트 잔액이 부족합니다.");
    }

    @Test
    @DisplayName("0원을 충전하면 예외가 발생한다")
    void chargeZeroPoint() {
        // given
        long userId = testUserId;

        // when & then
        assertThatThrownBy(() -> pointService.chargePoint(userId, 0L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("포인트는 0보다 커야 합니다.");
    }

    @Test
    @DisplayName("음수 포인트를 충전하면 예외가 발생한다")
    void chargeNegativePoint() {
        // given
        long userId = testUserId;

        // when & then
        assertThatThrownBy(() -> pointService.chargePoint(userId, -500L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("포인트는 0보다 커야 합니다.");
    }

    @Test
    @DisplayName("여러 유저의 포인트는 독립적으로 관리된다")
    void multipleUsersIndependentPoints() {
        // given
        long userId1 = testUserId;
        testUserId++; // 다음 사용자 ID 증가
        long userId2 = testUserId;

        // when
        pointService.chargePoint(userId1, 1000L);
        pointService.chargePoint(userId2, 2000L);
        pointService.usePoint(userId1, 300L);

        UserPoint user1Point = pointService.getUserPoint(userId1);
        UserPoint user2Point = pointService.getUserPoint(userId2);

        // then
        assertThat(user1Point.point()).isEqualTo(700L);
        assertThat(user2Point.point()).isEqualTo(2000L);
    }

    @Test
    @DisplayName("포인트 전액 사용 후 잔액은 0이 된다")
    void useAllPoints() {
        // given
        long userId = testUserId;
        long chargeAmount = 1000L;

        // when
        pointService.chargePoint(userId, chargeAmount);
        pointService.usePoint(userId, chargeAmount);
        UserPoint result = pointService.getUserPoint(userId);

        // then
        assertThat(result.point()).isEqualTo(0L);
    }
}