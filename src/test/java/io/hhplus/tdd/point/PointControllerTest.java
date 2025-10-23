package io.hhplus.tdd.point;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PointController의 단위 테스트
 * TDD Red-Green-Refactor 사이클을 따라 작성
 *
 * 테스트 구조:
 * - 포인트 조회 테스트
 * - 포인트 충전 테스트 (정상 케이스 / 예외 케이스)
 * - 포인트 사용 테스트 (정상 케이스 / 예외 케이스)
 * - 포인트 히스토리 조회 테스트
 */
@WebMvcTest(PointController.class)
@DisplayName("PointController 테스트")
class PointControllerTest {

    // mockMvc는 Spring MVC의 DispatcherServlet을 모의로 실행시켜 "실제 요청처럼 컨트롤러를 호출"하게 만들어주는 객체
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // 가짜 객체를 주입 >> 실제로 Service로직이 실행되지 않는다.
    @MockBean
    private PointService pointService;

    // 테스트 데이터 상수
    private static final long TEST_USER_ID = 1L;     // 테스트용ID
    private static final long INITIAL_POINT = 1000L; // 포인트
    private static final long CHARGE_AMOUNT = 500L;  // 충전금액
    private static final long USE_AMOUNT = 300L;     // 사용금액

    /**
     * 포인트 조회 API 테스트
     */
    @Nested // 테스트 클래스 안에 또 다른 테스트 클래스를 중첩시킬 수 있게 해주는 어노테이션이에요.
            // “기능 단위로 테스트를 묶고, 각 케이스를 하위로 정리”할 수 있다.
    @DisplayName("포인트 조회 API")
    class GetPointTests {

        @Test // 테스트를 나타내는 Junit5어노테이션
        @DisplayName("특정 유저의 포인트를 조회한다")
        void getUserPoint() throws Exception {
            // given
            // 테스트 유저아이디, 포인트,
            UserPoint expectedPoint = new UserPoint(TEST_USER_ID, INITIAL_POINT, System.currentTimeMillis());
            // given(가짜로 동작시킬 메서드 호출)
            // getUserPoint(테스트유저아이디로 포인트를 조회하면) >> 포인트를 보여준다
            given(pointService.getUserPoint(TEST_USER_ID)).willReturn(expectedPoint);

            // when & then
            // mockMvc를 이용한 컨트롤러 테스트
            // id를 매개변수로받는 point api호출
            mockMvc.perform(get("/point/{id}", TEST_USER_ID))
                    .andExpect(status().isOk()) // OK라면(HTTP 상태코드 200)
                    .andExpect(jsonPath("$.id").value(TEST_USER_ID)) // JSON응답값 중 id필드가 TEST_USER_ID와 동일한지 확인
                    .andExpect(jsonPath("$.point").value(INITIAL_POINT)) // JSON응답값 중 point필드가 INITIAL_POINT와 동일한지 확인
                    .andExpect(jsonPath("$.updateMillis").exists()); // JSON 응답에 updateMillis 필드가 존재하는지 확인
        }

        /**
         * 존재하지 않는 유저의 포인트 조회 테스트
         *
         * 목적: 시스템에 등록되지 않은 유저 ID로 포인트를 조회했을 때
         *      에러가 발생하지 않고 0 포인트를 가진 빈 객체를 반환하는지 확인
         */
        @Test // JUnit5의 테스트 메서드임을 표시
        @DisplayName("존재하지 않는 유저의 포인트 조회 시 0 포인트를 반환한다")
        void getPointForNonExistentUser() throws Exception {
            // ===== given (준비 단계): 테스트에 필요한 데이터 준비 =====

            // 1. 존재하지 않는 유저 ID 설정
            //    실제 DB에 없는 ID를 가정 (999L)
            long nonExistentUserId = 999L;

            // 2. 빈 포인트 객체 생성
            //    UserPoint.empty()는 id만 있고 포인트는 0인 객체를 반환하는 정적 메서드
            UserPoint emptyPoint = UserPoint.empty(nonExistentUserId);

            // 3. Mock 설정: PointService의 동작을 가짜로 정의
            //    "pointService.getUserPoint(999L)가 호출되면 emptyPoint를 반환하라"
            given(pointService.getUserPoint(nonExistentUserId)).willReturn(emptyPoint);

            // ===== when & then (실행 및 검증): API 호출 후 결과 확인 =====
            // when: GET /point/999 API 호출 (존재하지 않는 유저)
            mockMvc.perform(get("/point/{id}", nonExistentUserId))
                    // then: 응답 검증 시작
                    // 1. HTTP 상태 코드가 200 OK인지 확인
                    .andExpect(status().isOk())
                    // 2. JSON 응답의 id 필드가 요청한 유저 ID와 같은지 확인
                    //    $.id는 JSON 응답의 최상위 id 필드를 의미
                    .andExpect(jsonPath("$.id").value(nonExistentUserId))
                    // 3. JSON 응답의 point 필드가 0인지 확인
                    //    신규 유저는 포인트가 0이어야 함
                    .andExpect(jsonPath("$.point").value(0L));

            // 테스트 통과 조건:
            // - 존재하지 않는 유저 조회 시 예외가 발생하지 않음
            // - 0 포인트를 가진 유저 정보가 정상적으로 반환됨
            // - 이후 해당 유저가 포인트를 충전하면 DB에 저장됨
        }
    }

    /**
     * 포인트 충전 API 테스트
     */
    @Nested
    @DisplayName("포인트 충전 API")
    class ChargePointTests {

        @Test
        @DisplayName("정상적으로 포인트를 충전한다")
        void chargePoint() throws Exception {
            // given
            // 예상가는 충전결과를 유저포인트로 생성
            UserPoint expectedPoint = new UserPoint(TEST_USER_ID, CHARGE_AMOUNT, System.currentTimeMillis());
            // 포인트 충전 >> Service호출 시 예상동작
            given(pointService.chargePoint(TEST_USER_ID, INITIAL_POINT)).willReturn(expectedPoint);

            // when & then
            mockMvc.perform(patch("/point/{id}/charge", TEST_USER_ID) // patch로 포인트 충전 API호출
                            .contentType(MediaType.APPLICATION_JSON) // JSON타입 지정
                            .content(String.valueOf(INITIAL_POINT))) // 충전금액
                    .andExpect(status().isOk()) // 응답이 200일 경우 >>> OK
                    .andExpect(jsonPath("$.id").value(TEST_USER_ID)) // JSON응답 id가 동일한지
                    .andExpect(jsonPath("$.point").value(INITIAL_POINT)) //JSON응답 point가 동일한지
                    .andExpect(jsonPath("$.updateMillis").exists()); //JSON응답 업데이트시간이 존재하는지
        }

        @Test
        @DisplayName("매우 큰 포인트 충전이 가능하다")
        void chargeLargeAmount() throws Exception {
            // given
            long largeAmount = 1_000_000L; // 큰 포인트 선언
            // 예상가는 충전결과를 유저포인트로 생성
            UserPoint expectedPoint = new UserPoint(TEST_USER_ID, largeAmount, System.currentTimeMillis());
            // 포인트 충전 Service실행시의 예상 값을 설정 >> stub(미리 정해진 값)
            given(pointService.chargePoint(TEST_USER_ID, largeAmount)).willReturn(expectedPoint);

            // when & then
            mockMvc.perform(patch("/point/{id}/charge", TEST_USER_ID) // patch로 포인트 충전 API호출
                            .contentType(MediaType.APPLICATION_JSON) // JSON타입 지정
                            .content(String.valueOf(largeAmount)))   // 충전금액
                    .andExpect(status().isOk()) // 응답이 200일 경우
                    .andExpect(jsonPath("$.point").value(largeAmount)); // JSON응답 point가 큰 포인트와 같은지
        }

        @Test // 테스트 선언 어노테이션
        @DisplayName("연속으로 포인트를 충전한다")
        void chargePointConsecutively() throws Exception {
            // given
            long amount1 = 1000L; // 첫번째 충전 금액
            long amount2 = 500L;  // 두번째 충전 금액
            // 첫범째 금액 충전 후의 예상
            UserPoint afterFirstCharge = new UserPoint(TEST_USER_ID, amount1, System.currentTimeMillis());
            // 두번째 금액 충전 후의 예상
            UserPoint afterSecondCharge = new UserPoint(TEST_USER_ID, amount1 + amount2, System.currentTimeMillis());

            // 첫번째 충전 요청 Service가 반환할 값을 저장
            given(pointService.chargePoint(TEST_USER_ID, amount1)).willReturn(afterFirstCharge);
            // 두번째 충전 요청 Service가 반환할 값을 저장
            given(pointService.chargePoint(TEST_USER_ID, amount2)).willReturn(afterSecondCharge);

            // when & then - 첫 번째 충전
            mockMvc.perform(patch("/point/{id}/charge", TEST_USER_ID) // patch로 포인트 충전 API를 호출
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.valueOf(amount1)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.point").value(amount1));

            // when & then - 두 번째 충전
            mockMvc.perform(patch("/point/{id}/charge", TEST_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.valueOf(amount2)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.point").value(amount1 + amount2));
        }

        @Test
        @DisplayName("음수 포인트 충전 시도 시 실패한다")
        void chargePointWithNegativeAmount() throws Exception {
            // given
            long negativeAmount = -1000L; // 음수금액 설정
            //
            given(pointService.chargePoint(TEST_USER_ID, negativeAmount))
                    .willThrow(new IllegalArgumentException("포인트는 0보다 커야 합니다."));

            // when & then
            mockMvc.perform(patch("/point/{id}/charge", TEST_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.valueOf(negativeAmount)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("0 포인트 충전 시도 시 실패한다")
        void chargePointWithZeroAmount() throws Exception {
            // given
            long zeroAmount = 0L;
            given(pointService.chargePoint(TEST_USER_ID, zeroAmount))
                    .willThrow(new IllegalArgumentException("포인트는 0보다 커야 합니다."));

            // when & then
            mockMvc.perform(patch("/point/{id}/charge", TEST_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.valueOf(zeroAmount)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("포인트는 0보다 커야 합니다."));
        }
    }

    /**
     * 포인트 사용 API 테스트
     */
    @Nested
    @DisplayName("포인트 사용 API")
    class UsePointTests {

        @Test
        @DisplayName("정상적으로 포인트를 사용한다")
        void usePoint() throws Exception {
            // given
            UserPoint expectedPoint = new UserPoint(TEST_USER_ID, CHARGE_AMOUNT, System.currentTimeMillis());
            given(pointService.usePoint(TEST_USER_ID, CHARGE_AMOUNT)).willReturn(expectedPoint);

            // when & then
            mockMvc.perform(patch("/point/{id}/use", TEST_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.valueOf(CHARGE_AMOUNT)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(TEST_USER_ID))
                    .andExpect(jsonPath("$.point").value(CHARGE_AMOUNT))
                    .andExpect(jsonPath("$.updateMillis").exists());
        }

        @Test
        @DisplayName("음수 포인트 사용 시도 시 실패한다")
        void usePointWithNegativeAmount() throws Exception {
            // given
            long negativeAmount = -500L;
            given(pointService.usePoint(TEST_USER_ID, negativeAmount))
                    .willThrow(new IllegalArgumentException("포인트는 0보다 커야 합니다."));

            // when & then
            mockMvc.perform(patch("/point/{id}/use", TEST_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.valueOf(negativeAmount)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("0 포인트 사용 시도 시 실패한다")
        void usePointWithZeroAmount() throws Exception {
            // given
            long zeroAmount = 0L;
            given(pointService.usePoint(TEST_USER_ID, zeroAmount))
                    .willThrow(new IllegalArgumentException("포인트는 0보다 커야 합니다."));

            // when & then
            mockMvc.perform(patch("/point/{id}/use", TEST_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.valueOf(zeroAmount)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("포인트는 0보다 커야 합니다."));
        }

        @Test
        @DisplayName("잔액 부족 시 포인트 사용이 실패한다")
        void usePointWhenInsufficientBalance() throws Exception {
            // given
            long excessiveAmount = 5000L;
            given(pointService.usePoint(TEST_USER_ID, excessiveAmount))
                    .willThrow(new IllegalArgumentException("포인트 잔액이 부족합니다."));

            // when & then
            mockMvc.perform(patch("/point/{id}/use", TEST_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.valueOf(excessiveAmount)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("400"))
                    .andExpect(jsonPath("$.message").value("포인트 잔액이 부족합니다."));
        }
    }

    /**
     * 포인트 히스토리 조회 API 테스트
     */
    @Nested
    @DisplayName("포인트 히스토리 조회 API")
    class GetPointHistoryTests {

        @Test
        @DisplayName("특정 유저의 포인트 내역을 조회한다")
        void getPointHistory() throws Exception {
            // given
            List<PointHistory> expectedHistories = List.of(
                    new PointHistory(1L, TEST_USER_ID, INITIAL_POINT, TransactionType.CHARGE, System.currentTimeMillis())
            );
            given(pointService.getPointHistories(TEST_USER_ID)).willReturn(expectedHistories);

            // when & then
            mockMvc.perform(get("/point/{id}/histories", TEST_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].userId").value(TEST_USER_ID))
                    .andExpect(jsonPath("$[0].amount").value(INITIAL_POINT));
        }

        @Test
        @DisplayName("포인트 히스토리가 없는 유저 조회 시 빈 배열을 반환한다")
        void getEmptyHistoryForUser() throws Exception {
            // given
            given(pointService.getPointHistories(TEST_USER_ID)).willReturn(List.of());

            // when & then
            mockMvc.perform(get("/point/{id}/histories", TEST_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }

        @Test
        @DisplayName("여러 건의 포인트 히스토리를 조회한다")
        void getMultipleHistories() throws Exception {
            // given
            List<PointHistory> expectedHistories = List.of(
                    new PointHistory(1L, TEST_USER_ID, 1000L, TransactionType.CHARGE, System.currentTimeMillis()),
                    new PointHistory(2L, TEST_USER_ID, 500L, TransactionType.USE, System.currentTimeMillis()),
                    new PointHistory(3L, TEST_USER_ID, 2000L, TransactionType.CHARGE, System.currentTimeMillis())
            );
            given(pointService.getPointHistories(TEST_USER_ID)).willReturn(expectedHistories);

            // when & then
            mockMvc.perform(get("/point/{id}/histories", TEST_USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[0].type").value("CHARGE"))
                    .andExpect(jsonPath("$[1].type").value("USE"))
                    .andExpect(jsonPath("$[2].type").value("CHARGE"));
        }
    }
}
