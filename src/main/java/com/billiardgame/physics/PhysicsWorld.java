package com.billiardgame.physics;

import com.billiardgame.game.Ball;

import java.util.ArrayList;
import java.util.List;

public final class PhysicsWorld {
    private static final double BALL_MASS = 1.0;
    private static final double COLLISION_EPS = 1e-9;

    private final List<BallBody> balls;
    private final TableBounds bounds;

    public PhysicsWorld(List<Ball> initialBalls, TableBounds bounds) {
        balls = new ArrayList<>();
        for (Ball initialBall : initialBalls) {
            balls.add(new BallBody(initialBall, Vector2.ZERO));
        }
        this.bounds = bounds;
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

    public Ball ball(int index) {
        return balls.get(index).ball;
    }

    public void setCueBallVelocity(Vector2 velocity) {
        if (balls.isEmpty()) {
            throw new IllegalStateException("No balls in world");
        }
        balls.get(0).velocity = velocity;
    }

    public void setBallVelocity(int index, Vector2 velocity) {
        balls.get(index).velocity = velocity;
    }

    public Vector2 ballVelocity(int index) {
        return balls.get(index).velocity;
    }

    public double cueBallSpeed() {
        if (balls.isEmpty()) {
            throw new IllegalStateException("No balls in world");
        }
        return balls.get(0).velocity.length();
    }

    public boolean allBallsNearlyStopped() {
        for (BallBody ballBody : balls) {
            if (ballBody.velocity.length() >= PhysicsConstants.STOP_EPS_PX_PER_S) {
                return false;
            }
        }
        return true;
    }

    public void step(double dtSeconds) {
        if (dtSeconds <= 0) {
            throw new IllegalArgumentException("dtSeconds must be positive");
        }

        for (BallBody ballBody : balls) {
            ballBody.ball = new Ball(ballBody.ball.position().add(ballBody.velocity.mul(dtSeconds)), ballBody.ball.radius());
            resolveRailCollision(ballBody);
        }

        resolveBallCollisions();
        for (BallBody ballBody : balls) {
            resolveRailCollision(ballBody);
        }

        for (BallBody ballBody : balls) {
            double speed = ballBody.velocity.length();
            if (speed <= PhysicsConstants.STOP_EPS_PX_PER_S) {
                ballBody.velocity = Vector2.ZERO;
                continue;
            }

            double decel = speed > PhysicsConstants.ROLLING_THRESHOLD_PX_PER_S
                    ? PhysicsConstants.SLIDING_DECEL_PX_PER_S2
                    : PhysicsConstants.ROLLING_DECEL_PX_PER_S2;
            double newSpeed = Math.max(0.0, speed - (decel * dtSeconds));
            ballBody.velocity = ballBody.velocity.normalized().mul(newSpeed);
        }
    }

    private void resolveRailCollision(BallBody ballBody) {
        double r = ballBody.ball.radius();
        double x = ballBody.ball.position().x();
        double y = ballBody.ball.position().y();
        double vx = ballBody.velocity.x();
        double vy = ballBody.velocity.y();

        if (x + r > bounds.right()) {
            x = bounds.right() - r;
            if (vx > 0) {
                vx = -vx * PhysicsConstants.RAIL_RESTITUTION;
            }
        }
        if (x - r < bounds.left()) {
            x = bounds.left() + r;
            if (vx < 0) {
                vx = -vx * PhysicsConstants.RAIL_RESTITUTION;
            }
        }
        if (y + r > bounds.bottom()) {
            y = bounds.bottom() - r;
            if (vy > 0) {
                vy = -vy * PhysicsConstants.RAIL_RESTITUTION;
            }
        }
        if (y - r < bounds.top()) {
            y = bounds.top() + r;
            if (vy < 0) {
                vy = -vy * PhysicsConstants.RAIL_RESTITUTION;
            }
        }

        ballBody.ball = new Ball(new Vector2(x, y), r);
        ballBody.velocity = new Vector2(vx, vy);
    }

    private void resolveBallCollisions() {
        for (int i = 0; i < balls.size() - 1; i++) {
            BallBody a = balls.get(i);
            for (int j = i + 1; j < balls.size(); j++) {
                BallBody b = balls.get(j);

                Vector2 delta = b.ball.position().sub(a.ball.position());
                double minDistance = a.ball.radius() + b.ball.radius();
                double minDistanceSq = minDistance * minDistance;
                double distSq = delta.lengthSq();
                if (distSq > minDistanceSq) {
                    continue;
                }

                double distance = Math.sqrt(Math.max(distSq, 0.0));
                Vector2 normal;
                if (distance > COLLISION_EPS) {
                    normal = delta.mul(1.0 / distance);
                } else {
                    normal = new Vector2(1.0, 0.0);
                    distance = 0.0;
                }

                double penetration = minDistance - distance;
                Vector2 correction = normal.mul(penetration * 0.5);
                a.ball = new Ball(a.ball.position().sub(correction), a.ball.radius());
                b.ball = new Ball(b.ball.position().add(correction), b.ball.radius());

                Vector2 relativeVelocity = b.velocity.sub(a.velocity);
                double velAlongNormal = (relativeVelocity.x() * normal.x()) + (relativeVelocity.y() * normal.y());
                if (velAlongNormal >= 0.0) {
                    continue;
                }

                double impulseMagnitude = -(1.0 + PhysicsConstants.RESTITUTION) * velAlongNormal;
                impulseMagnitude /= (1.0 / BALL_MASS) + (1.0 / BALL_MASS);

                Vector2 impulse = normal.mul(impulseMagnitude);
                a.velocity = a.velocity.sub(impulse.mul(1.0 / BALL_MASS));
                b.velocity = b.velocity.add(impulse.mul(1.0 / BALL_MASS));
            }
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
