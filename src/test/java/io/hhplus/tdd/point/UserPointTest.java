package io.hhplus.tdd.point;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserPoint 테스트")
class UserPointTest {

    @Test
    @DisplayName("UserPoint 생성 테스트")
    void createUserPoint() {
        // given
        long id = 1L;
        long point = 1000L;
        long updateMillis = System.currentTimeMillis();

        // when
        UserPoint userPoint = new UserPoint(id, point, updateMillis);

        // then
        assertThat(userPoint.id()).isEqualTo(id);
        assertThat(userPoint.point()).isEqualTo(point);
        assertThat(userPoint.updateMillis()).isEqualTo(updateMillis);
    }

    @Test
    @DisplayName("빈 UserPoint 생성 테스트")
    void createEmptyUserPoint() {
        // given
        long id = 1L;

        // when
        UserPoint userPoint = UserPoint.empty(id);

        // then
        assertThat(userPoint.id()).isEqualTo(id);
        assertThat(userPoint.point()).isEqualTo(0L);
        assertThat(userPoint.updateMillis()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("UserPoint 동등성 테스트")
    void userPointEquality() {
        // given
        long updateMillis = System.currentTimeMillis();
        UserPoint userPoint1 = new UserPoint(1L, 1000L, updateMillis);
        UserPoint userPoint2 = new UserPoint(1L, 1000L, updateMillis);

        // when & then
        assertThat(userPoint1).isEqualTo(userPoint2);
    }

    @Test
    @DisplayName("UserPoint 불변성 테스트")
    void userPointImmutability() {
        // given
        long id = 1L;
        long point = 1000L;
        long updateMillis = System.currentTimeMillis();

        // when
        UserPoint userPoint = new UserPoint(id, point, updateMillis);

        // then
        // record는 불변 객체이므로 getter만 제공
        assertThat(userPoint.id()).isEqualTo(id);
        assertThat(userPoint.point()).isEqualTo(point);
        assertThat(userPoint.updateMillis()).isEqualTo(updateMillis);
    }
}