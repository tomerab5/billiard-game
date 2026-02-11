package com.billiardgame.physics;

import com.billiardgame.game.Ball;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PhysicsWorldTest {
    private static final TableBounds LARGE_BOUNDS = new TableBounds(-10_000, 10_000, -10_000, 10_000);

    @Test
    void velocityMagnitudeDecreasesAfterStepping() {
        PhysicsWorld world = new PhysicsWorld(List.of(new Ball(new Vector2(0, 0), 12)), LARGE_BOUNDS);
        world.setCueBallVelocity(new Vector2(100, 0));

        double speedBefore = world.cueBallSpeed();
        world.step(1.0 / 60.0);
        double speedAfter = world.cueBallSpeed();

        assertTrue(speedAfter < speedBefore);
        assertTrue(speedAfter > 0);
    }

    @Test
    void eventuallyStopsAfterEnoughSteps() {
        PhysicsWorld world = new PhysicsWorld(List.of(new Ball(new Vector2(0, 0), 12)), LARGE_BOUNDS);
        world.setCueBallVelocity(new Vector2(800, 0));

        for (int i = 0; i < 2000; i++) {
            world.step(1.0 / 120.0);
        }

        assertEquals(0.0, world.cueBallSpeed());
    }

    @Test
    void headOnEqualMassesApproximatelySwapVelocities() {
        Ball a = new Ball(new Vector2(0, 0), 12);
        Ball b = new Ball(new Vector2(24, 0), 12);
        PhysicsWorld world = new PhysicsWorld(List.of(a, b), LARGE_BOUNDS);
        world.setBallVelocity(0, new Vector2(100, 0));
        world.setBallVelocity(1, Vector2.ZERO);

        world.step(1.0 / 120.0);

        assertTrue(world.ballVelocity(0).x() < 5.0);
        assertTrue(world.ballVelocity(1).x() > 50.0);
    }

    @Test
    void overlappingStationaryBallsGetSeparatedAfterStep() {
        Ball a = new Ball(new Vector2(0, 0), 12);
        Ball b = new Ball(new Vector2(10, 0), 12);
        PhysicsWorld world = new PhysicsWorld(List.of(a, b), LARGE_BOUNDS);

        world.step(1.0 / 120.0);

        Vector2 delta = world.ball(1).position().sub(world.ball(0).position());
        double minDistance = world.ball(0).radius() + world.ball(1).radius();
        assertTrue(delta.length() >= minDistance - 1e-9);
    }

    @Test
    void rightWallBounceFlipsVelocityNegative() {
        TableBounds bounds = new TableBounds(0, 100, 0, 100);
        PhysicsWorld world = new PhysicsWorld(List.of(new Ball(new Vector2(88, 50), 10)), bounds);
        world.setCueBallVelocity(new Vector2(200, 0));

        world.step(1.0 / 60.0);

        assertTrue(world.ballVelocity(0).x() < 0);
    }

    @Test
    void ballStaysWithinBoundsAfterStep() {
        TableBounds bounds = new TableBounds(0, 100, 0, 100);
        Ball ball = new Ball(new Vector2(95, 95), 10);
        PhysicsWorld world = new PhysicsWorld(List.of(ball), bounds);
        world.setCueBallVelocity(new Vector2(500, 500));

        world.step(1.0 / 60.0);

        Ball stepped = world.ball(0);
        assertTrue(stepped.position().x() - stepped.radius() >= bounds.left());
        assertTrue(stepped.position().x() + stepped.radius() <= bounds.right());
        assertTrue(stepped.position().y() - stepped.radius() >= bounds.top());
        assertTrue(stepped.position().y() + stepped.radius() <= bounds.bottom());
    }
}
