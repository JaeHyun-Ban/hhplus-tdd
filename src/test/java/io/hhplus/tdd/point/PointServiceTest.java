package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PointService 테스트")
class PointServiceTest {

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @InjectMocks
    private PointService pointService;

    @Test
    @DisplayName("유저 포인트를 조회한다")
    void getUserPoint() {
        // given
        long userId = 1L;
        UserPoint expectedPoint = new UserPoint(userId, 1000L, System.currentTimeMillis());
        given(userPointTable.selectById(userId)).willReturn(expectedPoint);

        // when
        UserPoint result = pointService.getUserPoint(userId);

        // then
        assertThat(result).isEqualTo(expectedPoint);
        verify(userPointTable).selectById(userId);
    }

    @Test
    @DisplayName("유저의 포인트 히스토리를 조회한다")
    void getPointHistories() {
        // given
        long userId = 1L;
        List<PointHistory> expectedHistories = List.of(
                new PointHistory(1L, userId, 1000L, TransactionType.CHARGE, System.currentTimeMillis()),
                new PointHistory(2L, userId, 500L, TransactionType.USE, System.currentTimeMillis())
        );
        given(pointHistoryTable.selectAllByUserId(userId)).willReturn(expectedHistories);

        // when
        List<PointHistory> result = pointService.getPointHistories(userId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).isEqualTo(expectedHistories);
        verify(pointHistoryTable).selectAllByUserId(userId);
    }

    @Test
    @DisplayName("포인트를 충전한다")
    void chargePoint() {
        // given
        long userId = 1L;
        long currentAmount = 1000L;
        long chargeAmount = 500L;
        long expectedAmount = 1500L;

        UserPoint currentPoint = new UserPoint(userId, currentAmount, System.currentTimeMillis());
        UserPoint updatedPoint = new UserPoint(userId, expectedAmount, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(currentPoint);
        given(userPointTable.insertOrUpdate(eq(userId), eq(expectedAmount))).willReturn(updatedPoint);

        // when
        UserPoint result = pointService.chargePoint(userId, chargeAmount);

        // then
        assertThat(result.point()).isEqualTo(expectedAmount);
        verify(userPointTable).selectById(userId);
        verify(userPointTable).insertOrUpdate(userId, expectedAmount);
        verify(pointHistoryTable).insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    @DisplayName("0 포인트 충전 시 예외가 발생한다")
    void chargeZeroPoint() {
        // given
        long userId = 1L;
        long chargeAmount = 0L;

        // when & then
        assertThatThrownBy(() -> pointService.chargePoint(userId, chargeAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("포인트는 0보다 커야 합니다.");
    }

    @Test
    @DisplayName("음수 포인트 충전 시 예외가 발생한다")
    void chargeNegativePoint() {
        // given
        long userId = 1L;
        long chargeAmount = -500L;

        // when & then
        assertThatThrownBy(() -> pointService.chargePoint(userId, chargeAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("포인트는 0보다 커야 합니다.");
    }

    @Test
    @DisplayName("포인트를 사용한다")
    void usePoint() {
        // given
        long userId = 1L;
        long currentAmount = 1000L;
        long useAmount = 500L;
        long expectedAmount = 500L;

        UserPoint currentPoint = new UserPoint(userId, currentAmount, System.currentTimeMillis());
        UserPoint updatedPoint = new UserPoint(userId, expectedAmount, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(currentPoint);
        given(userPointTable.insertOrUpdate(eq(userId), eq(expectedAmount))).willReturn(updatedPoint);

        // when
        UserPoint result = pointService.usePoint(userId, useAmount);

        // then
        assertThat(result.point()).isEqualTo(expectedAmount);
        verify(userPointTable).selectById(userId);
        verify(userPointTable).insertOrUpdate(userId, expectedAmount);
        verify(pointHistoryTable).insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong());
    }

    @Test
    @DisplayName("잔액보다 많은 포인트 사용 시 예외가 발생한다")
    void usePointExceedsBalance() {
        // given
        long userId = 1L;
        long currentAmount = 1000L;
        long useAmount = 1500L;

        UserPoint currentPoint = new UserPoint(userId, currentAmount, System.currentTimeMillis());
        given(userPointTable.selectById(userId)).willReturn(currentPoint);

        // when & then
        assertThatThrownBy(() -> pointService.usePoint(userId, useAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("포인트 잔액이 부족합니다.");
    }

    @Test
    @DisplayName("0 포인트 사용 시 예외가 발생한다")
    void useZeroPoint() {
        // given
        long userId = 1L;
        long useAmount = 0L;

        // when & then
        assertThatThrownBy(() -> pointService.usePoint(userId, useAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("포인트는 0보다 커야 합니다.");
    }

    @Test
    @DisplayName("음수 포인트 사용 시 예외가 발생한다")
    void useNegativePoint() {
        // given
        long userId = 1L;
        long useAmount = -500L;

        // when & then
        assertThatThrownBy(() -> pointService.usePoint(userId, useAmount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("포인트는 0보다 커야 합니다.");
    }

    @Test
    @DisplayName("포인트 충전 후 히스토리가 기록된다")
    void chargePointHistory() {
        // given
        long userId = 1L;
        long currentAmount = 1000L;
        long chargeAmount = 500L;
        long expectedAmount = 1500L;

        UserPoint currentPoint = new UserPoint(userId, currentAmount, System.currentTimeMillis());
        UserPoint updatedPoint = new UserPoint(userId, expectedAmount, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(currentPoint);
        given(userPointTable.insertOrUpdate(eq(userId), eq(expectedAmount))).willReturn(updatedPoint);

        // when
        pointService.chargePoint(userId, chargeAmount);

        // then
        verify(pointHistoryTable).insert(eq(userId), eq(chargeAmount), eq(TransactionType.CHARGE), anyLong());
    }

    @Test
    @DisplayName("포인트 사용 후 히스토리가 기록된다")
    void usePointHistory() {
        // given
        long userId = 1L;
        long currentAmount = 1000L;
        long useAmount = 500L;
        long expectedAmount = 500L;

        UserPoint currentPoint = new UserPoint(userId, currentAmount, System.currentTimeMillis());
        UserPoint updatedPoint = new UserPoint(userId, expectedAmount, System.currentTimeMillis());

        given(userPointTable.selectById(userId)).willReturn(currentPoint);
        given(userPointTable.insertOrUpdate(eq(userId), eq(expectedAmount))).willReturn(updatedPoint);

        // when
        pointService.usePoint(userId, useAmount);

        // then
        verify(pointHistoryTable).insert(eq(userId), eq(useAmount), eq(TransactionType.USE), anyLong());
    }
}