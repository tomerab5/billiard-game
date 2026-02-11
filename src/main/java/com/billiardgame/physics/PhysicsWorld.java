package com.billiardgame.physics;

import com.billiardgame.game.Ball;

import java.util.ArrayList;
import java.util.List;

public final class PhysicsWorld {
    private static final double COLLISION_EPS = 1e-9;

    public enum MotionMode {
        SLIDING,
        ROLLING
    }

    private final List<BallBody> balls;
    private final TableBounds bounds;
    private final double pixelsPerMeter;

    public PhysicsWorld(List<Ball> initialBalls, TableBounds bounds) {
        this(initialBalls, bounds, 1.0);
    }

    public PhysicsWorld(List<Ball> initialBalls, TableBounds bounds, double pixelsPerMeter) {
        if (pixelsPerMeter <= 0) {
            throw new IllegalArgumentException("pixelsPerMeter must be positive");
        }
        this.pixelsPerMeter = pixelsPerMeter;
        balls = new ArrayList<>();
        for (Ball initialBall : initialBalls) {
            balls.add(new BallBody(
                    toMeters(initialBall.position()),
                    Vector2.ZERO,
                    Vector3.ZERO,
                    toMeters(initialBall.radius()),
                    PhysicsConfig.BALL_MASS_KG
            ));
        }
        this.bounds = new TableBounds(
                toMeters(bounds.left()),
                toMeters(bounds.right()),
                toMeters(bounds.top()),
                toMeters(bounds.bottom())
        );
    }

    public List<Ball> balls() {
        List<Ball> snapshot = new ArrayList<>(balls.size());
        for (BallBody ballBody : balls) {
            snapshot.add(toRenderBall(ballBody));
        }
        return List.copyOf(snapshot);
    }

    public Ball cueBall() {
        if (balls.isEmpty()) {
            throw new IllegalStateException("No balls in world");
        }
        return toRenderBall(balls.get(0));
    }

    public Ball ball(int index) {
        return toRenderBall(balls.get(index));
    }

    public void setCueBallVelocity(Vector2 velocity) {
        if (balls.isEmpty()) {
            throw new IllegalStateException("No balls in world");
        }
        balls.get(0).velocity = toMeters(velocity);
    }

    public void setBallVelocity(int index, Vector2 velocity) {
        balls.get(index).velocity = toMeters(velocity);
    }

    public Vector2 ballVelocity(int index) {
        return toPixels(balls.get(index).velocity);
    }

    public void setBallAngularVelocity(int index, Vector3 angularVelocity) {
        balls.get(index).angularVelocity = angularVelocity;
    }

    public Vector3 ballAngularVelocity(int index) {
        return balls.get(index).angularVelocity;
    }

    public MotionMode cueBallMotionMode() {
        if (balls.isEmpty()) {
            throw new IllegalStateException("No balls in world");
        }
        return balls.get(0).mode;
    }

    public double cueBallSpeed() {
        if (balls.isEmpty()) {
            throw new IllegalStateException("No balls in world");
        }
        return toPixels(balls.get(0).velocity).length();
    }

    public boolean allBallsNearlyStopped() {
        for (BallBody ballBody : balls) {
            if (ballBody.velocity.length() >= PhysicsConfig.STOP_EPS_M_PER_S) {
                return false;
            }
            if (Math.abs(ballBody.angularVelocity.z()) >= PhysicsConfig.STOP_SPIN_EPS_RAD_PER_S) {
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
            ballBody.position = ballBody.position.add(ballBody.velocity.mul(dtSeconds));
            resolveRailCollision(ballBody);
        }

        resolveBallCollisions();
        for (BallBody ballBody : balls) {
            resolveRailCollision(ballBody);
        }

        for (BallBody ballBody : balls) {
            applyClothInteraction(ballBody, dtSeconds);
        }
    }

    private void resolveRailCollision(BallBody ballBody) {
        double r = ballBody.radius;
        double x = ballBody.position.x();
        double y = ballBody.position.y();
        double vx = ballBody.velocity.x();
        double vy = ballBody.velocity.y();

        if (x + r > bounds.right()) {
            x = bounds.right() - r;
            if (vx > 0) {
                vx = -vx * PhysicsConfig.RAIL_RESTITUTION;
            }
        }
        if (x - r < bounds.left()) {
            x = bounds.left() + r;
            if (vx < 0) {
                vx = -vx * PhysicsConfig.RAIL_RESTITUTION;
            }
        }
        if (y + r > bounds.bottom()) {
            y = bounds.bottom() - r;
            if (vy > 0) {
                vy = -vy * PhysicsConfig.RAIL_RESTITUTION;
            }
        }
        if (y - r < bounds.top()) {
            y = bounds.top() + r;
            if (vy < 0) {
                vy = -vy * PhysicsConfig.RAIL_RESTITUTION;
            }
        }

        ballBody.position = new Vector2(x, y);
        ballBody.velocity = new Vector2(vx, vy);
    }

    private void resolveBallCollisions() {
        for (int i = 0; i < balls.size() - 1; i++) {
            BallBody a = balls.get(i);
            for (int j = i + 1; j < balls.size(); j++) {
                BallBody b = balls.get(j);

                Vector2 delta = b.position.sub(a.position);
                double minDistance = a.radius + b.radius;
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
                a.position = a.position.sub(correction);
                b.position = b.position.add(correction);

                Vector2 relativeVelocity = b.velocity.sub(a.velocity);
                double velAlongNormal = (relativeVelocity.x() * normal.x()) + (relativeVelocity.y() * normal.y());
                if (velAlongNormal >= 0.0) {
                    continue;
                }

                double impulseMagnitude = -(1.0 + PhysicsConfig.BALL_BALL_RESTITUTION) * velAlongNormal;
                impulseMagnitude /= (1.0 / a.mass) + (1.0 / b.mass);

                Vector2 impulse = normal.mul(impulseMagnitude);
                a.velocity = a.velocity.sub(impulse.mul(1.0 / a.mass));
                b.velocity = b.velocity.add(impulse.mul(1.0 / b.mass));
            }
        }
    }

    private void applyClothInteraction(BallBody ballBody, double dtSeconds) {
        Vector2 v = ballBody.velocity;
        Vector3 w = ballBody.angularVelocity;

        Vector2 slipVelocity = v.add(new Vector2(-ballBody.radius * w.y(), ballBody.radius * w.x()));
        double slipSpeed = slipVelocity.length();
        double speed = v.length();

        if (speed <= PhysicsConfig.STOP_EPS_M_PER_S) {
            ballBody.velocity = Vector2.ZERO;
            double wz = applySpinDecay(w.z(), dtSeconds);
            if (Math.abs(wz) <= PhysicsConfig.STOP_SPIN_EPS_RAD_PER_S) {
                wz = 0.0;
            }
            ballBody.angularVelocity = new Vector3(0, 0, wz);
            ballBody.mode = MotionMode.ROLLING;
            return;
        }

        if (slipSpeed > PhysicsConfig.SLIP_EPS_M_PER_S) {
            Vector2 uHat = slipVelocity.normalized();
            double aMag = PhysicsConfig.MU_SLIDING * PhysicsConfig.G_M_PER_S2;
            Vector2 nextVelocity = v.add(uHat.mul(-aMag * dtSeconds));

            double alphaFactor = (5.0 * PhysicsConfig.MU_SLIDING * PhysicsConfig.G_M_PER_S2) / (2.0 * ballBody.radius);
            double wx = w.x() - (alphaFactor * uHat.y() * dtSeconds);
            double wy = w.y() + (alphaFactor * uHat.x() * dtSeconds);
            double wz = applySpinDecay(w.z(), dtSeconds);
            Vector3 nextAngularVelocity = new Vector3(wx, wy, wz);

            Vector2 nextSlip = nextVelocity.add(new Vector2(-ballBody.radius * nextAngularVelocity.y(), ballBody.radius * nextAngularVelocity.x()));
            if (nextSlip.length() <= (PhysicsConfig.SLIP_EPS_M_PER_S * 1.5) || nextVelocity.length() <= 0.35) {
                double rollingSpeed = nextVelocity.length();
                Vector2 rollingVelocity = rollingSpeed > 0.0 ? nextVelocity.normalized().mul(rollingSpeed) : Vector2.ZERO;
                ballBody.velocity = rollingVelocity;
                if (rollingSpeed > PhysicsConfig.STOP_EPS_M_PER_S) {
                    ballBody.angularVelocity = new Vector3(
                            -rollingVelocity.y() / ballBody.radius,
                            rollingVelocity.x() / ballBody.radius,
                            wz
                    );
                } else {
                    ballBody.angularVelocity = new Vector3(0, 0, wz);
                }
                ballBody.mode = MotionMode.ROLLING;
                return;
            }

            ballBody.velocity = nextVelocity;
            ballBody.angularVelocity = nextAngularVelocity;
            ballBody.mode = MotionMode.SLIDING;
            return;
        }

        if (speed > 0.0) {
            double aRoll = PhysicsConfig.MU_ROLLING * PhysicsConfig.G_M_PER_S2;
            double newSpeed = Math.max(0.0, speed - (aRoll * dtSeconds));
            ballBody.velocity = v.normalized().mul(newSpeed);
        } else {
            ballBody.velocity = Vector2.ZERO;
        }

        if (ballBody.velocity.length() > PhysicsConfig.STOP_EPS_M_PER_S) {
            double wx = -ballBody.velocity.y() / ballBody.radius;
            double wy = ballBody.velocity.x() / ballBody.radius;
            ballBody.angularVelocity = new Vector3(wx, wy, applySpinDecay(w.z(), dtSeconds));
        } else {
            ballBody.angularVelocity = new Vector3(0, 0, applySpinDecay(w.z(), dtSeconds));
        }
        ballBody.mode = MotionMode.ROLLING;
    }

    private static double applySpinDecay(double wz, double dtSeconds) {
        double decayed = Math.abs(wz) - (PhysicsConfig.SPIN_DECAY_RAD_PER_S2 * dtSeconds);
        if (decayed <= 0.0) {
            return 0.0;
        }
        return Math.copySign(decayed, wz);
    }

    private Ball toRenderBall(BallBody body) {
        return new Ball(toPixels(body.position), toPixels(body.radius));
    }

    private Vector2 toMeters(Vector2 valuePx) {
        return valuePx.mul(1.0 / pixelsPerMeter);
    }

    private Vector2 toPixels(Vector2 valueM) {
        return valueM.mul(pixelsPerMeter);
    }

    private double toMeters(double valuePx) {
        return valuePx / pixelsPerMeter;
    }

    private double toPixels(double valueM) {
        return valueM * pixelsPerMeter;
    }

    private static final class BallBody {
        private Vector2 position;
        private Vector2 velocity;
        private Vector3 angularVelocity;
        private final double radius;
        private final double mass;
        private MotionMode mode;

        private BallBody(Vector2 position, Vector2 velocity, Vector3 angularVelocity, double radius, double mass) {
            this.position = position;
            this.velocity = velocity;
            this.angularVelocity = angularVelocity;
            this.radius = radius;
            this.mass = mass;
            this.mode = MotionMode.SLIDING;
        }
    }
}
