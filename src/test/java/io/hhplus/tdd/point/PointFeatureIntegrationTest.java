package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 각 기능별 통합 테스트
 *
 * 목적: 실제 환경과 유사한 조건에서 각 기능이 올바르게 동작하는지 검증
 *
 * 특징:
 * - @SpringBootTest로 전체 Spring Context 로드
 * - 실제 데이터베이스 테이블 사용
 * - 모든 계층(Controller → Service → Repository) 통합 검증
 */
@SpringBootTest // Spring Boot의 전체 컨텍스트를 로드하여 통합 테스트 환경 구성
@DisplayName("포인트 기능별 통합 테스트")
class PointFeatureIntegrationTest {

    @Autowired // Spring이 PointService 빈을 자동 주입
    private PointService pointService;

    @Autowired // Spring이 UserPointTable 빈을 자동 주입
    private UserPointTable userPointTable;

    @Autowired // Spring이 PointHistoryTable 빈을 자동 주입
    private PointHistoryTable pointHistoryTable;

    private static long testUserId = 10000L; // 테스트용 유저 ID 시작값 (다른 테스트와 겹치지 않도록)

    @BeforeEach // 각 테스트 메서드 실행 전에 실행되는 설정 메서드
    void setUp() {
        testUserId++; // 각 테스트마다 다른 유저 ID 사용 (데이터 격리)
    }

    /**
     * 포인트 충전 기능 통합 테스트
     */
    @Nested
    @DisplayName("포인트 충전 기능")
    class ChargePointFeature {

        @Test
        @DisplayName("신규 유저가 첫 충전을 성공한다")
        void newUserFirstCharge() {
            // ===== Given (준비) =====
            long userId = testUserId; // 신규 유저 ID
            long chargeAmount = 5000L; // 충전할 금액

            // ===== When (실행) =====
            UserPoint result = pointService.chargePoint(userId, chargeAmount); // 첫 충전 실행

            // ===== Then (검증) =====
            assertThat(result.id()).isEqualTo(userId); // 유저 ID가 일치해야 함
            assertThat(result.point()).isEqualTo(chargeAmount); // 충전한 금액과 포인트가 일치해야 함
            assertThat(result.updateMillis()).isGreaterThan(0L); // 업데이트 시간이 설정되어 있어야 함

            // 히스토리 검증
            List<PointHistory> histories = pointService.getPointHistories(userId); // 히스토리 조회
            assertThat(histories).hasSize(1); // 히스토리가 1개 생성되어야 함
            assertThat(histories.get(0).type()).isEqualTo(TransactionType.CHARGE); // 타입이 CHARGE여야 함
            assertThat(histories.get(0).amount()).isEqualTo(chargeAmount); // 금액이 일치해야 함
        }

        @Test
        @DisplayName("기존 유저가 추가 충전을 성공한다")
        void existingUserAdditionalCharge() {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트 유저 ID
            long firstCharge = 3000L; // 첫 번째 충전 금액
            long secondCharge = 2000L; // 두 번째 충전 금액
            long expectedTotal = firstCharge + secondCharge; // 예상 총액 (5000원)

            pointService.chargePoint(userId, firstCharge); // 첫 번째 충전 (데이터 셋업)

            // ===== When (실행) =====
            UserPoint result = pointService.chargePoint(userId, secondCharge); // 두 번째 충전 실행

            // ===== Then (검증) =====
            assertThat(result.point()).isEqualTo(expectedTotal); // 총 포인트가 5000원이어야 함

            // 히스토리 검증
            List<PointHistory> histories = pointService.getPointHistories(userId); // 히스토리 조회
            assertThat(histories).hasSize(2); // 충전 2회이므로 히스토리 2개
            assertThat(histories.get(0).amount()).isEqualTo(firstCharge); // 첫 번째 충전 금액 확인
            assertThat(histories.get(1).amount()).isEqualTo(secondCharge); // 두 번째 충전 금액 확인
        }

        @Test
        @DisplayName("여러 번 연속 충전이 모두 누적된다")
        void multipleConsecutiveCharges() {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트 유저 ID
            long[] chargeAmounts = {1000L, 2000L, 3000L, 4000L, 5000L}; // 충전할 금액 배열
            long expectedTotal = 15000L; // 예상 총액 (1000+2000+3000+4000+5000)

            // ===== When (실행) =====
            for (long amount : chargeAmounts) { // 배열의 각 금액만큼 반복 충전
                pointService.chargePoint(userId, amount); // 충전 실행
            }

            // ===== Then (검증) =====
            UserPoint result = pointService.getUserPoint(userId); // 최종 포인트 조회
            assertThat(result.point()).isEqualTo(expectedTotal); // 총액이 15000원이어야 함

            // 히스토리 검증
            List<PointHistory> histories = pointService.getPointHistories(userId); // 히스토리 조회
            assertThat(histories).hasSize(5); // 5회 충전이므로 히스토리 5개
            assertThat(histories).allMatch(h -> h.type() == TransactionType.CHARGE); // 모든 히스토리가 CHARGE 타입
        }

        @Test
        @DisplayName("충전 후 즉시 조회하면 업데이트된 포인트를 확인할 수 있다")
        void chargeAndImmediatelyRetrieve() {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트 유저 ID
            long chargeAmount = 7000L; // 충전 금액

            // ===== When (실행) =====
            pointService.chargePoint(userId, chargeAmount); // 충전 실행
            UserPoint result = pointService.getUserPoint(userId); // 즉시 조회

            // ===== Then (검증) =====
            assertThat(result.point()).isEqualTo(chargeAmount); // 충전한 금액과 조회한 포인트가 일치
        }
    }

    /**
     * 포인트 사용 기능 통합 테스트
     */
    @Nested
    @DisplayName("포인트 사용 기능")
    class UsePointFeature {

        @Test
        @DisplayName("충전 후 일부 사용이 성공한다")
        void chargeAndPartialUse() {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트 유저 ID
            long chargeAmount = 10000L; // 충전 금액
            long useAmount = 3000L; // 사용 금액
            long expectedRemaining = chargeAmount - useAmount; // 예상 잔액 (7000원)

            pointService.chargePoint(userId, chargeAmount); // 먼저 충전 (데이터 셋업)

            // ===== When (실행) =====
            UserPoint result = pointService.usePoint(userId, useAmount); // 포인트 사용

            // ===== Then (검증) =====
            assertThat(result.point()).isEqualTo(expectedRemaining); // 잔액이 7000원이어야 함

            // 히스토리 검증
            List<PointHistory> histories = pointService.getPointHistories(userId); // 히스토리 조회
            assertThat(histories).hasSize(2); // 충전 1회 + 사용 1회 = 2개
            assertThat(histories.get(0).type()).isEqualTo(TransactionType.CHARGE); // 첫 번째는 CHARGE
            assertThat(histories.get(1).type()).isEqualTo(TransactionType.USE); // 두 번째는 USE
        }

        @Test
        @DisplayName("포인트 전액 사용이 성공한다")
        void useAllPoints() {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트 유저 ID
            long chargeAmount = 5000L; // 충전 금액
            long useAmount = 5000L; // 사용 금액 (전액)

            pointService.chargePoint(userId, chargeAmount); // 충전 (데이터 셋업)

            // ===== When (실행) =====
            UserPoint result = pointService.usePoint(userId, useAmount); // 전액 사용

            // ===== Then (검증) =====
            assertThat(result.point()).isEqualTo(0L); // 잔액이 0원이어야 함

            // 히스토리 검증
            List<PointHistory> histories = pointService.getPointHistories(userId); // 히스토리 조회
            assertThat(histories).hasSize(2); // 충전 1회 + 사용 1회
        }

        @Test
        @DisplayName("여러 번 나누어 사용이 성공한다")
        void multiplePartialUses() {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트 유저 ID
            long initialCharge = 10000L; // 초기 충전 금액
            long[] useAmounts = {1000L, 2000L, 3000L}; // 사용할 금액 배열
            long totalUsed = 6000L; // 총 사용액
            long expectedRemaining = initialCharge - totalUsed; // 예상 잔액 (4000원)

            pointService.chargePoint(userId, initialCharge); // 초기 충전 (데이터 셋업)

            // ===== When (실행) =====
            for (long amount : useAmounts) { // 각 금액만큼 반복 사용
                pointService.usePoint(userId, amount); // 포인트 사용
            }

            // ===== Then (검증) =====
            UserPoint result = pointService.getUserPoint(userId); // 최종 포인트 조회
            assertThat(result.point()).isEqualTo(expectedRemaining); // 잔액이 4000원이어야 함

            // 히스토리 검증
            List<PointHistory> histories = pointService.getPointHistories(userId); // 히스토리 조회
            assertThat(histories).hasSize(4); // 충전 1회 + 사용 3회 = 4개
        }

        @Test
        @DisplayName("잔액 부족 시 사용이 실패한다")
        void useFailsWhenInsufficientBalance() {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트 유저 ID
            long chargeAmount = 1000L; // 충전 금액
            long excessiveUseAmount = 1500L; // 사용 금액 (잔액보다 많음)

            pointService.chargePoint(userId, chargeAmount); // 충전 (데이터 셋업)

            // ===== When & Then (실행 및 검증) =====
            assertThatThrownBy(() -> pointService.usePoint(userId, excessiveUseAmount)) // 잔액보다 많은 금액 사용 시도
                    .isInstanceOf(IllegalArgumentException.class) // 예외 발생 확인
                    .hasMessage("포인트 잔액이 부족합니다."); // 예외 메시지 확인

            // 실패 후 잔액 확인
            UserPoint result = pointService.getUserPoint(userId); // 포인트 조회
            assertThat(result.point()).isEqualTo(chargeAmount); // 잔액이 변하지 않아야 함 (롤백)
        }
    }

    /**
     * 포인트 조회 기능 통합 테스트
     */
    @Nested
    @DisplayName("포인트 조회 기능")
    class GetPointFeature {

        @Test
        @DisplayName("신규 유저는 0 포인트를 조회한다")
        void newUserHasZeroPoint() {
            // ===== Given (준비) =====
            long newUserId = testUserId; // 신규 유저 ID

            // ===== When (실행) =====
            UserPoint result = pointService.getUserPoint(newUserId); // 신규 유저 포인트 조회

            // ===== Then (검증) =====
            assertThat(result.id()).isEqualTo(newUserId); // 유저 ID 일치
            assertThat(result.point()).isEqualTo(0L); // 포인트가 0이어야 함
        }

        @Test
        @DisplayName("충전한 유저는 충전 금액을 조회한다")
        void chargedUserRetrievesCorrectAmount() {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트 유저 ID
            long chargeAmount = 8000L; // 충전 금액

            pointService.chargePoint(userId, chargeAmount); // 충전 (데이터 셋업)

            // ===== When (실행) =====
            UserPoint result = pointService.getUserPoint(userId); // 포인트 조회

            // ===== Then (검증) =====
            assertThat(result.point()).isEqualTo(chargeAmount); // 충전 금액과 일치
        }

        @Test
        @DisplayName("여러 번 조회해도 같은 값을 반환한다 (멱등성)")
        void multipleRetrievalsReturnSameValue() {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트 유저 ID
            long chargeAmount = 6000L; // 충전 금액

            pointService.chargePoint(userId, chargeAmount); // 충전 (데이터 셋업)

            // ===== When (실행) =====
            UserPoint result1 = pointService.getUserPoint(userId); // 첫 번째 조회
            UserPoint result2 = pointService.getUserPoint(userId); // 두 번째 조회
            UserPoint result3 = pointService.getUserPoint(userId); // 세 번째 조회

            // ===== Then (검증) =====
            assertThat(result1.point()).isEqualTo(chargeAmount); // 모두 같은 금액
            assertThat(result2.point()).isEqualTo(chargeAmount); // 조회는 데이터를 변경하지 않음
            assertThat(result3.point()).isEqualTo(chargeAmount); // 멱등성 보장
        }
    }

    /**
     * 포인트 히스토리 조회 기능 통합 테스트
     */
    @Nested
    @DisplayName("포인트 히스토리 조회 기능")
    class GetHistoryFeature {

        @Test
        @DisplayName("신규 유저는 빈 히스토리를 조회한다")
        void newUserHasEmptyHistory() {
            // ===== Given (준비) =====
            long newUserId = testUserId; // 신규 유저 ID

            // ===== When (실행) =====
            List<PointHistory> histories = pointService.getPointHistories(newUserId); // 히스토리 조회

            // ===== Then (검증) =====
            assertThat(histories).isEmpty(); // 히스토리가 비어있어야 함
        }

        @Test
        @DisplayName("충전/사용 내역이 시간순으로 조회된다")
        void historiesAreRetrievedInOrder() {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트 유저 ID

            // ===== When (실행) =====
            pointService.chargePoint(userId, 5000L); // 첫 번째: 5000원 충전
            pointService.chargePoint(userId, 3000L); // 두 번째: 3000원 충전
            pointService.usePoint(userId, 2000L); // 세 번째: 2000원 사용
            pointService.usePoint(userId, 1000L); // 네 번째: 1000원 사용

            List<PointHistory> histories = pointService.getPointHistories(userId); // 히스토리 조회

            // ===== Then (검증) =====
            assertThat(histories).hasSize(4); // 총 4개의 히스토리

            // 순서 검증 (시간순)
            assertThat(histories.get(0).amount()).isEqualTo(5000L); // 첫 번째 충전
            assertThat(histories.get(0).type()).isEqualTo(TransactionType.CHARGE); // CHARGE 타입

            assertThat(histories.get(1).amount()).isEqualTo(3000L); // 두 번째 충전
            assertThat(histories.get(1).type()).isEqualTo(TransactionType.CHARGE); // CHARGE 타입

            assertThat(histories.get(2).amount()).isEqualTo(2000L); // 첫 번째 사용
            assertThat(histories.get(2).type()).isEqualTo(TransactionType.USE); // USE 타입

            assertThat(histories.get(3).amount()).isEqualTo(1000L); // 두 번째 사용
            assertThat(histories.get(3).type()).isEqualTo(TransactionType.USE); // USE 타입
        }

        @Test
        @DisplayName("각 히스토리는 올바른 타임스탬프를 가진다")
        void historiesHaveCorrectTimestamps() {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트 유저 ID
            long beforeCharge = System.currentTimeMillis(); // 충전 전 시간

            // ===== When (실행) =====
            pointService.chargePoint(userId, 1000L); // 충전 실행

            long afterCharge = System.currentTimeMillis(); // 충전 후 시간

            List<PointHistory> histories = pointService.getPointHistories(userId); // 히스토리 조회

            // ===== Then (검증) =====
            assertThat(histories).hasSize(1); // 히스토리 1개
            PointHistory history = histories.get(0); // 첫 번째 히스토리

            // 타임스탬프가 유효한 범위 내에 있는지 확인
            assertThat(history.updateMillis()).isGreaterThanOrEqualTo(beforeCharge); // 충전 전보다 크거나 같음
            assertThat(history.updateMillis()).isLessThanOrEqualTo(afterCharge); // 충전 후보다 작거나 같음
        }
    }

    /**
     * 복합 시나리오 통합 테스트
     */
    @Nested
    @DisplayName("복합 시나리오")
    class ComplexScenarios {

        @Test
        @DisplayName("실제 사용 시나리오: 충전 → 사용 → 재충전 → 재사용")
        void realWorldScenario() {
            // ===== Given (준비) =====
            long userId = testUserId; // 테스트 유저 ID

            // ===== When (실행) =====
            // 1. 첫 충전: 10000원
            pointService.chargePoint(userId, 10000L); // 잔액: 10000원
            assertThat(pointService.getUserPoint(userId).point()).isEqualTo(10000L); // 검증

            // 2. 첫 사용: 3000원
            pointService.usePoint(userId, 3000L); // 잔액: 7000원
            assertThat(pointService.getUserPoint(userId).point()).isEqualTo(7000L); // 검증

            // 3. 재충전: 5000원
            pointService.chargePoint(userId, 5000L); // 잔액: 12000원
            assertThat(pointService.getUserPoint(userId).point()).isEqualTo(12000L); // 검증

            // 4. 재사용: 2000원
            pointService.usePoint(userId, 2000L); // 잔액: 10000원
            assertThat(pointService.getUserPoint(userId).point()).isEqualTo(10000L); // 검증

            // ===== Then (검증) =====
            // 최종 잔액 검증
            UserPoint finalPoint = pointService.getUserPoint(userId); // 최종 포인트 조회
            assertThat(finalPoint.point()).isEqualTo(10000L); // 최종 잔액 10000원

            // 히스토리 검증
            List<PointHistory> histories = pointService.getPointHistories(userId); // 히스토리 조회
            assertThat(histories).hasSize(4); // 총 4개의 트랜잭션
        }

        @Test
        @DisplayName("여러 유저의 포인트는 독립적으로 관리된다")
        void multipleUsersIndependent() {
            // ===== Given (준비) =====
            long user1 = testUserId; // 첫 번째 유저
            long user2 = testUserId + 1; // 두 번째 유저
            long user3 = testUserId + 2; // 세 번째 유저

            // ===== When (실행) =====
            pointService.chargePoint(user1, 1000L); // 유저1: 1000원 충전
            pointService.chargePoint(user2, 2000L); // 유저2: 2000원 충전
            pointService.chargePoint(user3, 3000L); // 유저3: 3000원 충전

            // ===== Then (검증) =====
            assertThat(pointService.getUserPoint(user1).point()).isEqualTo(1000L); // 유저1: 1000원
            assertThat(pointService.getUserPoint(user2).point()).isEqualTo(2000L); // 유저2: 2000원
            assertThat(pointService.getUserPoint(user3).point()).isEqualTo(3000L); // 유저3: 3000원

            // 각 유저의 히스토리가 독립적으로 관리됨
            assertThat(pointService.getPointHistories(user1)).hasSize(1); // 유저1: 히스토리 1개
            assertThat(pointService.getPointHistories(user2)).hasSize(1); // 유저2: 히스토리 1개
            assertThat(pointService.getPointHistories(user3)).hasSize(1); // 유저3: 히스토리 1개
        }
    }
}
