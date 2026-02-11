package com.billiardgame.physics;

public final class PhysicsConstants {
    public static final double SLIDING_DECEL_PX_PER_S2 = 900.0;
    public static final double ROLLING_DECEL_PX_PER_S2 = 220.0;
    public static final double ROLLING_THRESHOLD_PX_PER_S = 250.0;
    public static final double STOP_EPS_PX_PER_S = 8.0;
    public static final double RESTITUTION = 0.98;
    public static final double RAIL_RESTITUTION = 0.92;
    public static final double MAX_SHOT_SPEED = 1000.0;
    public static final double CHARGE_TIME_TO_MAX = 1.0;

    private PhysicsConstants() {
    }
}
