package com.billiardgame.physics;

import com.billiardgame.game.Ball;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PhysicsWorldTest {

    @Test
    void velocityMagnitudeDecreasesAfterStepping() {
        PhysicsWorld world = new PhysicsWorld(List.of(new Ball(new Vector2(0, 0), 12)));
        world.setCueBallVelocity(new Vector2(100, 0));

        double speedBefore = world.cueBallSpeed();
        world.step(1.0 / 60.0);
        double speedAfter = world.cueBallSpeed();

        assertTrue(speedAfter < speedBefore);
        assertTrue(speedAfter > 0);
    }

    @Test
    void eventuallyStopsAfterEnoughSteps() {
        PhysicsWorld world = new PhysicsWorld(List.of(new Ball(new Vector2(0, 0), 12)));
        world.setCueBallVelocity(new Vector2(800, 0));

        for (int i = 0; i < 2000; i++) {
            world.step(1.0 / 120.0);
        }

        assertEquals(0.0, world.cueBallSpeed());
    }
}