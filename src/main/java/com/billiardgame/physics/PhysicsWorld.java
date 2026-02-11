package com.billiardgame.physics;

import com.billiardgame.game.Ball;

import java.util.ArrayList;
import java.util.List;

public final class PhysicsWorld {
    private final List<BallBody> balls;

    public PhysicsWorld(List<Ball> initialBalls) {
        balls = new ArrayList<>();
        for (Ball initialBall : initialBalls) {
            balls.add(new BallBody(initialBall, Vector2.ZERO));
        }
    }

    public List<Ball> balls() {
        List<Ball> snapshot = new ArrayList<>(balls.size());
        for (BallBody ballBody : balls) {
            snapshot.add(ballBody.ball);
        }
        return List.copyOf(snapshot);
    }

    public Ball cueBall() {
        if (balls.isEmpty()) {
            throw new IllegalStateException("No balls in world");
        }
        return balls.get(0).ball;
    }

    public void setCueBallVelocity(Vector2 velocity) {
        if (balls.isEmpty()) {
            throw new IllegalStateException("No balls in world");
        }
        balls.get(0).velocity = velocity;
    }

    public double cueBallSpeed() {
        if (balls.isEmpty()) {
            throw new IllegalStateException("No balls in world");
        }
        return balls.get(0).velocity.length();
    }

    public void step(double dtSeconds) {
        if (dtSeconds <= 0) {
            throw new IllegalArgumentException("dtSeconds must be positive");
        }

        double decay = Math.max(0.0, 1.0 - (PhysicsConstants.FRICTION_PER_SEC * dtSeconds));

        for (BallBody ballBody : balls) {
            Vector2 nextPosition = ballBody.ball.position().add(ballBody.velocity.mul(dtSeconds));
            Vector2 nextVelocity = ballBody.velocity.mul(decay);

            if (nextVelocity.length() < PhysicsConstants.STOP_EPS) {
                nextVelocity = Vector2.ZERO;
            }

            ballBody.ball = new Ball(nextPosition, ballBody.ball.radius());
            ballBody.velocity = nextVelocity;
        }
    }

    private static final class BallBody {
        private Ball ball;
        private Vector2 velocity;

        private BallBody(Ball ball, Vector2 velocity) {
            this.ball = ball;
            this.velocity = velocity;
        }
    }
}
