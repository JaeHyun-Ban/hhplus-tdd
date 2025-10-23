package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 포인트 관리 서비스
 *
 * 동시성 제어:
 * - 사용자별 Lock을 사용하여 Race Condition 방지
 * - ConcurrentHashMap으로 Lock 관리의 Thread-Safety 보장
 * - ReentrantLock으로 공정성(fairness) 있는 Lock 획득 순서 보장
 */
@Service
public class PointService {

    // 포인트 데이터를 관리하는 테이블 (영속성 레이어)
    private final UserPointTable userPointTable;
    // 포인트 히스토리를 관리하는 테이블 (영속성 레이어)
    private final PointHistoryTable pointHistoryTable;
    // 사용자별 Lock을 관리하는 Thread-Safe Map
    // Key: userId, Value: 해당 사용자의 포인트 연산을 보호하는 Lock
    private final ConcurrentHashMap<Long, Lock> userLocks;

    /**
     * PointService 생성자
     *
     * @param userPointTable 유저 포인트 데이터 접근 객체
     * @param pointHistoryTable 포인트 히스토리 데이터 접근 객체
     */
    public PointService(UserPointTable userPointTable, PointHistoryTable pointHistoryTable) {
        this.userPointTable = userPointTable; // 유저 포인트 테이블 주입
        this.pointHistoryTable = pointHistoryTable; // 포인트 히스토리 테이블 주입
        this.userLocks = new ConcurrentHashMap<>(); // 사용자별 Lock Map 초기화
    }

    /**
     * 특정 사용자의 Lock을 가져옵니다.
     * Lock이 없으면 새로 생성합니다. (Lazy Initialization)
     *
     * @param userId 사용자 ID
     * @return 해당 사용자의 ReentrantLock
     */
    private Lock getUserLock(long userId) {
        // computeIfAbsent는 Thread-Safe하게 동작:
        // 1. userId에 해당하는 Lock이 있으면 반환
        // 2. 없으면 새 ReentrantLock(true) 생성 후 저장하고 반환
        // 3. true 파라미터는 공정성(fairness)을 의미 - 대기 시간이 긴 스레드가 우선권을 가짐
        return userLocks.computeIfAbsent(userId, id -> new ReentrantLock(true));
    }

    /**
     * 특정 유저의 포인트를 조회합니다.
     * 읽기 전용 작업이므로 Lock이 필요하지 않습니다.
     *
     * @param userId 조회할 사용자 ID
     * @return 사용자의 현재 포인트 정보
     */
    public UserPoint getUserPoint(long userId) {
        // 단순 조회는 동시성 문제가 없으므로 직접 반환
        return userPointTable.selectById(userId);
    }

    /**
     * 특정 유저의 포인트 충전/사용 내역을 조회합니다.
     * 읽기 전용 작업이므로 Lock이 필요하지 않습니다.
     *
     * @param userId 조회할 사용자 ID
     * @return 사용자의 포인트 히스토리 목록 (시간순 정렬)
     */
    public List<PointHistory> getPointHistories(long userId) {
        // 히스토리 조회도 읽기 전용이므로 직접 반환
        return pointHistoryTable.selectAllByUserId(userId);
    }

    /**
     * 특정 유저의 포인트를 충전합니다.
     * 동시성 제어: 사용자별 Lock으로 Race Condition 방지
     *
     * @param userId 충전할 사용자 ID
     * @param amount 충전할 포인트 금액
     * @return 충전 후 업데이트된 포인트 정보
     * @throws IllegalArgumentException amount가 0 이하인 경우
     */
    public UserPoint chargePoint(long userId, long amount) {
        // 1단계: 입력값 검증 (Lock 획득 전 수행하여 성능 향상)
        validateAmount(amount);

        // 2단계: 해당 사용자의 Lock 획득
        Lock lock = getUserLock(userId);
        lock.lock(); // Lock 획득 (다른 스레드는 여기서 대기)

        try {
            // 3단계: 현재 포인트 조회 (Critical Section 시작)
            UserPoint currentPoint = userPointTable.selectById(userId);

            // 4단계: 새로운 포인트 계산 (충전이므로 덧셈)
            long newPoint = currentPoint.point() + amount;

            // 5단계: 포인트 업데이트 (DB에 반영)
            UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);

            // 6단계: 히스토리 기록 (감사 추적을 위해)
            pointHistoryTable.insert(userId, amount, TransactionType.CHARGE, updatedPoint.updateMillis());

            // 7단계: 업데이트된 포인트 반환
            return updatedPoint;
        } finally {
            // 8단계: 반드시 Lock 해제 (예외 발생 시에도 실행됨)
            lock.unlock();
        }
    }

    /**
     * 특정 유저의 포인트를 사용합니다.
     * 동시성 제어: 사용자별 Lock으로 Race Condition 방지
     *
     * @param userId 포인트를 사용할 사용자 ID
     * @param amount 사용할 포인트 금액
     * @return 사용 후 업데이트된 포인트 정보
     * @throws IllegalArgumentException amount가 0 이하이거나 잔액 부족인 경우
     */
    public UserPoint usePoint(long userId, long amount) {
        // 1단계: 입력값 검증 (Lock 획득 전 수행)
        validateAmount(amount);

        // 2단계: 해당 사용자의 Lock 획득
        Lock lock = getUserLock(userId);
        lock.lock(); // Lock 획득 (다른 스레드는 여기서 대기)

        try {
            // 3단계: 현재 포인트 조회 (Critical Section 시작)
            UserPoint currentPoint = userPointTable.selectById(userId);

            // 4단계: 잔액 확인 (사용 금액이 현재 포인트보다 크면 예외)
            if (currentPoint.point() < amount) {
                throw new IllegalArgumentException("포인트 잔액이 부족합니다.");
            }

            // 5단계: 새로운 포인트 계산 (사용이므로 뺄셈)
            long newPoint = currentPoint.point() - amount;

            // 6단계: 포인트 업데이트 (DB에 반영)
            UserPoint updatedPoint = userPointTable.insertOrUpdate(userId, newPoint);

            // 7단계: 히스토리 기록 (감사 추적을 위해)
            pointHistoryTable.insert(userId, amount, TransactionType.USE, updatedPoint.updateMillis());

            // 8단계: 업데이트된 포인트 반환
            return updatedPoint;
        } finally {
            // 9단계: 반드시 Lock 해제 (예외 발생 시에도 실행됨)
            lock.unlock();
        }
    }

    /**
     * 포인트 금액의 유효성을 검증합니다.
     *
     * @param amount 검증할 금액
     * @throws IllegalArgumentException amount가 0 이하인 경우
     */
    private void validateAmount(long amount) {
        // 포인트는 반드시 양수여야 함
        if (amount <= 0) {
            throw new IllegalArgumentException("포인트는 0보다 커야 합니다.");
        }
    }
}