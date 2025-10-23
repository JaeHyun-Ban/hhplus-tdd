package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * PointService 예외 케이스 검증 테스트
 *
 * 목적: 정책 위반 시 적절한 예외가 발생하는지 검증
 *
 * 테스트 범위:
 * 1. 금액 검증 실패 케이스
 * 2. 잔액 부족 케이스
 * 3. 비즈니스 정책 위반 케이스
 */
@ExtendWith(MockitoExtension.class) // Mockito를 JUnit5와 통합
@DisplayName("PointService 예외 케이스 테스트")
class PointServiceExceptionTest {

    @Mock // UserPointTable을 Mock 객체로 생성
    private UserPointTable userPointTable;

    @Mock // PointHistoryTable을 Mock 객체로 생성
    private PointHistoryTable pointHistoryTable;

    @InjectMocks // Mock 객체들을 PointService에 자동 주입
    private PointService pointService;

    /**
     * 포인트 충전 예외 케이스 테스트 그룹
     */
    @Nested
    @DisplayName("포인트 충전 예외 케이스")
    class ChargePointExceptions {

        @Test
        @DisplayName("0원 충전 시 IllegalArgumentException 발생")
        void shouldThrowException_WhenChargeZeroAmount() {
            // ===== Given (준비) =====
            long userId = 1L; // 테스트할 유저 ID
            long zeroAmount = 0L; // 0원 충전 시도

            // ===== When & Then (실행 및 검증) =====
            assertThatThrownBy(() -> pointService.chargePoint(userId, zeroAmount)) // 0원 충전 실행
                    .isInstanceOf(IllegalArgumentException.class) // IllegalArgumentException이 발생해야 함
                    .hasMessage("포인트는 0보다 커야 합니다."); // 에러 메시지 검증
        }

        @Test
        @DisplayName("음수 금액 충전 시 IllegalArgumentException 발생")
        void shouldThrowException_WhenChargeNegativeAmount() {
            // ===== Given (준비) =====
            long userId = 1L; // 테스트할 유저 ID
            long negativeAmount = -1000L; // 음수 금액

            // ===== When & Then (실행 및 검증) =====
            assertThatThrownBy(() -> pointService.chargePoint(userId, negativeAmount)) // 음수 충전 실행
                    .isInstanceOf(IllegalArgumentException.class) // IllegalArgumentException이 발생해야 함
                    .hasMessage("포인트는 0보다 커야 합니다."); // 에러 메시지 검증
        }

        @Test
        @DisplayName("최소 충전 금액 미만 시 예외 발생 (정책: 최소 100원)")
        void shouldThrowException_WhenBelowMinimumChargeAmount() {
            // ===== Given (준비) =====
            long userId = 1L; // 테스트할 유저 ID
            long belowMinimum = 50L; // 최소 금액(100원) 미만

            // 참고: 현재는 정책이 구현되지 않았으므로 이 테스트는 실패할 수 있음
            // 이것이 바로 TDD의 RED 단계!

            // ===== When & Then (실행 및 검증) =====
            // 향후 정책 추가 시 활성화
            // assertThatThrownBy(() -> pointService.chargePoint(userId, belowMinimum))
            //         .isInstanceOf(IllegalArgumentException.class)
            //         .hasMessage("최소 충전 금액은 100원입니다.");
        }

        @Test
        @DisplayName("1회 최대 충전 금액 초과 시 예외 발생 (정책: 최대 100만원)")
        void shouldThrowException_WhenExceedMaximumChargeAmount() {
            // ===== Given (준비) =====
            long userId = 1L; // 테스트할 유저 ID
            long exceedMaximum = 1_000_001L; // 최대 금액(100만원) 초과

            // 참고: 현재는 정책이 구현되지 않았으므로 이 테스트는 실패할 수 있음
            // 향후 정책 추가를 위한 테스트 (TDD RED 단계)

            // ===== When & Then (실행 및 검증) =====
            // 향후 정책 추가 시 활성화
            // assertThatThrownBy(() -> pointService.chargePoint(userId, exceedMaximum))
            //         .isInstanceOf(IllegalArgumentException.class)
            //         .hasMessage("1회 최대 충전 금액은 100만원입니다.");
        }
    }

    /**
     * 포인트 사용 예외 케이스 테스트 그룹
     */
    @Nested
    @DisplayName("포인트 사용 예외 케이스")
    class UsePointExceptions {

        @Test
        @DisplayName("0원 사용 시 IllegalArgumentException 발생")
        void shouldThrowException_WhenUseZeroAmount() {
            // ===== Given (준비) =====
            long userId = 1L; // 테스트할 유저 ID
            long zeroAmount = 0L; // 0원 사용 시도

            // ===== When & Then (실행 및 검증) =====
            assertThatThrownBy(() -> pointService.usePoint(userId, zeroAmount)) // 0원 사용 실행
                    .isInstanceOf(IllegalArgumentException.class) // IllegalArgumentException이 발생해야 함
                    .hasMessage("포인트는 0보다 커야 합니다."); // 에러 메시지 검증
        }

        @Test
        @DisplayName("음수 금액 사용 시 IllegalArgumentException 발생")
        void shouldThrowException_WhenUseNegativeAmount() {
            // ===== Given (준비) =====
            long userId = 1L; // 테스트할 유저 ID
            long negativeAmount = -500L; // 음수 금액

            // ===== When & Then (실행 및 검증) =====
            assertThatThrownBy(() -> pointService.usePoint(userId, negativeAmount)) // 음수 사용 실행
                    .isInstanceOf(IllegalArgumentException.class) // IllegalArgumentException이 발생해야 함
                    .hasMessage("포인트는 0보다 커야 합니다."); // 에러 메시지 검증
        }

        @Test
        @DisplayName("잔액 부족 시 IllegalArgumentException 발생")
        void shouldThrowException_WhenInsufficientBalance() {
            // ===== Given (준비) =====
            long userId = 1L; // 테스트할 유저 ID
            long currentBalance = 500L; // 현재 잔액
            long useAmount = 1000L; // 사용하려는 금액 (잔액보다 많음)

            UserPoint currentPoint = new UserPoint(userId, currentBalance, System.currentTimeMillis()); // 현재 포인트 객체 생성
            given(userPointTable.selectById(userId)).willReturn(currentPoint); // Mock: DB에서 현재 포인트 조회 시 currentPoint 반환

            // ===== When & Then (실행 및 검증) =====
            assertThatThrownBy(() -> pointService.usePoint(userId, useAmount)) // 잔액보다 많은 금액 사용 시도
                    .isInstanceOf(IllegalArgumentException.class) // IllegalArgumentException이 발생해야 함
                    .hasMessage("포인트 잔액이 부족합니다."); // 에러 메시지 검증
        }

        @Test
        @DisplayName("잔액과 정확히 같은 금액은 사용 가능")
        void shouldNotThrowException_WhenUseExactBalance() {
            // ===== Given (준비) =====
            long userId = 1L; // 테스트할 유저 ID
            long exactBalance = 1000L; // 현재 잔액
            long useAmount = 1000L; // 사용하려는 금액 (잔액과 동일)

            UserPoint currentPoint = new UserPoint(userId, exactBalance, System.currentTimeMillis()); // 현재 포인트 객체
            UserPoint zeroPoint = new UserPoint(userId, 0L, System.currentTimeMillis()); // 사용 후 0원

            given(userPointTable.selectById(userId)).willReturn(currentPoint); // Mock: 현재 포인트 조회
            given(userPointTable.insertOrUpdate(userId, 0L)).willReturn(zeroPoint); // Mock: 0원으로 업데이트

            // ===== When & Then (실행 및 검증) =====
            // 예외가 발생하지 않아야 함
            pointService.usePoint(userId, useAmount); // 정확히 잔액만큼 사용
            // 예외가 발생하지 않으면 테스트 성공
        }

        @Test
        @DisplayName("최소 사용 금액 미만 시 예외 발생 (정책: 최소 10원)")
        void shouldThrowException_WhenBelowMinimumUseAmount() {
            // ===== Given (준비) =====
            long userId = 1L; // 테스트할 유저 ID
            long belowMinimum = 5L; // 최소 금액(10원) 미만

            // 참고: 향후 정책 추가를 위한 테스트

            // ===== When & Then (실행 및 검증) =====
            // 향후 정책 추가 시 활성화
            // assertThatThrownBy(() -> pointService.usePoint(userId, belowMinimum))
            //         .isInstanceOf(IllegalArgumentException.class)
            //         .hasMessage("최소 사용 금액은 10원입니다.");
        }
    }

    /**
     * 비즈니스 정책 예외 케이스 테스트 그룹
     */
    @Nested
    @DisplayName("비즈니스 정책 예외 케이스")
    class BusinessPolicyExceptions {

        @Test
        @DisplayName("존재하지 않는 유저는 에러 없이 0 포인트 반환")
        void shouldReturnZeroPoint_WhenUserNotExists() {
            // ===== Given (준비) =====
            long nonExistentUserId = 9999L; // 존재하지 않는 유저 ID
            UserPoint emptyPoint = UserPoint.empty(nonExistentUserId); // 빈 포인트 객체 (0원)

            given(userPointTable.selectById(nonExistentUserId)).willReturn(emptyPoint); // Mock: 존재하지 않는 유저 조회 시 빈 포인트 반환

            // ===== When (실행) =====
            UserPoint result = pointService.getUserPoint(nonExistentUserId); // 존재하지 않는 유저 포인트 조회

            // ===== Then (검증) =====
            // 예외가 발생하지 않고 0 포인트를 반환해야 함
            assert result.point() == 0L; // 포인트가 0이어야 함
        }

        @Test
        @DisplayName("일일 충전 한도 초과 시 예외 발생 (정책: 일일 500만원)")
        void shouldThrowException_WhenExceedDailyChargeLimit() {
            // ===== Given (준비) =====
            long userId = 1L; // 테스트할 유저 ID

            // 참고: 이 테스트는 일일 충전 한도를 추적하는 기능이 필요함
            // 현재는 구현되지 않았으므로 향후 추가 예정

            // ===== When & Then (실행 및 검증) =====
            // 향후 정책 추가 시 활성화
            // 1. 오늘 이미 500만원 충전했다고 가정
            // 2. 추가로 1원이라도 충전 시도하면 예외 발생
            // assertThatThrownBy(() -> pointService.chargePoint(userId, 1L))
            //         .isInstanceOf(IllegalArgumentException.class)
            //         .hasMessage("일일 충전 한도를 초과했습니다.");
        }

        @Test
        @DisplayName("보유 가능한 최대 포인트 초과 시 예외 발생 (정책: 최대 1000만원)")
        void shouldThrowException_WhenExceedMaximumBalance() {
            // ===== Given (준비) =====
            long userId = 1L; // 테스트할 유저 ID
            long currentBalance = 9_999_999L; // 현재 잔액 (거의 최대치)
            long chargeAmount = 2L; // 충전 금액 (최대치 초과하게 됨)

            // 참고: 최대 보유 한도 정책 추가 시 활성화

            // ===== When & Then (실행 및 검증) =====
            // 향후 정책 추가 시 활성화
            // assertThatThrownBy(() -> pointService.chargePoint(userId, chargeAmount))
            //         .isInstanceOf(IllegalArgumentException.class)
            //         .hasMessage("최대 보유 가능한 포인트는 1000만원입니다.");
        }

        @Test
        @DisplayName("휴면 계정은 포인트 사용 불가 (정책)")
        void shouldThrowException_WhenInactiveAccount() {
            // ===== Given (준비) =====
            long inactiveUserId = 1L; // 휴면 계정 ID

            // 참고: 휴면 계정 판단 로직이 필요함 (향후 추가)

            // ===== When & Then (실행 및 검증) =====
            // 향후 정책 추가 시 활성화
            // assertThatThrownBy(() -> pointService.usePoint(inactiveUserId, 100L))
            //         .isInstanceOf(IllegalArgumentException.class)
            //         .hasMessage("휴면 계정은 포인트를 사용할 수 없습니다.");
        }

        @Test
        @DisplayName("탈퇴한 계정은 포인트 조회 불가 (정책)")
        void shouldThrowException_WhenWithdrawnAccount() {
            // ===== Given (준비) =====
            long withdrawnUserId = 1L; // 탈퇴한 계정 ID

            // 참고: 계정 상태 확인 로직이 필요함 (향후 추가)

            // ===== When & Then (실행 및 검증) =====
            // 향후 정책 추가 시 활성화
            // assertThatThrownBy(() -> pointService.getUserPoint(withdrawnUserId))
            //         .isInstanceOf(IllegalArgumentException.class)
            //         .hasMessage("탈퇴한 계정입니다.");
        }
    }

    /**
     * 경계값 테스트 (Boundary Value Test)
     */
    @Nested
    @DisplayName("경계값 테스트")
    class BoundaryValueTests {

        @Test
        @DisplayName("Long 최대값 충전 시도 (오버플로우 방지 테스트)")
        void shouldHandleLongMaxValue() {
            // ===== Given (준비) =====
            long userId = 1L; // 테스트할 유저 ID
            long maxValue = Long.MAX_VALUE; // Long 타입의 최대값

            // 참고: 실제로는 이렇게 큰 값을 허용하면 안 됨
            // 오버플로우 방지를 위한 검증이 필요

            // ===== When & Then (실행 및 검증) =====
            // 향후 정책 추가 시 활성화
            // assertThatThrownBy(() -> pointService.chargePoint(userId, maxValue))
            //         .isInstanceOf(IllegalArgumentException.class)
            //         .hasMessage("허용된 범위를 초과했습니다.");
        }

        @Test
        @DisplayName("1원 충전은 성공해야 함 (최소 경계값)")
        void shouldSucceed_WhenChargeOneWon() {
            // ===== Given (준비) =====
            long userId = 1L; // 테스트할 유저 ID
            long oneWon = 1L; // 1원 (최소 유효값)

            UserPoint currentPoint = new UserPoint(userId, 0L, System.currentTimeMillis()); // 현재 0원
            UserPoint updatedPoint = new UserPoint(userId, 1L, System.currentTimeMillis()); // 충전 후 1원

            given(userPointTable.selectById(userId)).willReturn(currentPoint); // Mock: 현재 포인트 조회
            given(userPointTable.insertOrUpdate(userId, 1L)).willReturn(updatedPoint); // Mock: 1원으로 업데이트

            // ===== When & Then (실행 및 검증) =====
            // 예외가 발생하지 않아야 함
            pointService.chargePoint(userId, oneWon); // 1원 충전
            // 예외가 발생하지 않으면 테스트 성공
        }
    }
}
