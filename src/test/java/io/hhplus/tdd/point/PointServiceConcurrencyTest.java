package io.hhplus.tdd.point;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PointService 동시성 제어 통합 테스트
 *
 * 목적:
 * - 여러 스레드가 동시에 포인트를 충전/사용할 때 Race Condition이 발생하지 않는지 검증
 * - ReentrantLock을 통한 사용자별 Lock 메커니즘이 올바르게 작동하는지 검증
 * - 동시성 환경에서도 데이터 일관성이 유지되는지 검증
 *
 * 동시성 제어 방식:
 * - 사용자별 ReentrantLock (공정성 있는 Lock)
 * - ConcurrentHashMap으로 Lock 관리
 * - 읽기 작업은 Lock 없이 수행 (성능 최적화)
 */
@SpringBootTest // Spring Boot의 전체 컨텍스트를 로드하여 실제 환경과 유사하게 테스트
@DisplayName("PointService 동시성 제어 통합 테스트")
class PointServiceConcurrencyTest {

    @Autowired // Spring이 PointService 빈을 자동 주입
    private PointService pointService;

    // 테스트에 사용할 기본 사용자 ID
    // System.currentTimeMillis()를 사용하여 매 테스트 실행마다 고유한 ID 보장
    // ThreadLocalRandom으로 추가 랜덤성 부여하여 동시 실행 시에도 충돌 방지
    private long testUserId;

    /**
     * 각 테스트 전에 실행되는 설정 메서드
     * 테스트 간 간섭을 방지하기 위해 고유한 userId를 생성
     */
    @BeforeEach
    void setUp() throws InterruptedException {
        // 현재 시간 기반으로 고유한 사용자 ID 생성
        // System.currentTimeMillis()는 밀리초 단위이므로 테스트 실행마다 다른 값 생성
        testUserId = System.currentTimeMillis();

        // 짧은 대기 시간을 추가하여 다음 테스트의 userId가 확실히 다르도록 보장
        // (테스트가 너무 빠르게 실행되는 경우 대비)
        Thread.sleep(2);
    }

    /**
     * 동시 충전 시나리오 테스트
     *
     * 시나리오:
     * - 한 사용자가 여러 스레드에서 동시에 포인트를 충전
     * - Lock이 없다면: Race Condition으로 인해 일부 충전이 누락될 수 있음
     * - Lock이 있다면: 모든 충전이 순차적으로 처리되어 정확한 합계가 계산됨
     */
    @Nested
    @DisplayName("동시 충전 테스트")
    class ConcurrentChargeTests {

        @Test
        @DisplayName("10개 스레드가 동시에 1000원씩 충전하면 10000원이 된다")
        void concurrentCharge_10Threads() throws InterruptedException {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트할 사용자 ID
            int threadCount = 10; // 동시에 실행할 스레드 개수
            long chargeAmount = 1000L; // 각 스레드가 충전할 금액
            long expectedFinalPoint = threadCount * chargeAmount; // 예상 최종 포인트 (10 * 1000 = 10000)

            // ExecutorService: 스레드 풀을 관리하는 인터페이스
            // FixedThreadPool(10): 최대 10개의 스레드를 재사용하는 풀 생성
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

            // CountDownLatch: 모든 스레드가 동시에 시작하도록 동기화하는 도구
            // threadCount로 초기화하면, countDown()이 threadCount번 호출될 때까지 대기
            CountDownLatch startLatch = new CountDownLatch(1); // 시작 신호용
            CountDownLatch endLatch = new CountDownLatch(threadCount); // 완료 대기용

            // ===== When (실행) =====
            // 10개의 스레드가 각각 충전 작업을 제출
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> { // 람다식으로 Runnable 작성
                    try {
                        // 모든 스레드가 준비될 때까지 대기
                        // startLatch.countDown()이 호출될 때까지 여기서 블로킹
                        startLatch.await();

                        // 포인트 충전 실행 (Critical Section)
                        pointService.chargePoint(userId, chargeAmount);
                    } catch (InterruptedException e) {
                        // 인터럽트 발생 시 현재 스레드의 인터럽트 상태를 다시 설정
                        Thread.currentThread().interrupt();
                    } finally {
                        // 이 스레드의 작업이 완료되었음을 알림
                        endLatch.countDown();
                    }
                });
            }

            // 모든 스레드를 동시에 시작
            startLatch.countDown();

            // 모든 스레드가 완료될 때까지 최대 10초 대기
            // 10초 안에 완료되지 않으면 false 반환 (타임아웃)
            boolean completed = endLatch.await(10, TimeUnit.SECONDS);

            // ExecutorService 종료 (더 이상 작업을 받지 않음)
            executorService.shutdown();

            // ===== Then (검증) =====
            // 1. 모든 스레드가 정상적으로 완료되었는지 확인
            assertThat(completed)
                    .as("모든 스레드가 제한 시간 내에 완료되어야 함")
                    .isTrue();

            // 2. 최종 포인트가 예상값과 일치하는지 확인
            UserPoint finalPoint = pointService.getUserPoint(userId);
            assertThat(finalPoint.point())
                    .as("10개 스레드가 각각 1000원씩 충전하여 총 10000원이 되어야 함")
                    .isEqualTo(expectedFinalPoint);

            // 3. 히스토리 개수가 충전 횟수와 일치하는지 확인
            List<PointHistory> histories = pointService.getPointHistories(userId);
            assertThat(histories)
                    .as("10번의 충전 히스토리가 모두 기록되어야 함")
                    .hasSize(threadCount);
        }

        @Test
        @DisplayName("50개 스레드가 동시에 200원씩 충전하면 10000원이 된다 (대용량 테스트)")
        void concurrentCharge_50Threads() throws InterruptedException {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트할 사용자 ID
            int threadCount = 50; // 동시에 실행할 스레드 개수 (대용량)
            long chargeAmount = 200L; // 각 스레드가 충전할 금액
            long expectedFinalPoint = threadCount * chargeAmount; // 예상 최종 포인트 (50 * 200 = 10000)

            // 50개의 스레드를 관리할 스레드 풀 생성
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

            // 동시 시작 및 완료 대기용 래치
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);

            // ===== When (실행) =====
            // 50개의 충전 작업 제출
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        // 모든 스레드가 동시에 시작하도록 대기
                        startLatch.await();

                        // 포인트 충전 실행
                        pointService.chargePoint(userId, chargeAmount);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        // 작업 완료 표시
                        endLatch.countDown();
                    }
                });
            }

            // 모든 스레드 동시 시작
            startLatch.countDown();

            // 모든 스레드 완료 대기 (대용량이므로 30초 타임아웃)
            boolean completed = endLatch.await(30, TimeUnit.SECONDS);
            executorService.shutdown();

            // ===== Then (검증) =====
            assertThat(completed)
                    .as("모든 스레드가 제한 시간 내에 완료되어야 함")
                    .isTrue();

            // 최종 포인트 검증
            UserPoint finalPoint = pointService.getUserPoint(userId);
            assertThat(finalPoint.point())
                    .as("50개 스레드가 각각 200원씩 충전하여 총 10000원이 되어야 함")
                    .isEqualTo(expectedFinalPoint);

            // 히스토리 개수 검증
            List<PointHistory> histories = pointService.getPointHistories(userId);
            assertThat(histories)
                    .as("50번의 충전 히스토리가 모두 기록되어야 함")
                    .hasSize(threadCount);
        }
    }

    /**
     * 동시 사용 시나리오 테스트
     *
     * 시나리오:
     * - 사용자가 충분한 포인트를 충전한 후, 여러 스레드에서 동시에 포인트 사용
     * - Lock이 없다면: 잔액보다 많은 포인트가 사용될 수 있음 (Over-draft)
     * - Lock이 있다면: 정확한 차감이 순차적으로 처리됨
     */
    @Nested
    @DisplayName("동시 사용 테스트")
    class ConcurrentUseTests {

        @Test
        @DisplayName("10000원 충전 후 10개 스레드가 동시에 500원씩 사용하면 5000원이 된다")
        void concurrentUse_10Threads() throws InterruptedException {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트할 사용자 ID
            long initialCharge = 10000L; // 초기 충전 금액
            int threadCount = 10; // 동시에 실행할 스레드 개수
            long useAmount = 500L; // 각 스레드가 사용할 금액
            long expectedFinalPoint = initialCharge - (threadCount * useAmount); // 예상 최종 포인트 (10000 - 5000 = 5000)

            // 먼저 포인트를 충전하여 초기 잔액 설정
            pointService.chargePoint(userId, initialCharge);

            // 스레드 풀 생성
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);

            // ===== When (실행) =====
            // 10개의 스레드가 각각 500원씩 사용
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        // 동시 시작 대기
                        startLatch.await();

                        // 포인트 사용 실행
                        pointService.usePoint(userId, useAmount);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        // 작업 완료 표시
                        endLatch.countDown();
                    }
                });
            }

            // 모든 스레드 동시 시작
            startLatch.countDown();

            // 모든 스레드 완료 대기
            boolean completed = endLatch.await(10, TimeUnit.SECONDS);
            executorService.shutdown();

            // ===== Then (검증) =====
            assertThat(completed)
                    .as("모든 스레드가 제한 시간 내에 완료되어야 함")
                    .isTrue();

            // 최종 포인트 검증
            UserPoint finalPoint = pointService.getUserPoint(userId);
            assertThat(finalPoint.point())
                    .as("10000원에서 10번 * 500원 사용하여 5000원이 되어야 함")
                    .isEqualTo(expectedFinalPoint);

            // 히스토리 개수 검증 (1회 충전 + 10회 사용 = 11개)
            List<PointHistory> histories = pointService.getPointHistories(userId);
            assertThat(histories)
                    .as("1번의 충전과 10번의 사용 히스토리가 기록되어야 함")
                    .hasSize(threadCount + 1);
        }

        @Test
        @DisplayName("잔액보다 많은 포인트를 동시에 사용하려고 하면 일부만 성공한다")
        void concurrentUse_InsufficientBalance() throws InterruptedException {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트할 사용자 ID
            long initialCharge = 5000L; // 초기 충전 금액 (충분하지 않은 금액)
            int threadCount = 10; // 동시에 실행할 스레드 개수
            long useAmount = 1000L; // 각 스레드가 사용할 금액
            // 5000원으로는 1000원을 5번만 사용 가능

            // 초기 포인트 충전
            pointService.chargePoint(userId, initialCharge);

            // 스레드 풀 생성
            ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);

            // 성공한 사용 횟수를 Thread-Safe하게 카운트
            // AtomicInteger: 동시성 환경에서 안전하게 증감할 수 있는 정수형
            AtomicInteger successCount = new AtomicInteger(0);
            // 실패한 사용 횟수를 Thread-Safe하게 카운트
            AtomicInteger failCount = new AtomicInteger(0);

            // ===== When (실행) =====
            // 10개의 스레드가 각각 1000원씩 사용 시도
            for (int i = 0; i < threadCount; i++) {
                executorService.submit(() -> {
                    try {
                        // 동시 시작 대기
                        startLatch.await();

                        // 포인트 사용 시도
                        pointService.usePoint(userId, useAmount);

                        // 예외가 발생하지 않으면 성공 카운트 증가
                        successCount.incrementAndGet();
                    } catch (IllegalArgumentException e) {
                        // "포인트 잔액이 부족합니다" 예외 발생 시 실패 카운트 증가
                        failCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        // 작업 완료 표시
                        endLatch.countDown();
                    }
                });
            }

            // 모든 스레드 동시 시작
            startLatch.countDown();

            // 모든 스레드 완료 대기
            boolean completed = endLatch.await(10, TimeUnit.SECONDS);
            executorService.shutdown();

            // ===== Then (검증) =====
            assertThat(completed)
                    .as("모든 스레드가 제한 시간 내에 완료되어야 함")
                    .isTrue();

            // 1. 성공 횟수 검증 (5000원으로 1000원씩 5번만 가능)
            assertThat(successCount.get())
                    .as("5000원으로 1000원씩 5번만 사용 가능해야 함")
                    .isEqualTo(5);

            // 2. 실패 횟수 검증 (나머지 5번은 실패)
            assertThat(failCount.get())
                    .as("나머지 5번은 잔액 부족으로 실패해야 함")
                    .isEqualTo(5);

            // 3. 최종 포인트 검증 (5000원 - 5000원 = 0원)
            UserPoint finalPoint = pointService.getUserPoint(userId);
            assertThat(finalPoint.point())
                    .as("모든 포인트가 사용되어 0원이 되어야 함")
                    .isEqualTo(0L);
        }
    }

    /**
     * 충전과 사용이 동시에 발생하는 복합 시나리오 테스트
     *
     * 시나리오:
     * - 일부 스레드는 충전, 일부 스레드는 사용을 동시에 수행
     * - 실제 서비스 환경과 유사한 복잡한 동시성 상황
     */
    @Nested
    @DisplayName("충전과 사용 동시 발생 테스트")
    class ConcurrentMixedTests {

        @Test
        @DisplayName("충전 5회(1000원)와 사용 3회(500원)가 동시에 발생하면 3500원이 된다")
        void concurrentChargeAndUse() throws InterruptedException {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트할 사용자 ID
            int chargeThreadCount = 5; // 충전 스레드 개수
            int useThreadCount = 3; // 사용 스레드 개수
            long chargeAmount = 1000L; // 각 충전 금액
            long useAmount = 500L; // 각 사용 금액
            // 예상 최종 포인트: (5 * 1000) - (3 * 500) = 5000 - 1500 = 3500
            long expectedFinalPoint = (chargeThreadCount * chargeAmount) - (useThreadCount * useAmount);

            // 총 스레드 개수
            int totalThreadCount = chargeThreadCount + useThreadCount;

            // 스레드 풀 생성
            ExecutorService executorService = Executors.newFixedThreadPool(totalThreadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(totalThreadCount);

            // ===== When (실행) =====
            // 충전 스레드 5개 제출
            for (int i = 0; i < chargeThreadCount; i++) {
                executorService.submit(() -> {
                    try {
                        // 동시 시작 대기
                        startLatch.await();

                        // 포인트 충전
                        pointService.chargePoint(userId, chargeAmount);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        // 작업 완료 표시
                        endLatch.countDown();
                    }
                });
            }

            // 사용 스레드 3개 제출
            for (int i = 0; i < useThreadCount; i++) {
                executorService.submit(() -> {
                    try {
                        // 동시 시작 대기
                        startLatch.await();

                        // 포인트 사용 시도 (충전이 먼저 완료되지 않으면 실패할 수 있음)
                        // 하지만 Lock으로 순차 처리되므로 충전 후 사용이 가능해짐
                        try {
                            pointService.usePoint(userId, useAmount);
                        } catch (IllegalArgumentException e) {
                            // 잔액 부족 시 예외 발생 (정상적인 동작)
                            // 충전이 아직 완료되지 않았을 경우 발생할 수 있음
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        // 작업 완료 표시
                        endLatch.countDown();
                    }
                });
            }

            // 모든 스레드 동시 시작
            startLatch.countDown();

            // 모든 스레드 완료 대기
            boolean completed = endLatch.await(10, TimeUnit.SECONDS);
            executorService.shutdown();

            // ===== Then (검증) =====
            assertThat(completed)
                    .as("모든 스레드가 제한 시간 내에 완료되어야 함")
                    .isTrue();

            // 최종 포인트 검증
            // 참고: 사용이 충전보다 먼저 실행되면 실패할 수 있으므로
            // 최종 포인트는 예상값보다 클 수 있음 (일부 사용이 실패한 경우)
            UserPoint finalPoint = pointService.getUserPoint(userId);
            assertThat(finalPoint.point())
                    .as("충전과 사용이 모두 성공하면 3500원, 일부 사용이 실패하면 그보다 큼")
                    .isGreaterThanOrEqualTo(expectedFinalPoint);
        }
    }

    /**
     * 여러 사용자가 동시에 포인트를 충전/사용하는 시나리오 테스트
     *
     * 시나리오:
     * - 사용자별 Lock이 올바르게 분리되어 작동하는지 검증
     * - 사용자 A의 Lock이 사용자 B의 작업을 블로킹하지 않는지 확인
     */
    @Nested
    @DisplayName("여러 사용자 동시 작업 테스트")
    class MultipleUsersConcurrencyTests {

        @Test
        @DisplayName("10명의 사용자가 각각 10개 스레드로 1000원씩 충전하면 각각 10000원이 된다")
        void multipleUsersIndependentCharge() throws InterruptedException {
            // ===== Given (준비) =====
            int userCount = 10; // 동시에 작업할 사용자 수
            int threadPerUser = 10; // 각 사용자당 충전 스레드 수
            long chargeAmount = 1000L; // 각 스레드가 충전할 금액
            long expectedPointPerUser = threadPerUser * chargeAmount; // 각 사용자의 예상 최종 포인트

            // 총 스레드 개수 (10명 * 10개 = 100개)
            int totalThreadCount = userCount * threadPerUser;

            // 사용자 ID 목록 생성
            List<Long> userIds = new ArrayList<>();
            for (int i = 0; i < userCount; i++) {
                // testUserId를 기준으로 10개의 연속된 사용자 ID 생성
                userIds.add(testUserId + i);
            }

            // 스레드 풀 생성 (100개의 스레드)
            ExecutorService executorService = Executors.newFixedThreadPool(totalThreadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(totalThreadCount);

            // ===== When (실행) =====
            // 각 사용자별로 10개의 충전 스레드 제출
            for (long userId : userIds) {
                for (int j = 0; j < threadPerUser; j++) {
                    // userId를 final 변수로 캡처 (람다에서 사용하기 위함)
                    final long currentUserId = userId;

                    executorService.submit(() -> {
                        try {
                            // 동시 시작 대기
                            startLatch.await();

                            // 해당 사용자의 포인트 충전
                            pointService.chargePoint(currentUserId, chargeAmount);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            // 작업 완료 표시
                            endLatch.countDown();
                        }
                    });
                }
            }

            // 모든 스레드 동시 시작
            startLatch.countDown();

            // 모든 스레드 완료 대기 (대용량이므로 40초 타임아웃)
            boolean completed = endLatch.await(40, TimeUnit.SECONDS);
            executorService.shutdown();

            // ===== Then (검증) =====
            assertThat(completed)
                    .as("모든 스레드가 제한 시간 내에 완료되어야 함")
                    .isTrue();

            // 각 사용자의 최종 포인트를 검증
            for (long userId : userIds) {
                UserPoint userPoint = pointService.getUserPoint(userId);

                assertThat(userPoint.point())
                        .as("사용자 %d는 10개 스레드가 각각 1000원씩 충전하여 10000원이 되어야 함", userId)
                        .isEqualTo(expectedPointPerUser);

                // 각 사용자의 히스토리도 검증
                List<PointHistory> histories = pointService.getPointHistories(userId);
                assertThat(histories)
                        .as("사용자 %d는 10번의 충전 히스토리를 가져야 함", userId)
                        .hasSize(threadPerUser);
            }
        }

        @Test
        @DisplayName("사용자 A의 작업이 사용자 B의 작업을 블로킹하지 않는다 (Lock 격리 검증)")
        void userLockIsolation() throws InterruptedException {
            // ===== Given (준비) =====
            long userA = testUserId; // 사용자 A
            long userB = testUserId + 1; // 사용자 B

            long chargeAmount = 1000L; // 충전 금액

            // 스레드 풀 생성 (2개의 스레드)
            ExecutorService executorService = Executors.newFixedThreadPool(2);

            // 각 스레드의 실행 시간을 기록할 리스트
            // CopyOnWriteArrayList: Thread-Safe한 ArrayList
            List<Long> executionTimes = new CopyOnWriteArrayList<>();

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(2);

            // ===== When (실행) =====
            // 사용자 A의 충전 작업 (실행 시간 기록)
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    // 시작 시간 기록
                    long startTime = System.currentTimeMillis();

                    // 포인트 충전
                    pointService.chargePoint(userA, chargeAmount);

                    // 종료 시간 기록 및 실행 시간 계산
                    long executionTime = System.currentTimeMillis() - startTime;
                    executionTimes.add(executionTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });

            // 사용자 B의 충전 작업 (실행 시간 기록)
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    // 시작 시간 기록
                    long startTime = System.currentTimeMillis();

                    // 포인트 충전
                    pointService.chargePoint(userB, chargeAmount);

                    // 종료 시간 기록 및 실행 시간 계산
                    long executionTime = System.currentTimeMillis() - startTime;
                    executionTimes.add(executionTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });

            // 모든 스레드 동시 시작
            startLatch.countDown();

            // 모든 스레드 완료 대기
            boolean completed = endLatch.await(10, TimeUnit.SECONDS);
            executorService.shutdown();

            // ===== Then (검증) =====
            assertThat(completed)
                    .as("모든 스레드가 제한 시간 내에 완료되어야 함")
                    .isTrue();

            // 1. 각 사용자의 포인트 검증
            assertThat(pointService.getUserPoint(userA).point())
                    .as("사용자 A는 1000원이 충전되어야 함")
                    .isEqualTo(chargeAmount);

            assertThat(pointService.getUserPoint(userB).point())
                    .as("사용자 B는 1000원이 충전되어야 함")
                    .isEqualTo(chargeAmount);

            // 2. 실행 시간 검증 (두 작업이 거의 동시에 완료되어야 함)
            // 만약 사용자별 Lock이 격리되지 않았다면, 한 작업이 끝날 때까지 다른 작업이 대기해야 함
            // 실행 시간 차이가 200ms 이내라면 동시에 실행된 것으로 판단 (DB I/O 고려)
            assertThat(executionTimes)
                    .as("2개의 실행 시간이 기록되어야 함")
                    .hasSize(2);

            // 두 실행 시간의 차이 계산
            long timeDiff = Math.abs(executionTimes.get(0) - executionTimes.get(1));

            assertThat(timeDiff)
                    .as("사용자 A와 B의 작업이 거의 동시에 완료되어야 함 (Lock 격리 증명)")
                    .isLessThan(200L); // 200ms 이내 차이 (DB I/O 시간 고려)
        }
    }
}
