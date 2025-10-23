package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PointService의 동시성 제어 테스트
 *
 * 목적: 여러 스레드가 동시에 포인트를 충전/사용할 때
 *      Race Condition이 발생하지 않는지 검증
 *
 * Race Condition이란?
 * - 여러 스레드가 동시에 같은 데이터를 수정할 때 발생하는 문제
 * - 예: 잔액 1000원, A와 B가 동시에 500원씩 사용
 *   → 제대로 동작하면 두 번째는 실패해야 하지만,
 *     동시성 제어가 없으면 둘 다 성공해서 잔액이 -500원이 될 수 있음
 */
@SpringBootTest
@DisplayName("PointService 동시성 제어 테스트")
class PointServiceConcurrencyTest {

    @Autowired
    private PointService pointService;

    @Autowired
    private UserPointTable userPointTable;

    @Autowired
    private PointHistoryTable pointHistoryTable;

    /**
     * 동시에 여러 번 충전하는 테스트
     *
     * 시나리오:
     * 1. 초기 포인트 0
     * 2. 10개의 스레드가 동시에 100포인트씩 충전
     * 3. 최종 포인트는 1000이어야 함
     *
     * 예상되는 문제 (동시성 제어 없을 때):
     * - Thread 1: 포인트 조회 (0) → +100 → 저장 (100)
     * - Thread 2: 포인트 조회 (0) → +100 → 저장 (100)  ← 문제!
     * → 200이 되어야 하는데 100이 됨 (Lost Update)
     */
    @Test
    @DisplayName("동시에 여러 번 충전해도 정확한 금액이 충전된다")
    void concurrentCharge() throws InterruptedException {
        // given
        long userId = 1000L;
        int threadCount = 10; // 동시 실행할 스레드 수
        long chargeAmount = 100L; // 각 스레드가 충전할 금액
        long expectedFinalPoint = threadCount * chargeAmount; // 기대값: 1000

        // 스레드 동기화를 위한 도구
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when: 10개 스레드가 동시에 충전 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.chargePoint(userId, chargeAmount);
                } finally {
                    latch.countDown(); // 작업 완료 신호
                }
            });
        }

        // 모든 스레드가 작업을 완료할 때까지 대기
        latch.await();
        executorService.shutdown();

        // then: 최종 포인트 확인
        UserPoint finalPoint = pointService.getUserPoint(userId);

        // 동시성 제어가 제대로 되었다면 정확히 1000이어야 함
        assertThat(finalPoint.point()).isEqualTo(expectedFinalPoint);
    }

    /**
     * 동시에 충전과 사용을 하는 테스트
     *
     * 시나리오:
     * 1. 초기 포인트 1000
     * 2. 5개 스레드는 200씩 충전, 5개 스레드는 100씩 사용
     * 3. 최종 포인트는 1000 + (5*200) - (5*100) = 1500
     */
    @Test
    @DisplayName("동시에 충전과 사용이 발생해도 정확한 금액이 계산된다")
    void concurrentChargeAndUse() throws InterruptedException {
        // given
        long userId = 2000L;
        long initialPoint = 1000L;

        // 초기 포인트 설정
        pointService.chargePoint(userId, initialPoint);

        int chargeThreadCount = 5;
        int useThreadCount = 5;
        long chargeAmount = 200L;
        long useAmount = 100L;

        // 기대값: 1000 + (5*200) - (5*100) = 1500
        long expectedFinalPoint = initialPoint + (chargeThreadCount * chargeAmount) - (useThreadCount * useAmount);

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(chargeThreadCount + useThreadCount);

        // when: 충전 스레드 5개
        for (int i = 0; i < chargeThreadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.chargePoint(userId, chargeAmount);
                } finally {
                    latch.countDown();
                }
            });
        }

        // when: 사용 스레드 5개
        for (int i = 0; i < useThreadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.usePoint(userId, useAmount);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        UserPoint finalPoint = pointService.getUserPoint(userId);
        assertThat(finalPoint.point()).isEqualTo(expectedFinalPoint);
    }

    /**
     * 잔액보다 많은 금액을 동시에 사용하려는 테스트
     *
     * 시나리오:
     * 1. 초기 포인트 1000
     * 2. 10개 스레드가 동시에 500씩 사용 시도
     * 3. 최대 2개만 성공해야 함 (1000 / 500 = 2)
     * 4. 나머지 8개는 "잔액 부족" 예외 발생
     */
    @Test
    @DisplayName("잔액보다 많은 금액을 동시에 사용하면 일부만 성공한다")
    void concurrentUseWithInsufficientBalance() throws InterruptedException {
        // given
        long userId = 3000L;
        long initialPoint = 1000L;
        pointService.chargePoint(userId, initialPoint);

        int threadCount = 10;
        long useAmount = 500L;

        AtomicInteger successCount = new AtomicInteger(0); // 성공 횟수
        AtomicInteger failCount = new AtomicInteger(0); // 실패 횟수

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when: 10개 스레드가 동시에 500씩 사용 시도
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.usePoint(userId, useAmount);
                    successCount.incrementAndGet(); // 성공
                } catch (IllegalArgumentException e) {
                    // "포인트 잔액이 부족합니다" 예외
                    failCount.incrementAndGet(); // 실패
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        UserPoint finalPoint = pointService.getUserPoint(userId);

        // 최대 2번만 성공해야 함 (1000 / 500 = 2)
        assertThat(successCount.get()).isEqualTo(2);
        assertThat(failCount.get()).isEqualTo(8);

        // 최종 잔액은 0이어야 함
        assertThat(finalPoint.point()).isEqualTo(0L);
    }

    /**
     * 같은 유저에 대한 동시 요청 vs 다른 유저에 대한 동시 요청
     *
     * 시나리오:
     * - 서로 다른 유저의 포인트는 독립적으로 동작해야 함
     * - User 1과 User 2가 동시에 포인트를 충전해도 서로 영향 없음
     */
    @Test
    @DisplayName("서로 다른 유저의 동시 충전은 서로 영향을 주지 않는다")
    void concurrentChargeForDifferentUsers() throws InterruptedException {
        // given
        long userId1 = 4000L;
        long userId2 = 5000L;
        int threadCount = 10;
        long chargeAmount = 100L;

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount * 2);
        CountDownLatch latch = new CountDownLatch(threadCount * 2);

        // when: User1에 10번 충전
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.chargePoint(userId1, chargeAmount);
                } finally {
                    latch.countDown();
                }
            });
        }

        // when: User2에 10번 충전 (User1과 동시에)
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pointService.chargePoint(userId2, chargeAmount);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then: 각 유저는 독립적으로 1000씩 가져야 함
        UserPoint user1Point = pointService.getUserPoint(userId1);
        UserPoint user2Point = pointService.getUserPoint(userId2);

        assertThat(user1Point.point()).isEqualTo(1000L);
        assertThat(user2Point.point()).isEqualTo(1000L);
    }
}
