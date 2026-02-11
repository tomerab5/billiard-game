package com.billiardgame.game;

import com.billiardgame.physics.Vector2;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class BallTest {

    @Test
    void keepsPositionAndRadius() {
        Ball ball = new Ball(new Vector2(10, 20), 12);

        assertEquals(10, ball.position().x());
        assertEquals(20, ball.position().y());
        assertEquals(12, ball.radius());
    }
}