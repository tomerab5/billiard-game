package com.billiardgame.physics;

public final class PhysicsConstants {
    public static final double FRICTION_PER_SEC = 1.5;
    public static final double STOP_EPS = 5.0;
    public static final double RESTITUTION = 0.98;
    public static final double RAIL_RESTITUTION = 0.92;
    public static final double MAX_SHOT_SPEED = 1000.0;
    public static final double CHARGE_TIME_TO_MAX = 1.0;

    private PhysicsConstants() {
    }
}
