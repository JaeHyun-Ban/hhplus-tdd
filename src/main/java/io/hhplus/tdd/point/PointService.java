package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 포인트 관리 서비스
 *
 * 주요 기능:
 * - 포인트 조회/충전/사용
 * - 포인트 히스토리 관리
 * - 동시성 제어 (Race Condition 방지)
 */
@Service
public class PointService {

    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    /**
     * 유저별 Lock 관리
     * ConcurrentHashMap: 스레드 안전한 Map
     * ReentrantLock: 재진입 가능한 Lock (같은 스레드가 여러 번 락을 획득할 수 있음)
     *
     * 왜 유저별로 Lock을 분리하나?
     * - 전체 Lock: 모든 유저의 포인트 작업이 순차 처리 (느림)
     * - 유저별 Lock: 다른 유저는 동시 처리 가능 (빠름)
     */
    private final Map<Long, Lock> userLocks = new ConcurrentHashMap<>();

    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
    }

    /**
     * 유저별 Lock 획득
     * computeIfAbsent: key가 없으면 새로 생성, 있으면 기존 값 반환
     */
    private Lock getUserLock(long userId) {
        return userLocks.computeIfAbsent(userId, id -> new ReentrantLock());
    }

    /**
     * 특정 유저의 포인트를 조회합니다.
     * 조회는 읽기 작업이므로 Lock 불필요 (현재 구현에서는)
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
     *
     * 동시성 제어:
     * 1. 유저별 Lock 획득
     * 2. 포인트 조회 → 검증 → 충전 → 히스토리 기록
     * 3. Lock 해제
     *
     * Lock을 사용하는 이유:
     * - 두 스레드가 동시에 조회하면 같은 금액을 읽음
     * - 각자 충전 후 저장하면 한 번의 충전이 사라짐 (Lost Update)
     */
    public UserPoint chargePoint(long userId, long amount) {
        validateAmount(amount);

        Lock lock = getUserLock(userId);
        lock.lock(); // 🔒 Lock 획득 (다른 스레드는 대기)

        try {
            // Critical Section (임계 영역): 한 번에 한 스레드만 실행
            UserPoint currentPoint = userPointTable.selectById(userId);
            long newPoint = currentPoint.point() + amount;

            // 포인트 업데이트
            UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);

            // 히스토리 기록
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, updatedPoint.updateMillis());

            return updatedPoint;
        } finally {
            lock.unlock(); // 🔓 Lock 해제 (반드시 실행되도록 finally 사용)
        }
    }

    /**
     * 특정 유저의 포인트를 사용합니다.
     *
     * 동시성 제어가 중요한 이유:
     * 잔액 1000원, A와 B가 동시에 600원 사용 시도
     * - Lock 없음: 둘 다 1000원 확인 → 둘 다 성공 → 잔액 -200원 (문제!)
     * - Lock 있음: A만 성공 → 잔액 400원 → B는 실패 (정상!)
     */
    public UserPoint usePoint(long userId, long amount) {
        validateAmount(amount);

        Lock lock = getUserLock(userId);
        lock.lock(); // 🔒 Lock 획득

        try {
            UserPoint currentPoint = userPointTable.selectById(userId);

            // 잔액 확인 (Lock 안에서 해야 정확함)
            if (currentPoint.point() < amount) {
                throw new IllegalArgumentException("포인트 잔액이 부족합니다.");
            }

            long newPoint = currentPoint.point() - amount;

            // 포인트 업데이트
            UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);

            // 히스토리 기록
            pointHistoryTable.insert(userId, amount, TransactionType.USE, updatedPoint.updateMillis());

            return updatedPoint;
        } finally {
            lock.unlock(); // 🔓 Lock 해제
        }
    }

    /**
     * 금액 검증
     * - 0 이하의 금액은 허용하지 않음
     */
    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("포인트는 0보다 커야 합니다.");
        }
    }
}
