package com.billiardgame.physics;

import com.billiardgame.game.Ball;

import java.util.ArrayList;
import java.util.List;

public final class PhysicsWorld {
    public enum MotionMode {
        SLIDING,
        ROLLING
    }

    private final List<BallBody> balls;
    private final TableBounds bounds;
    private final double pixelsPerMeter;
    private double muRolling;
    private final PocketModel pocketModel;

    public static final class DebugSegment {
        private final Vector2 a;
        private final Vector2 b;

        public DebugSegment(Vector2 a, Vector2 b) {
            this.a = a;
            this.b = b;
        }

        public Vector2 a() {
            return a;
        }

        public Vector2 b() {
            return b;
        }
    }

    public static final class DebugArc {
        private final Vector2 center;
        private final double radius;
        private final double startDeg;
        private final double sweepDeg;

        public DebugArc(Vector2 center, double radius, double startDeg, double sweepDeg) {
            this.center = center;
            this.radius = radius;
            this.startDeg = startDeg;
            this.sweepDeg = sweepDeg;
        }

        public Vector2 center() {
            return center;
        }

        public double radius() {
            return radius;
        }

        public double startDeg() {
            return startDeg;
        }

        public double sweepDeg() {
            return sweepDeg;
        }
    }

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
        this.pocketModel = PocketModel.fromBounds(this.bounds, PhysicsConfig.BALL_RADIUS_M);
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

    public Quaternion ballOrientation(int index) {
        return balls.get(index).orientation;
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

    public int ballCount() {
        return balls.size();
    }

    public List<DebugSegment> pocketMouthSegmentsPx() {
        List<DebugSegment> out = new ArrayList<>();
        for (Segment2 s : pocketModel.mouthSegments) {
            out.add(new DebugSegment(toPixels(s.a), toPixels(s.b)));
        }
        return List.copyOf(out);
    }

    public List<DebugArc> pocketJawArcsPx() {
        List<DebugArc> out = new ArrayList<>();
        for (ArcJaw a : pocketModel.jawArcs) {
            out.add(new DebugArc(toPixels(a.center), toPixels(a.radius), a.startDeg, a.sweepDeg));
        }
        return List.copyOf(out);
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
            double pocketDamping = pocketModel.captureApproachDamping(ballBody.position, ballBody.radius, dtSeconds);
            if (pocketDamping < 1.0) {
                ballBody.velocity = ballBody.velocity.mul(pocketDamping);
            }
            applyClothInteraction(ballBody, dtSeconds);
            integrateOrientation(ballBody, dtSeconds);
        }

        balls.removeIf(body -> {
            if (!isPotted(body)) {
                return false;
            }
            body.velocity = Vector2.ZERO;
            body.angularVelocity = Vector3.ZERO;
            return true;
        });
    }

    private void resolveRailCollision(BallBody ballBody) {
        double r = ballBody.radius;
        double x = ballBody.position.x();
        double y = ballBody.position.y();

        if (x + r > bounds.right()) {
            if (!pocketModel.inRightOpening(y)) {
                x = bounds.right() - r;
                ballBody.position = new Vector2(x, y);
                applyRailContactImpulse(ballBody, new Vector2(-1, 0));
            }
        }
        if (x - r < bounds.left()) {
            if (!pocketModel.inLeftOpening(y)) {
                x = bounds.left() + r;
                ballBody.position = new Vector2(x, y);
                applyRailContactImpulse(ballBody, new Vector2(1, 0));
            }
        }
        if (y + r > bounds.bottom()) {
            if (!pocketModel.inBottomOpening(x)) {
                y = bounds.bottom() - r;
                ballBody.position = new Vector2(x, y);
                applyRailContactImpulse(ballBody, new Vector2(0, -1));
            }
        }
        if (y - r < bounds.top()) {
            if (!pocketModel.inTopOpening(x)) {
                y = bounds.top() + r;
                ballBody.position = new Vector2(x, y);
                applyRailContactImpulse(ballBody, new Vector2(0, 1));
            }
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
        double jn = -(1.0 + PhysicsConfig.CUSHION_RESTITUTION) * vn / kN;

        double vt = dot(vContact, t);
        double jt = 0.0;
        if (Math.abs(vt) > PhysicsConfig.BALL_COLLISION_TANGENTIAL_EPS_M_PER_S) {
            double kT = invMass + cross(r, t3).lengthSq() * invI;
            double jtUnclamped = -vt / kT;
            double jtMax = PhysicsConfig.MU_CUSHION * jn;
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
                if (distance > PhysicsConfig.COLLISION_EPS) {
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
        return PhysicsConfig.SOLID_SPHERE_INERTIA_SCALE * mass * radius * radius;
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

    private boolean isPotted(BallBody body) {
        return pocketModel.isPotted(body.position, body.radius);
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
            if (nextSlip.length() <= (PhysicsConfig.SLIP_EPS_M_PER_S * PhysicsConfig.SLIP_TO_ROLLING_EPS_SCALE)
                    || nextVelocity.length() <= PhysicsConfig.SLIP_TO_ROLLING_SPEED_M_PER_S) {
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

    private void integrateOrientation(BallBody body, double dtSeconds) {
        Vector3 w = body.angularVelocity;
        double omega = w.length();
        double angle = omega * dtSeconds;
        if (angle < PhysicsConfig.ORIENTATION_INTEGRATION_EPS_RAD) {
            return;
        }
        Vector3 axis = w.normalized();
        Quaternion dq = Quaternion.fromAxisAngle(axis, angle);
        body.orientation = dq.mul(body.orientation).normalize();
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
        private Quaternion orientation;
        private final double radius;
        private final double mass;
        private MotionMode mode;

        private BallBody(Vector2 position, Vector2 velocity, Vector3 angularVelocity, double radius, double mass) {
            this.position = position;
            this.velocity = velocity;
            this.angularVelocity = angularVelocity;
            this.orientation = Quaternion.IDENTITY;
            this.radius = radius;
            this.mass = mass;
            this.mode = MotionMode.SLIDING;
        }
    }

    private static final class Segment2 {
        private final Vector2 a;
        private final Vector2 b;

        private Segment2(Vector2 a, Vector2 b) {
            this.a = a;
            this.b = b;
        }
    }

    private static final class ArcJaw {
        private final Vector2 center;
        private final double radius;
        private final double startDeg;
        private final double sweepDeg;

        private ArcJaw(Vector2 center, double radius, double startDeg, double sweepDeg) {
            this.center = center;
            this.radius = radius;
            this.startDeg = startDeg;
            this.sweepDeg = sweepDeg;
        }
    }

    private static final class PocketModel {
        private final double left;
        private final double right;
        private final double top;
        private final double bottom;
        private final double cx;
        private final double mouthHalf;
        private final double cornerMouth;
        private final double dropDepth;
        private final List<Segment2> mouthSegments;
        private final List<ArcJaw> jawArcs;

        private PocketModel(double left, double right, double top, double bottom, double cx, double mouthHalf, double cornerMouth, double dropDepth, List<Segment2> mouthSegments, List<ArcJaw> jawArcs) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
            this.cx = cx;
            this.mouthHalf = mouthHalf;
            this.cornerMouth = cornerMouth;
            this.dropDepth = dropDepth;
            this.mouthSegments = mouthSegments;
            this.jawArcs = jawArcs;
        }

        private static PocketModel fromBounds(TableBounds b, double ballRadius) {
            double left = b.left();
            double right = b.right();
            double top = b.top();
            double bottom = b.bottom();
            double cx = (left + right) * 0.5;
            double mouthHalf = ballRadius * PhysicsConfig.POCKET_SIDE_MOUTH_HALF_MULTIPLIER;
            double cornerMouth = ballRadius * PhysicsConfig.POCKET_CORNER_MOUTH_MULTIPLIER;
            double dropDepth = ballRadius * PhysicsConfig.POCKET_DROP_DEPTH_MULTIPLIER;
            double jawR = ballRadius * PhysicsConfig.POCKET_JAW_RADIUS_MULTIPLIER;

            List<Segment2> segs = new ArrayList<>();
            segs.add(new Segment2(new Vector2(left + cornerMouth, top), new Vector2(cx - mouthHalf, top)));
            segs.add(new Segment2(new Vector2(cx + mouthHalf, top), new Vector2(right - cornerMouth, top)));
            segs.add(new Segment2(new Vector2(left + cornerMouth, bottom), new Vector2(cx - mouthHalf, bottom)));
            segs.add(new Segment2(new Vector2(cx + mouthHalf, bottom), new Vector2(right - cornerMouth, bottom)));
            segs.add(new Segment2(new Vector2(left, top + cornerMouth), new Vector2(left, bottom - cornerMouth)));
            segs.add(new Segment2(new Vector2(right, top + cornerMouth), new Vector2(right, bottom - cornerMouth)));

            List<ArcJaw> jaws = new ArrayList<>();
            jaws.add(new ArcJaw(new Vector2(left + cornerMouth, top + jawR), jawR, 180, 90));
            jaws.add(new ArcJaw(new Vector2(left + jawR, top + cornerMouth), jawR, 270, 90));
            jaws.add(new ArcJaw(new Vector2(right - cornerMouth, top + jawR), jawR, 270, 90));
            jaws.add(new ArcJaw(new Vector2(right - jawR, top + cornerMouth), jawR, 180, 90));
            jaws.add(new ArcJaw(new Vector2(left + cornerMouth, bottom - jawR), jawR, 90, 90));
            jaws.add(new ArcJaw(new Vector2(left + jawR, bottom - cornerMouth), jawR, 0, 90));
            jaws.add(new ArcJaw(new Vector2(right - cornerMouth, bottom - jawR), jawR, 0, 90));
            jaws.add(new ArcJaw(new Vector2(right - jawR, bottom - cornerMouth), jawR, 90, 90));
            jaws.add(new ArcJaw(new Vector2(cx - mouthHalf, top + jawR), jawR, 180, 90));
            jaws.add(new ArcJaw(new Vector2(cx + mouthHalf, top + jawR), jawR, 270, 90));
            jaws.add(new ArcJaw(new Vector2(cx - mouthHalf, bottom - jawR), jawR, 90, 90));
            jaws.add(new ArcJaw(new Vector2(cx + mouthHalf, bottom - jawR), jawR, 0, 90));

            return new PocketModel(left, right, top, bottom, cx, mouthHalf, cornerMouth, dropDepth, List.copyOf(segs), List.copyOf(jaws));
        }

        private boolean inTopOpening(double x) {
            return x <= left + cornerMouth || x >= right - cornerMouth || Math.abs(x - cx) <= mouthHalf;
        }

        private boolean inBottomOpening(double x) {
            return inTopOpening(x);
        }

        private boolean inLeftOpening(double y) {
            return y <= top + cornerMouth || y >= bottom - cornerMouth;
        }

        private boolean inRightOpening(double y) {
            return inLeftOpening(y);
        }

        private boolean isPotted(Vector2 p, double ballRadius) {
            return isPottedAt(p, ballRadius, new Vector2(left, top), cornerMouth)
                    || isPottedAt(p, ballRadius, new Vector2(cx, top), mouthHalf)
                    || isPottedAt(p, ballRadius, new Vector2(right, top), cornerMouth)
                    || isPottedAt(p, ballRadius, new Vector2(left, bottom), cornerMouth)
                    || isPottedAt(p, ballRadius, new Vector2(cx, bottom), mouthHalf)
                    || isPottedAt(p, ballRadius, new Vector2(right, bottom), cornerMouth);
        }

        private double captureApproachDamping(Vector2 p, double ballRadius, double dtSeconds) {
            double damping = 1.0;
            damping = Math.min(damping, approachDampingAt(p, ballRadius, dtSeconds, new Vector2(left, top), cornerMouth));
            damping = Math.min(damping, approachDampingAt(p, ballRadius, dtSeconds, new Vector2(cx, top), mouthHalf));
            damping = Math.min(damping, approachDampingAt(p, ballRadius, dtSeconds, new Vector2(right, top), cornerMouth));
            damping = Math.min(damping, approachDampingAt(p, ballRadius, dtSeconds, new Vector2(left, bottom), cornerMouth));
            damping = Math.min(damping, approachDampingAt(p, ballRadius, dtSeconds, new Vector2(cx, bottom), mouthHalf));
            damping = Math.min(damping, approachDampingAt(p, ballRadius, dtSeconds, new Vector2(right, bottom), cornerMouth));
            return damping;
        }

        private boolean isPottedAt(Vector2 p, double ballRadius, Vector2 center, double pocketRadius) {
            double captureRadius = pocketRadius - ballRadius - (ballRadius * PhysicsConfig.POCKET_CAPTURE_LIP_MARGIN);
            if (captureRadius <= 0.0) {
                return false;
            }
            return p.sub(center).length() <= captureRadius;
        }

        private double approachDampingAt(Vector2 p, double ballRadius, double dtSeconds, Vector2 center, double pocketRadius) {
            double entryRadius = pocketRadius - ballRadius;
            if (entryRadius <= 0.0) {
                return 1.0;
            }
            double dist = p.sub(center).length();
            if (dist >= entryRadius) {
                return 1.0;
            }
            double depth = 1.0 - (dist / entryRadius);
            double dampingRate = PhysicsConfig.POCKET_APPROACH_DAMPING_RATE * depth;
            return Math.max(0.0, 1.0 - (dampingRate * dtSeconds));
        }
    }
}
