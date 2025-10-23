package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    /**
     * 특정 유저의 포인트를 조회합니다.
     */
    public UserPoint getUserPoint(long userId) {
        return userPointTable.selectById(userId);
    }

    /**
     * 특정 유저의 포인트 충전/사용 내역을 조회합니다.
     */
    public List<PointHistory> getPointHistories(long userId) {
        return pointHistoryTable.selectAllByUserId(userId);
    }

    /**
     * 특정 유저의 포인트를 충전합니다.
     */
    public UserPoint chargePoint(long userId, long amount) {
        validateAmount(amount);

        UserPoint currentPoint = userPointTable.selectById(userId);
        long newPoint = currentPoint.point() + amount;

        // 포인트 업데이트
        UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);

        // 히스토리 기록
        pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, updatedPoint.updateMillis());

        return updatedPoint;
    }

    /**
     * 특정 유저의 포인트를 사용합니다.
     */
    public UserPoint usePoint(long userId, long amount) {
        validateAmount(amount);

        UserPoint currentPoint = userPointTable.selectById(userId);

        // 잔액 확인
        if (currentPoint.point() < amount) {
            throw new IllegalArgumentException("포인트 잔액이 부족합니다.");
        }

        long newPoint = currentPoint.point() - amount;

        // 포인트 업데이트
        UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);

        // 히스토리 기록
        pointHistoryTable.insert(userId, amount, TransactionType.USE, updatedPoint.updateMillis());

        return updatedPoint;
    }

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("포인트는 0보다 커야 합니다.");
        }
    }
}