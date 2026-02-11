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
    private double muRolling;

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
        this.muRolling = PhysicsConfig.DEFAULT_MU_ROLLING;
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

    public void strikeCueBall(Vector2 directionPx, double speedPxPerSec, Vector2 tipOffsetNorm) {
        if (balls.isEmpty()) {
            throw new IllegalStateException("No balls in world");
        }
        if (directionPx.length() <= 1e-9 || speedPxPerSec <= 0.0) {
            return;
        }

        BallBody cue = balls.get(0);
        Vector2 dir = directionPx.normalized();
        double speedMps = speedPxPerSec / pixelsPerMeter;
        Vector3 impulse = toVec3(dir.mul(cue.mass * speedMps));

        Vector2 left = new Vector2(-dir.y(), dir.x());
        double rawDx = tipOffsetNorm.x();
        double rawDy = tipOffsetNorm.y();
        double tangential = Math.sqrt((rawDx * rawDx) + (rawDy * rawDy));
        double maxTangential = PhysicsConfig.MU_TIP;
        double scale = tangential > maxTangential && tangential > 1e-9 ? (maxTangential / tangential) : 1.0;
        double dx = rawDx * scale;
        double dy = rawDy * scale;

        Vector3 r = new Vector3(
                left.x() * dx * cue.radius,
                left.y() * dx * cue.radius,
                dy * cue.radius
        );

        applyImpulse(cue, impulse, r);
    }

    public double rollingFriction() {
        return muRolling;
    }

    public double rollingDecelMps2() {
        return muRolling * PhysicsConfig.G_M_PER_S2;
    }

    public void increaseRollingFriction(double delta) {
        setRollingFriction(muRolling + delta);
    }

    public void decreaseRollingFriction(double delta) {
        setRollingFriction(muRolling - delta);
    }

    public void setRollingFriction(double value) {
        muRolling = Math.max(PhysicsConfig.MIN_MU_ROLLING, Math.min(PhysicsConfig.MAX_MU_ROLLING, value));
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

        if (x + r > bounds.right()) {
            x = bounds.right() - r;
            ballBody.position = new Vector2(x, y);
            applyRailContactImpulse(ballBody, new Vector2(-1, 0));
        }
        if (x - r < bounds.left()) {
            x = bounds.left() + r;
            ballBody.position = new Vector2(x, y);
            applyRailContactImpulse(ballBody, new Vector2(1, 0));
        }
        if (y + r > bounds.bottom()) {
            y = bounds.bottom() - r;
            ballBody.position = new Vector2(x, y);
            applyRailContactImpulse(ballBody, new Vector2(0, -1));
        }
        if (y - r < bounds.top()) {
            y = bounds.top() + r;
            ballBody.position = new Vector2(x, y);
            applyRailContactImpulse(ballBody, new Vector2(0, 1));
        }

        ballBody.position = new Vector2(x, y);
    }

    private void applyRailContactImpulse(BallBody body, Vector2 normal) {
        Vector2 n = normal.normalized();
        Vector2 t = new Vector2(-n.y(), n.x());

        // Wall-contact model: contact point from center is -R*n.
        Vector3 r = new Vector3(-n.x() * body.radius, -n.y() * body.radius, 0.0);
        Vector3 vContact3 = toVec3(body.velocity).add(cross(body.angularVelocity, r));
        Vector2 vContact = new Vector2(vContact3.x(), vContact3.y());

        double vn = dot(vContact, n);
        if (vn >= 0.0) {
            return;
        }

        double invMass = 1.0 / body.mass;
        double invI = 1.0 / inertia(body.mass, body.radius);
        Vector3 n3 = toVec3(n);
        Vector3 t3 = toVec3(t);

        double kN = invMass + cross(r, n3).lengthSq() * invI;
        double jn = -(1.0 + PhysicsConfig.RAIL_RESTITUTION) * vn / kN;

        double vt = dot(vContact, t);
        double jt = 0.0;
        if (Math.abs(vt) > PhysicsConfig.BALL_COLLISION_TANGENTIAL_EPS_M_PER_S) {
            double kT = invMass + cross(r, t3).lengthSq() * invI;
            double jtUnclamped = -vt / kT;
            double jtMax = PhysicsConfig.MU_RAIL * jn;
            jt = Math.max(-jtMax, Math.min(jtMax, jtUnclamped));
        }

        Vector3 impulse = n3.mul(jn).add(t3.mul(jt));
        applyImpulse(body, impulse, r);
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

                Vector3 n3 = new Vector3(normal.x(), normal.y(), 0.0);
                Vector3 r1 = n3.mul(a.radius);
                Vector3 r2 = n3.mul(-b.radius);

                Vector3 v1c = toVec3(a.velocity).add(cross(a.angularVelocity, r1));
                Vector3 v2c = toVec3(b.velocity).add(cross(b.angularVelocity, r2));
                Vector3 vRel3 = v2c.sub(v1c);
                Vector2 vRel = new Vector2(vRel3.x(), vRel3.y());

                double vRelN = dot(vRel, normal);
                if (vRelN >= 0.0) {
                    continue;
                }

                double invMassSum = (1.0 / a.mass) + (1.0 / b.mass);
                double jn = -(1.0 + PhysicsConfig.BALL_BALL_RESTITUTION) * vRelN / invMassSum;

                Vector2 tangentComponent = vRel.sub(normal.mul(vRelN));
                Vector2 tangent = tangentComponent.normalized();
                double vt = tangentComponent.length();
                double jt = 0.0;
                if (vt > PhysicsConfig.BALL_COLLISION_TANGENTIAL_EPS_M_PER_S) {
                    double i1 = inertia(a.mass, a.radius);
                    double i2 = inertia(b.mass, b.radius);
                    Vector3 t3 = new Vector3(tangent.x(), tangent.y(), 0.0);
                    double kT = invMassSum
                            + cross(r1, t3).lengthSq() / i1
                            + cross(r2, t3).lengthSq() / i2;
                    double jtUnclamped = -vt / kT;
                    double jtMax = PhysicsConfig.MU_BALL_BALL * jn;
                    jt = Math.max(-jtMax, Math.min(jtMax, jtUnclamped));
                }

                Vector3 impulse = n3.mul(jn).add(new Vector3(tangent.x(), tangent.y(), 0.0).mul(jt));
                applyImpulse(a, impulse.mul(-1.0), r1);
                applyImpulse(b, impulse, r2);
            }
        }
    }

    private void applyImpulse(BallBody body, Vector3 impulse, Vector3 contactOffset) {
        body.velocity = body.velocity.add(new Vector2(impulse.x(), impulse.y()).mul(1.0 / body.mass));
        Vector3 torqueImpulse = cross(contactOffset, impulse);
        double invI = 1.0 / inertia(body.mass, body.radius);
        body.angularVelocity = body.angularVelocity.add(torqueImpulse.mul(invI));
    }

    private static double inertia(double mass, double radius) {
        return (2.0 / 5.0) * mass * radius * radius;
    }

    private static double dot(Vector2 a, Vector2 b) {
        return (a.x() * b.x()) + (a.y() * b.y());
    }

    private static Vector3 toVec3(Vector2 v) {
        return new Vector3(v.x(), v.y(), 0.0);
    }

    private static Vector3 cross(Vector3 a, Vector3 b) {
        return new Vector3(
                (a.y() * b.z()) - (a.z() * b.y()),
                (a.z() * b.x()) - (a.x() * b.z()),
                (a.x() * b.y()) - (a.y() * b.x())
        );
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
            double aRoll = muRolling * PhysicsConfig.G_M_PER_S2;
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
