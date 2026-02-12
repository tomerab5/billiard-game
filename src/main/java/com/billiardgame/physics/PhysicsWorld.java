package com.billiardgame.physics;

import com.billiardgame.game.Ball;

import java.util.ArrayList;
import java.util.List;

public final class PhysicsWorld {
    public enum BallPocketState {
        ON_TABLE,
        ENTERING_POCKET,
        IN_POCKET,
        REMOVED
    }

    public enum MotionMode {
        SLIDING,
        ROLLING
    }

    private final List<BallBody> balls;
    private final TableBounds bounds;
    private final double pixelsPerMeter;
    private double muRolling;
    private final PocketModel pocketModel;
    private final double bedMinX;
    private final double bedMaxX;
    private final double bedMinY;
    private final double bedMaxY;
    private final int railSegmentCount;
    private final int railTopSegmentCount;
    private final int railBottomSegmentCount;
    private final int railLeftSegmentCount;
    private final int railRightSegmentCount;
    private int pocketEscapeGuardHits;
    private int pocketEscapeGuardLastPocketId;
    private String pocketEscapeGuardLastPocketType;
    private int pocketEscapesCaught;
    private int pocketEscapesClamped;
    private final boolean scratchEnabled;
    private boolean scratchPending;
    private boolean cueBallRespawnedThisStep;
    private int nextBallId;

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

    public static final class DebugFacing {
        private final Vector2 a;
        private final Vector2 b;
        private final Vector2 normal;

        public DebugFacing(Vector2 a, Vector2 b, Vector2 normal) {
            this.a = a;
            this.b = b;
            this.normal = normal;
        }

        public Vector2 a() {
            return a;
        }

        public Vector2 b() {
            return b;
        }

        public Vector2 normal() {
            return normal;
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
        for (int i = 0; i < initialBalls.size(); i++) {
            Ball initialBall = initialBalls.get(i);
            balls.add(new BallBody(
                    nextBallId++,
                    i == 0,
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
        this.bedMinX = this.bounds.left();
        this.bedMaxX = this.bounds.right();
        this.bedMinY = this.bounds.top();
        this.bedMaxY = this.bounds.bottom();
        this.railSegmentCount = pocketModel.railSegmentCount();
        this.railTopSegmentCount = pocketModel.railSegmentCountBySide(RailSide.TOP);
        this.railBottomSegmentCount = pocketModel.railSegmentCountBySide(RailSide.BOTTOM);
        this.railLeftSegmentCount = pocketModel.railSegmentCountBySide(RailSide.LEFT);
        this.railRightSegmentCount = pocketModel.railSegmentCountBySide(RailSide.RIGHT);
        this.pocketEscapeGuardHits = 0;
        this.pocketEscapeGuardLastPocketId = -1;
        this.pocketEscapeGuardLastPocketType = "none";
        this.pocketEscapesCaught = 0;
        this.pocketEscapesClamped = 0;
        this.scratchEnabled = initialBalls.size() > 1;
        this.scratchPending = false;
        this.cueBallRespawnedThisStep = false;
    }

    public List<Ball> balls() {
        List<Ball> snapshot = new ArrayList<>(balls.size());
        for (BallBody ballBody : balls) {
            snapshot.add(toRenderBall(ballBody));
        }
        return List.copyOf(snapshot);
    }

    public Ball cueBall() {
        return toRenderBall(requireCueBody());
    }

    public Ball ball(int index) {
        return toRenderBall(balls.get(index));
    }

    public void setCueBallVelocity(Vector2 velocity) {
        if (!isCueBallOnTable()) {
            return;
        }
        requireCueBody().velocity = toMeters(velocity);
    }

    public void setBallVelocity(int index, Vector2 velocity) {
        balls.get(index).velocity = toMeters(velocity);
    }

    public Vector2 ballVelocity(int index) {
        return toPixels(balls.get(index).velocity);
    }

    public double ballSlipSpeed(int index) {
        return toPixels(slipVelocityAtContact(balls.get(index)).length());
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
        if (!isCueBallOnTable()) {
            return MotionMode.ROLLING;
        }
        return requireCueBody().mode;
    }

    public double cueBallSpeed() {
        if (!isCueBallOnTable()) {
            return 0.0;
        }
        return toPixels(requireCueBody().velocity).length();
    }

    public void strikeCueBall(Vector2 directionPx, double speedPxPerSec, Vector2 tipOffsetNorm) {
        if (!isCueBallOnTable()) {
            return;
        }
        if (directionPx.length() <= 1e-9 || speedPxPerSec <= 0.0) {
            return;
        }

        BallBody cue = requireCueBody();
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
            if (ballBody.pocketState != BallPocketState.ON_TABLE) {
                continue;
            }
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

    public boolean isCueBallOnTable() {
        BallBody cue = findCueBody();
        return cue != null && cue.pocketState == BallPocketState.ON_TABLE;
    }

    public boolean consumeCueBallRespawnedThisStep() {
        boolean out = cueBallRespawnedThisStep;
        cueBallRespawnedThisStep = false;
        return out;
    }

    public int cueBallIndex() {
        for (int i = 0; i < balls.size(); i++) {
            if (balls.get(i).isCue) {
                return i;
            }
        }
        return -1;
    }

    public boolean ballIsCue(int index) {
        return balls.get(index).isCue;
    }

    public int ballId(int index) {
        return balls.get(index).id;
    }

    public List<String> ballPocketDebugLines() {
        List<String> out = new ArrayList<>();
        out.add(String.format(
                "pocketEscapes caught=%d clamped=%d",
                pocketEscapesCaught,
                pocketEscapesClamped
        ));
        for (BallBody body : balls) {
            out.add(String.format(
                    "id=%d type=%s state=%s tPocket=%.2f",
                    body.id,
                    body.isCue ? "CUE" : "OBJ",
                    body.pocketState,
                    body.timeInPocketState
            ));
        }
        return List.copyOf(out);
    }

    public List<DebugSegment> pocketMouthSegmentsPx() {
        List<DebugSegment> out = new ArrayList<>();
        for (RailSegment s : pocketModel.railColliders) {
            out.add(new DebugSegment(toPixels(s.segment.a), toPixels(s.segment.b)));
        }
        return List.copyOf(out);
    }

    public int railSegmentCount() {
        return railSegmentCount;
    }

    public int railTopSegmentCount() {
        return railTopSegmentCount;
    }

    public int railBottomSegmentCount() {
        return railBottomSegmentCount;
    }

    public int railLeftSegmentCount() {
        return railLeftSegmentCount;
    }

    public int railRightSegmentCount() {
        return railRightSegmentCount;
    }

    public int pocketEscapeGuardHits() {
        return pocketEscapeGuardHits;
    }

    public int pocketEscapeGuardLastPocketId() {
        return pocketEscapeGuardLastPocketId;
    }

    public String pocketEscapeGuardLastPocketType() {
        return pocketEscapeGuardLastPocketType;
    }

    public int pocketEscapesCaught() {
        return pocketEscapesCaught;
    }

    public int pocketEscapesClamped() {
        return pocketEscapesClamped;
    }

    public List<DebugArc> pocketJawArcsPx() {
        List<DebugArc> out = new ArrayList<>();
        for (ArcJaw a : pocketModel.jawArcs) {
            out.add(new DebugArc(toPixels(a.center), toPixels(a.radius), a.startDeg, a.sweepDeg));
        }
        return List.copyOf(out);
    }

    public List<DebugSegment> pocketFacingSegmentsPx() {
        List<DebugSegment> out = new ArrayList<>();
        for (FacingSegment s : pocketModel.facingSegments) {
            out.add(new DebugSegment(toPixels(s.segment.a), toPixels(s.segment.b)));
        }
        return List.copyOf(out);
    }

    public List<DebugFacing> pocketFacingsPx() {
        List<DebugFacing> out = new ArrayList<>();
        for (FacingSegment s : pocketModel.facingSegments) {
            out.add(new DebugFacing(
                    toPixels(s.segment.a),
                    toPixels(s.segment.b),
                    s.bedSideNormal
            ));
        }
        return List.copyOf(out);
    }

    public List<DebugSegment> pocketShelfSegmentsPx() {
        List<DebugSegment> out = new ArrayList<>();
        for (ShelfLine s : pocketModel.shelfLines) {
            out.add(new DebugSegment(toPixels(s.segment.a), toPixels(s.segment.b)));
        }
        return List.copyOf(out);
    }

    public BallPocketState ballPocketState(int index) {
        return balls.get(index).pocketState;
    }

    public double ballPocketZ(int index) {
        return balls.get(index).pocketZ;
    }

    public double ballRenderScale(int index) {
        BallBody body = balls.get(index);
        if (body.pocketState == BallPocketState.REMOVED) {
            return 0.0;
        }
        if (body.pocketState == BallPocketState.ON_TABLE) {
            return 1.0;
        }
        return Math.max(0.35, 1.0 - (body.pocketZ / (body.radius * 2.8)));
    }

    public double ballRenderAlpha(int index) {
        BallBody body = balls.get(index);
        if (body.pocketState == BallPocketState.REMOVED) {
            return 0.0;
        }
        if (body.pocketState == BallPocketState.ON_TABLE) {
            return 1.0;
        }
        return Math.max(0.10, 1.0 - (body.pocketZ / (body.radius * 2.1)));
    }

    public void step(double dtSeconds) {
        if (dtSeconds <= 0) {
            throw new IllegalArgumentException("dtSeconds must be positive");
        }
        cueBallRespawnedThisStep = false;

        for (BallBody ballBody : balls) {
            if (ballBody.pocketState != BallPocketState.ON_TABLE) {
                continue;
            }
            ballBody.position = ballBody.position.add(ballBody.velocity.mul(dtSeconds));
            resolveRailCollision(ballBody);
        }

        resolveBallCollisions();
        for (BallBody ballBody : balls) {
            if (ballBody.pocketState != BallPocketState.ON_TABLE) {
                continue;
            }
            resolveRailCollision(ballBody);
            resolvePocketSurfaceCollision(ballBody);
        }

        for (BallBody ballBody : balls) {
            if (ballBody.pocketState == BallPocketState.REMOVED) {
                continue;
            }
            if (ballBody.pocketState != BallPocketState.ON_TABLE) {
                updatePocketState(ballBody, dtSeconds);
                continue;
            }
            ballBody.timeInPocketState = 0.0;
            ballBody.pocketStillTimer = 0.0;
            containOnTableBall(ballBody);
            if (ballBody.pocketState != BallPocketState.ON_TABLE) {
                continue;
            }
            double pocketDamping = pocketModel.captureApproachDamping(ballBody.position, ballBody.radius, dtSeconds);
            if (pocketDamping < 1.0) {
                ballBody.velocity = ballBody.velocity.mul(pocketDamping);
            }
            applyClothInteraction(ballBody, dtSeconds);
            integrateOrientation(ballBody, dtSeconds);
            if (PhysicsConfig.POCKET_ESCAPE_GUARD_ENABLED) {
                applyPocketEscapeGuard(ballBody);
            }
            if (ballBody.pocketState != BallPocketState.ON_TABLE) {
                continue;
            }
            PocketCapture capture = pocketModel.captureCandidate(ballBody.position, ballBody.radius);
            if (capture != null) {
                ballBody.pocketState = BallPocketState.ENTERING_POCKET;
                ballBody.pocketCenter = capture.center;
                ballBody.velocity = ballBody.velocity.mul(0.35);
                ballBody.angularVelocity = ballBody.angularVelocity.mul(0.60);
                ballBody.pocketTimer = 0.0;
                ballBody.pocketEscapeGuarded = false;
                if (ballBody.isCue && scratchEnabled) {
                    scratchPending = true;
                }
            }
        }

        for (int i = balls.size() - 1; i >= 0; i--) {
            if (balls.get(i).pocketState != BallPocketState.REMOVED) {
                continue;
            }
            if (balls.get(i).isCue && scratchPending && scratchEnabled) {
                continue;
            }
            balls.remove(i);
        }
        if (scratchPending && scratchEnabled && !balls.isEmpty() && allBallsNearlyStopped()) {
            respawnCueBall();
        }
    }

    private void resolveRailCollision(BallBody ballBody) {
        if (pocketModel.isInPocketMouthOpenRegion(ballBody.position, ballBody.radius)) {
            return;
        }
        RailSegment bestRail = null;
        double bestPenetration = 0.0;
        for (RailSegment rail : pocketModel.railColliders) {
            CollisionContact c = segmentCollision(ballBody.position, ballBody.radius, rail.segment);
            if (c == null) {
                continue;
            }
            if (c.penetration > bestPenetration) {
                bestPenetration = c.penetration;
                bestRail = rail;
            }
        }
        if (bestRail != null) {
            Vector2 n = bestRail.inTableNormal;
            ballBody.position = ballBody.position.add(n.mul(bestPenetration + 1e-6));
            applyRailContactImpulse(ballBody, n);
        }
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

    private void resolvePocketSurfaceCollision(BallBody body) {
        if (!PhysicsConfig.POCKET_MOUTH_COLLIDERS_ENABLED) {
            return;
        }
        if (pocketModel.isInPocketMouthOpenRegion(body.position, body.radius)) {
            return;
        }
        for (FacingSegment fs : pocketModel.facingSegments) {
            Vector2 closest = closestPointOnSegment(body.position, fs.segment);
            CollisionContact c = segmentCollision(body.position, body.radius, fs.segment);
            if (c == null) {
                continue;
            }
            Vector2 inward = fs.bedSideNormal;
            double signedDistanceToFacing = dot(body.position.sub(closest), inward);
            if (signedDistanceToFacing <= 0.0) {
                continue;
            }
            if (dot(c.normal, inward) <= 0.0) {
                continue;
            }
            Vector3 r = new Vector3(-inward.x() * body.radius, -inward.y() * body.radius, 0.0);
            Vector3 vContact3 = toVec3(body.velocity).add(cross(body.angularVelocity, r));
            Vector2 vContact = new Vector2(vContact3.x(), vContact3.y());
            if (dot(vContact, inward) >= 0.0) {
                continue;
            }
            body.position = body.position.add(inward.mul(c.penetration + 1e-6));
            applySurfaceImpulse(body, inward, PhysicsConfig.POCKET_JAW_RESTITUTION, PhysicsConfig.POCKET_JAW_FRICTION);
        }
    }

    private void containOnTableBall(BallBody body) {
        double eps = body.radius * 0.25;
        Vector2 p = body.position;
        boolean outside = p.x() < bedMinX - eps
                || p.x() > bedMaxX + eps
                || p.y() < bedMinY - eps
                || p.y() > bedMaxY + eps;
        if (!outside) {
            return;
        }
        PocketHit pocketHit = classifyPocketExit(p, body.radius);
        if (pocketHit != null) {
            body.pocketState = BallPocketState.ENTERING_POCKET;
            body.pocketCenter = pocketHit.center;
            body.velocity = body.velocity.mul(0.5);
            body.angularVelocity = body.angularVelocity.mul(0.5);
            body.pocketTimer = 0.0;
            body.pocketEscapeGuarded = true;
            if (body.isCue && scratchEnabled) {
                scratchPending = true;
            }
            pocketEscapesCaught++;
            pocketEscapeGuardHits++;
            pocketEscapeGuardLastPocketId = pocketHit.pocketId;
            pocketEscapeGuardLastPocketType = pocketHit.sidePocket ? "SIDE" : "CORNER";
            return;
        }

        double insidePad = body.radius * 0.05;
        double clampedX = Math.max(bedMinX + insidePad, Math.min(bedMaxX - insidePad, p.x()));
        double clampedY = Math.max(bedMinY + insidePad, Math.min(bedMaxY - insidePad, p.y()));
        double vx = body.velocity.x();
        double vy = body.velocity.y();
        if (p.x() < bedMinX - eps || p.x() > bedMaxX + eps) {
            vx = -vx * 0.65;
        }
        if (p.y() < bedMinY - eps || p.y() > bedMaxY + eps) {
            vy = -vy * 0.65;
        }
        body.position = new Vector2(clampedX, clampedY);
        body.velocity = new Vector2(vx, vy);
        pocketEscapesClamped++;
    }

    private PocketHit classifyPocketExit(Vector2 p, double ballR) {
        PocketRegion nearest = pocketModel.nearestPocketRegion(p);
        if (nearest == null) {
            return null;
        }
        double triggerRadius = nearest.radius + (ballR * 1.5);
        if (p.sub(nearest.center).length() > triggerRadius) {
            return null;
        }
        return new PocketHit(
                nearest.center,
                nearest.radius,
                nearest.sidePocket,
                pocketModel.pocketIndex(nearest)
        );
    }

    private static Vector2 closestPointOnSegment(Vector2 center, Segment2 seg) {
        Vector2 ab = seg.b.sub(seg.a);
        double abLenSq = ab.lengthSq();
        if (abLenSq <= 1e-12) {
            return seg.a;
        }
        double t = dot(center.sub(seg.a), ab) / abLenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        return seg.a.add(ab.mul(t));
    }

    private static CollisionContact segmentCollision(Vector2 center, double radius, Segment2 seg) {
        Vector2 ab = seg.b.sub(seg.a);
        double abLenSq = ab.lengthSq();
        if (abLenSq <= 1e-12) {
            return null;
        }
        double t = dot(center.sub(seg.a), ab) / abLenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        Vector2 closest = seg.a.add(ab.mul(t));
        Vector2 delta = center.sub(closest);
        double dist = delta.length();
        if (dist >= radius || dist <= 1e-12) {
            return null;
        }
        Vector2 normal = delta.mul(1.0 / dist);
        return new CollisionContact(normal, radius - dist);
    }

    private void applyPocketEscapeGuard(BallBody body) {
        if (body.pocketState != BallPocketState.ON_TABLE) {
            return;
        }
        double eps = body.radius * 0.10;
        Vector2 p = body.position;
        if (p.x() >= bedMinX - eps && p.x() <= bedMaxX + eps && p.y() >= bedMinY - eps && p.y() <= bedMaxY + eps) {
            return;
        }
        PocketRegion nearest = pocketModel.nearestPocketRegion(p);
        if (nearest == null) {
            return;
        }
        double dist = p.sub(nearest.center).length();
        double triggerRadius = nearest.radius + (body.radius * 1.2);
        if (dist > triggerRadius) {
            return;
        }
        body.pocketState = BallPocketState.ENTERING_POCKET;
        body.pocketCenter = nearest.center;
        body.pocketTimer = 0.0;
        body.pocketZ = Math.max(body.pocketZ, body.radius * 0.15);
        body.velocity = body.velocity.mul(0.30);
        body.angularVelocity = body.angularVelocity.mul(0.40);
        body.pocketEscapeGuarded = true;
        if (body.isCue && scratchEnabled) {
            scratchPending = true;
        }
        pocketEscapeGuardHits++;
        pocketEscapeGuardLastPocketId = pocketModel.pocketIndex(nearest);
        pocketEscapeGuardLastPocketType = nearest.sidePocket ? "SIDE" : "CORNER";
    }

    private void respawnCueBall() {
        BallBody cue = findCueBody();
        if (cue == null) {
            scratchPending = false;
            return;
        }
        Vector2 spawn = findCueBallRespawnPosition(cue.radius);
        cue.position = spawn;
        cue.velocity = Vector2.ZERO;
        cue.angularVelocity = Vector3.ZERO;
        cue.orientation = Quaternion.IDENTITY;
        cue.mode = MotionMode.SLIDING;
        cue.pocketState = BallPocketState.ON_TABLE;
        cue.pocketCenter = null;
        cue.pocketZ = 0.0;
        cue.pocketTimer = 0.0;
        cue.pocketEscapeGuarded = false;
        scratchPending = false;
        cueBallRespawnedThisStep = true;
    }

    private Vector2 findCueBallRespawnPosition(double radius) {
        double left = bounds.left() + radius;
        double right = bounds.right() - radius;
        double top = bounds.top() + radius;
        double bottom = bounds.bottom() - radius;
        double centerY = (top + bottom) * 0.5;
        Vector2[] candidates = new Vector2[]{
                new Vector2(bounds.left() + ((bounds.right() - bounds.left()) * 0.25), centerY),
                new Vector2((bounds.left() + bounds.right()) * 0.5, centerY),
                new Vector2(bounds.left() + ((bounds.right() - bounds.left()) * 0.20), centerY)
        };
        for (Vector2 candidate : candidates) {
            Vector2 clamped = new Vector2(
                    Math.max(left, Math.min(right, candidate.x())),
                    Math.max(top, Math.min(bottom, candidate.y()))
            );
            if (isCueSpawnClear(clamped, radius)) {
                return clamped;
            }
        }
        int scanX = 18;
        int scanY = 10;
        for (int iy = 0; iy < scanY; iy++) {
            double y = top + ((bottom - top) * ((iy + 0.5) / scanY));
            for (int ix = 0; ix < scanX; ix++) {
                double x = left + ((right - left) * ((ix + 0.5) / scanX));
                Vector2 probe = new Vector2(x, y);
                if (isCueSpawnClear(probe, radius)) {
                    return probe;
                }
            }
        }
        return new Vector2(Math.max(left, Math.min(right, (bounds.left() + bounds.right()) * 0.5)), centerY);
    }

    private boolean isCueSpawnClear(Vector2 position, double radius) {
        double minGap = radius * 2.05;
        double minGapSq = minGap * minGap;
        for (BallBody other : balls) {
            if (other.isCue) {
                continue;
            }
            if (other.pocketState != BallPocketState.ON_TABLE) {
                continue;
            }
            if (other.position.sub(position).lengthSq() < minGapSq) {
                return false;
            }
        }
        return true;
    }

    private void applySurfaceImpulse(BallBody body, Vector2 normal, double restitution, double friction) {
        Vector2 n = normal.normalized();
        Vector2 t = new Vector2(-n.y(), n.x());
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
        double jn = -(1.0 + restitution) * vn / kN;
        double vt = dot(vContact, t);
        double jt = 0.0;
        if (Math.abs(vt) > PhysicsConfig.BALL_COLLISION_TANGENTIAL_EPS_M_PER_S) {
            double kT = invMass + cross(r, t3).lengthSq() * invI;
            double jtUnclamped = -vt / kT;
            double jtMax = friction * jn;
            jt = Math.max(-jtMax, Math.min(jtMax, jtUnclamped));
        }
        Vector3 impulse = n3.mul(jn).add(t3.mul(jt));
        applyImpulse(body, impulse, r);
    }

    private void updatePocketState(BallBody body, double dtSeconds) {
        body.pocketTimer += dtSeconds;
        body.timeInPocketState += dtSeconds;
        if (body.pocketCenter != null) {
            Vector2 toCenter = body.pocketCenter.sub(body.position);
            double pullRate = body.pocketEscapeGuarded ? 6.2 : 8.0;
            body.position = body.position.add(toCenter.mul(Math.min(1.0, dtSeconds * pullRate)));
            double dist = toCenter.length();
            if (dist > 1e-9) {
                Vector2 inward = toCenter.mul(1.0 / dist);
                double inwardAccel = body.pocketEscapeGuarded ? 1.8 : 0.7;
                if (inwardAccel > 0.0) {
                    body.velocity = body.velocity.add(inward.mul(inwardAccel * dtSeconds));
                }
            }
        }
        double linerDamping = body.pocketEscapeGuarded
                ? (10.0 + (PhysicsConfig.POCKET_LINER_FRICTION * 10.0))
                : (8.0 + (PhysicsConfig.POCKET_LINER_FRICTION * 8.0));
        double spinDamping = body.pocketEscapeGuarded
                ? (10.5 + (PhysicsConfig.POCKET_LINER_FRICTION * 11.0))
                : (8.2 + (PhysicsConfig.POCKET_LINER_FRICTION * 8.4));
        body.velocity = body.velocity.mul(Math.max(0.0, 1.0 - linerDamping * dtSeconds));
        body.angularVelocity = body.angularVelocity.mul(Math.max(0.0, 1.0 - spinDamping * dtSeconds));
        body.velocity = body.velocity.mul(PhysicsConfig.POCKET_LINER_RESTITUTION);
        if (body.velocity.length() < PhysicsConfig.STOP_EPS_M_PER_S
                && Math.abs(body.angularVelocity.z()) < PhysicsConfig.STOP_SPIN_EPS_RAD_PER_S) {
            body.pocketStillTimer += dtSeconds;
        } else {
            body.pocketStillTimer = 0.0;
        }
        body.pocketZ += (body.radius * 3.0) * dtSeconds;
        if (body.pocketState == BallPocketState.ENTERING_POCKET && body.pocketTimer >= 0.10) {
            body.pocketState = BallPocketState.IN_POCKET;
        }
        if (body.pocketStillTimer > 0.25 || body.timeInPocketState > 2.0) {
            if (body.isCue && scratchEnabled) {
                scratchPending = true;
            }
            body.pocketState = BallPocketState.REMOVED;
            return;
        }
        if (body.pocketTimer >= 0.45) {
            body.pocketState = BallPocketState.REMOVED;
        }
    }

    private void applyClothInteraction(BallBody ballBody, double dtSeconds) {
        Vector2 v = ballBody.velocity;
        Vector3 w = ballBody.angularVelocity;

        Vector2 slipVelocity = slipVelocityAtContact(ballBody);
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
            if (nextSlip.length() <= (PhysicsConfig.SLIP_EPS_M_PER_S * PhysicsConfig.SLIP_TO_ROLLING_EPS_SCALE)) {
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
        if (decayed < PhysicsConfig.SPIN_DECAY_SMOOTH_EPS_RAD_PER_S) {
            decayed = (decayed * decayed) / PhysicsConfig.SPIN_DECAY_SMOOTH_EPS_RAD_PER_S;
        }
        return Math.copySign(decayed, wz);
    }

    private static Vector2 slipVelocityAtContact(BallBody ballBody) {
        Vector2 v = ballBody.velocity;
        Vector3 w = ballBody.angularVelocity;
        return v.add(new Vector2(-ballBody.radius * w.y(), ballBody.radius * w.x()));
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

    private BallBody findCueBody() {
        for (BallBody body : balls) {
            if (body.isCue) {
                return body;
            }
        }
        return null;
    }

    private BallBody requireCueBody() {
        BallBody cue = findCueBody();
        if (cue == null) {
            throw new IllegalStateException("Cue ball not found");
        }
        return cue;
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
        private final int id;
        private final boolean isCue;
        private Vector2 position;
        private Vector2 velocity;
        private Vector3 angularVelocity;
        private Quaternion orientation;
        private final double radius;
        private final double mass;
        private MotionMode mode;
        private BallPocketState pocketState;
        private Vector2 pocketCenter;
        private double pocketZ;
        private double pocketTimer;
        private double timeInPocketState;
        private double pocketStillTimer;
        private boolean pocketEscapeGuarded;

        private BallBody(int id, boolean isCue, Vector2 position, Vector2 velocity, Vector3 angularVelocity, double radius, double mass) {
            this.id = id;
            this.isCue = isCue;
            this.position = position;
            this.velocity = velocity;
            this.angularVelocity = angularVelocity;
            this.orientation = Quaternion.IDENTITY;
            this.radius = radius;
            this.mass = mass;
            this.mode = MotionMode.SLIDING;
            this.pocketState = BallPocketState.ON_TABLE;
            this.pocketCenter = null;
            this.pocketZ = 0.0;
            this.pocketTimer = 0.0;
            this.timeInPocketState = 0.0;
            this.pocketStillTimer = 0.0;
            this.pocketEscapeGuarded = false;
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

        private boolean containsAngle(double angleDeg) {
            double a = normalizeDeg(angleDeg);
            double start = normalizeDeg(startDeg);
            double end = normalizeDeg(startDeg + sweepDeg);
            if (sweepDeg >= 0) {
                if (start <= end) {
                    return a >= start && a <= end;
                }
                return a >= start || a <= end;
            }
            if (end <= start) {
                return a <= start && a >= end;
            }
            return a <= start || a >= end;
        }
    }

    private static final class ShelfLine {
        private final Segment2 segment;
        private final Vector2 inTableNormal;

        private ShelfLine(Segment2 segment, Vector2 inTableNormal) {
            this.segment = segment;
            this.inTableNormal = inTableNormal.normalized();
        }
    }

    private enum RailSide {
        TOP,
        BOTTOM,
        LEFT,
        RIGHT
    }

    private static final class RailSegment {
        private final Segment2 segment;
        private final RailSide side;
        private final Vector2 inTableNormal;

        private RailSegment(Segment2 segment, RailSide side) {
            this.segment = segment;
            this.side = side;
            this.inTableNormal = switch (side) {
                case TOP -> new Vector2(0, 1);
                case BOTTOM -> new Vector2(0, -1);
                case LEFT -> new Vector2(1, 0);
                case RIGHT -> new Vector2(-1, 0);
            };
        }
    }

    private static final class PocketCapture {
        private final Vector2 center;

        private PocketCapture(Vector2 center) {
            this.center = center;
        }
    }

    private static final class PocketRegion {
        private final Vector2 center;
        private final double radius;
        private final boolean sidePocket;

        private PocketRegion(Vector2 center, double radius, boolean sidePocket) {
            this.center = center;
            this.radius = radius;
            this.sidePocket = sidePocket;
        }
    }

    private static final class FacingSegment {
        private final Segment2 segment;
        private final Vector2 bedSideNormal;

        private FacingSegment(Segment2 segment, Vector2 bedSideNormal) {
            this.segment = segment;
            this.bedSideNormal = bedSideNormal.normalized();
        }
    }

    private static final class CollisionContact {
        private final Vector2 normal;
        private final double penetration;

        private CollisionContact(Vector2 normal, double penetration) {
            this.normal = normal;
            this.penetration = penetration;
        }
    }

    private static final class PocketHit {
        private final Vector2 center;
        private final double mouthRadius;
        private final boolean sidePocket;
        private final int pocketId;

        private PocketHit(Vector2 center, double mouthRadius, boolean sidePocket, int pocketId) {
            this.center = center;
            this.mouthRadius = mouthRadius;
            this.sidePocket = sidePocket;
            this.pocketId = pocketId;
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
        private final List<RailSegment> railColliders;
        private final List<FacingSegment> facingSegments;
        private final List<ShelfLine> shelfLines;
        private final List<ArcJaw> jawArcs;
        private final List<PocketRegion> pocketRegions;

        private PocketModel(double left, double right, double top, double bottom, double cx, double mouthHalf, double cornerMouth, double dropDepth, List<RailSegment> railColliders, List<FacingSegment> facingSegments, List<ShelfLine> shelfLines, List<ArcJaw> jawArcs, List<PocketRegion> pocketRegions) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
            this.cx = cx;
            this.mouthHalf = mouthHalf;
            this.cornerMouth = cornerMouth;
            this.dropDepth = dropDepth;
            this.railColliders = railColliders;
            this.facingSegments = facingSegments;
            this.shelfLines = shelfLines;
            this.jawArcs = jawArcs;
            this.pocketRegions = pocketRegions;
        }

        private static PocketModel fromBounds(TableBounds b, double ballRadius) {
            double left = b.left();
            double right = b.right();
            double top = b.top();
            double bottom = b.bottom();
            double cx = (left + right) * 0.5;
            // Rail cutouts are driven by regulation mouth widths from PhysicsConfig.
            double mouthHalf = PhysicsConfig.SIDE_POCKET_MOUTH_MIN_M * 0.5;
            double cornerMouth = PhysicsConfig.CORNER_POCKET_MOUTH_MIN_M * 0.5;
            // Shelf/depth proxy for capture geometry.
            double dropDepth = Math.max(PhysicsConfig.CORNER_SHELF_MIN_M, ballRadius * PhysicsConfig.POCKET_DROP_DEPTH_MULTIPLIER);
            double jawR = ballRadius * PhysicsConfig.POCKET_JAW_RADIUS_MULTIPLIER;
            double cornerCenterOffset = cornerMouth;
            double sideCenterOffset = mouthHalf;
            double facingLength = Math.max(ballRadius * 0.55, dropDepth * 0.55);
            Vector2 topLeftCenter = new Vector2(left - cornerCenterOffset, top - cornerCenterOffset);
            Vector2 topMiddleCenter = new Vector2(cx, top - sideCenterOffset);
            Vector2 topRightCenter = new Vector2(right + cornerCenterOffset, top - cornerCenterOffset);
            Vector2 bottomLeftCenter = new Vector2(left - cornerCenterOffset, bottom + cornerCenterOffset);
            Vector2 bottomMiddleCenter = new Vector2(cx, bottom + sideCenterOffset);
            Vector2 bottomRightCenter = new Vector2(right + cornerCenterOffset, bottom + cornerCenterOffset);

            List<RailSegment> segs = new ArrayList<>();
            addRailSegment(segs, new Vector2(left + cornerMouth, top), new Vector2(cx - mouthHalf, top), RailSide.TOP);
            addRailSegment(segs, new Vector2(cx + mouthHalf, top), new Vector2(right - cornerMouth, top), RailSide.TOP);
            addRailSegment(segs, new Vector2(left + cornerMouth, bottom), new Vector2(cx - mouthHalf, bottom), RailSide.BOTTOM);
            addRailSegment(segs, new Vector2(cx + mouthHalf, bottom), new Vector2(right - cornerMouth, bottom), RailSide.BOTTOM);
            addRailSegment(segs, new Vector2(left, top + cornerMouth), new Vector2(left, bottom - cornerMouth), RailSide.LEFT);
            addRailSegment(segs, new Vector2(right, top + cornerMouth), new Vector2(right, bottom - cornerMouth), RailSide.RIGHT);
            if (segs.isEmpty()) {
                throw new IllegalStateException("No rail segments constructed.");
            }

            List<FacingSegment> facings = new ArrayList<>();
            addFacingFromAnchor(facings, new Vector2(left + cornerMouth, top), topLeftCenter, facingLength);
            addFacingFromAnchor(facings, new Vector2(left, top + cornerMouth), topLeftCenter, facingLength);
            addFacingFromAnchor(facings, new Vector2(cx - mouthHalf, top), topMiddleCenter, facingLength);
            addFacingFromAnchor(facings, new Vector2(cx + mouthHalf, top), topMiddleCenter, facingLength);
            addFacingFromAnchor(facings, new Vector2(right - cornerMouth, top), topRightCenter, facingLength);
            addFacingFromAnchor(facings, new Vector2(right, top + cornerMouth), topRightCenter, facingLength);
            addFacingFromAnchor(facings, new Vector2(left, bottom - cornerMouth), bottomLeftCenter, facingLength);
            addFacingFromAnchor(facings, new Vector2(left + cornerMouth, bottom), bottomLeftCenter, facingLength);
            addFacingFromAnchor(facings, new Vector2(cx - mouthHalf, bottom), bottomMiddleCenter, facingLength);
            addFacingFromAnchor(facings, new Vector2(cx + mouthHalf, bottom), bottomMiddleCenter, facingLength);
            addFacingFromAnchor(facings, new Vector2(right, bottom - cornerMouth), bottomRightCenter, facingLength);
            addFacingFromAnchor(facings, new Vector2(right - cornerMouth, bottom), bottomRightCenter, facingLength);

            List<ShelfLine> shelves = new ArrayList<>();
            shelves.add(new ShelfLine(new Segment2(new Vector2(left + cornerMouth, top + dropDepth), new Vector2(left + dropDepth, top + cornerMouth)), new Vector2(1, 1)));
            shelves.add(new ShelfLine(new Segment2(new Vector2(right - cornerMouth, top + dropDepth), new Vector2(right - dropDepth, top + cornerMouth)), new Vector2(-1, 1)));
            shelves.add(new ShelfLine(new Segment2(new Vector2(left + cornerMouth, bottom - dropDepth), new Vector2(left + dropDepth, bottom - cornerMouth)), new Vector2(1, -1)));
            shelves.add(new ShelfLine(new Segment2(new Vector2(right - cornerMouth, bottom - dropDepth), new Vector2(right - dropDepth, bottom - cornerMouth)), new Vector2(-1, -1)));
            shelves.add(new ShelfLine(new Segment2(new Vector2(cx - mouthHalf, top + dropDepth), new Vector2(cx + mouthHalf, top + dropDepth)), new Vector2(0, 1)));
            shelves.add(new ShelfLine(new Segment2(new Vector2(cx - mouthHalf, bottom - dropDepth), new Vector2(cx + mouthHalf, bottom - dropDepth)), new Vector2(0, -1)));

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

            List<PocketRegion> regions = new ArrayList<>();
            regions.add(new PocketRegion(topLeftCenter, cornerMouth, false));
            regions.add(new PocketRegion(topMiddleCenter, mouthHalf, true));
            regions.add(new PocketRegion(topRightCenter, cornerMouth, false));
            regions.add(new PocketRegion(bottomLeftCenter, cornerMouth, false));
            regions.add(new PocketRegion(bottomMiddleCenter, mouthHalf, true));
            regions.add(new PocketRegion(bottomRightCenter, cornerMouth, false));

            return new PocketModel(left, right, top, bottom, cx, mouthHalf, cornerMouth, dropDepth, List.copyOf(segs), List.copyOf(facings), List.copyOf(shelves), List.copyOf(jaws), List.copyOf(regions));
        }

        private static void addRailSegment(List<RailSegment> out, Vector2 a, Vector2 b, RailSide side) {
            if (b.sub(a).lengthSq() <= 1e-12) {
                return;
            }
            out.add(new RailSegment(new Segment2(a, b), side));
        }

        private static void addFacingFromAnchor(List<FacingSegment> out, Vector2 anchor, Vector2 pocketCenter, double facingLength) {
            Vector2 toPocket = pocketCenter.sub(anchor);
            if (toPocket.lengthSq() <= 1e-12) {
                return;
            }
            Vector2 direction = toPocket.normalized();
            Vector2 tip = anchor.add(direction.mul(facingLength));
            Vector2 bedNormal = anchor.sub(pocketCenter);
            if (bedNormal.lengthSq() <= 1e-12) {
                return;
            }
            out.add(new FacingSegment(new Segment2(anchor, tip), bedNormal));
        }

        private int railSegmentCount() {
            return railColliders.size();
        }

        private int railSegmentCountBySide(RailSide side) {
            int count = 0;
            for (RailSegment rail : railColliders) {
                if (rail.side == side) {
                    count++;
                }
            }
            return count;
        }

        private PocketRegion nearestPocketRegion(Vector2 p) {
            PocketRegion best = null;
            double bestD = Double.POSITIVE_INFINITY;
            for (PocketRegion region : pocketRegions) {
                double d = p.sub(region.center).lengthSq();
                if (d < bestD) {
                    bestD = d;
                    best = region;
                }
            }
            return best;
        }

        private int pocketIndex(PocketRegion target) {
            for (int i = 0; i < pocketRegions.size(); i++) {
                if (pocketRegions.get(i) == target) {
                    return i;
                }
            }
            return -1;
        }

        private double captureApproachDamping(Vector2 p, double ballRadius, double dtSeconds) {
            double damping = 1.0;
            for (PocketRegion region : pocketRegions) {
                damping = Math.min(damping, approachDampingAt(p, ballRadius, dtSeconds, region.center, region.radius));
            }
            return damping;
        }

        private boolean isInPocketMouthOpenRegion(Vector2 p, double ballRadius) {
            for (PocketRegion region : pocketRegions) {
                double openMouthRadius = region.radius - (ballRadius * 0.25);
                if (openMouthRadius <= 0.0) {
                    continue;
                }
                if (p.sub(region.center).length() < openMouthRadius && isWithinMouthSpan(p, region)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isWithinMouthSpan(Vector2 p, PocketRegion region) {
            if (region.sidePocket) {
                return Math.abs(p.x() - cx) <= mouthHalf;
            }
            if (region.center.x() < cx) {
                return p.x() <= left + cornerMouth;
            }
            return p.x() >= right - cornerMouth;
        }

        private boolean isPottedAt(Vector2 p, double ballRadius, Vector2 center, double pocketRadius) {
            double captureRadius = pocketRadius - ballRadius - (ballRadius * PhysicsConfig.POCKET_CAPTURE_LIP_MARGIN);
            if (captureRadius <= 0.0) {
                return false;
            }
            return p.sub(center).length() <= captureRadius;
        }

        private PocketCapture captureCandidate(Vector2 p, double ballRadius) {
            double margin = ballRadius * PhysicsConfig.POCKET_CAPTURE_LIP_MARGIN;
            for (ShelfLine line : shelfLines) {
                Vector2 ab = line.segment.b.sub(line.segment.a);
                double abLenSq = ab.lengthSq();
                if (abLenSq <= 1e-12) {
                    continue;
                }
                double t = dot(p.sub(line.segment.a), ab) / abLenSq;
                if (t < 0.0 || t > 1.0) {
                    continue;
                }
                Vector2 onSeg = line.segment.a.add(ab.mul(t));
                double signed = dot(p.sub(onSeg), line.inTableNormal);
                if (signed >= -margin) {
                    continue;
                }
                Vector2 pc = closestPocketCenter(p);
                if (pc == null) {
                    continue;
                }
                double pocketRadius = pocketRadiusForCenter(pc);
                if (!isPottedAt(p, ballRadius, pc, pocketRadius)) {
                    continue;
                }
                return new PocketCapture(pc);
            }
            return null;
        }

        private Vector2 closestPocketCenter(Vector2 p) {
            Vector2 best = null;
            double bestD = Double.POSITIVE_INFINITY;
            for (PocketRegion region : pocketRegions) {
                double d = p.sub(region.center).lengthSq();
                if (d < bestD) {
                    bestD = d;
                    best = region.center;
                }
            }
            return best;
        }

        private double pocketRadiusForCenter(Vector2 center) {
            for (PocketRegion region : pocketRegions) {
                if (region.center.sub(center).lengthSq() <= 1e-12) {
                    return region.radius;
                }
            }
            return mouthHalf;
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

    private static double normalizeDeg(double deg) {
        double out = deg % 360.0;
        if (out < 0) {
            out += 360.0;
        }
        return out;
    }
}
