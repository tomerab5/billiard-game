package com.billiardgame.physics;

public final class PhysicsConfig {
    // Seed values aligned with commonly cited Dr Dave billiards references.
    public static final double BALL_RADIUS_M = 0.028575;
    public static final double BALL_MASS_KG = 0.170097;
    public static final double G_M_PER_S2 = 9.81;
    public static final double MU_SLIDING = 0.20;
    public static final double MU_ROLLING = 0.010;
    public static final double SPIN_DECAY_RAD_PER_S2 = 8.0;

    public static final double STOP_EPS_M_PER_S = 0.020;
    public static final double SLIP_EPS_M_PER_S = 0.030;
    public static final double STOP_SPIN_EPS_RAD_PER_S = 0.25;

    public static final double BALL_BALL_RESTITUTION = 0.98;
    public static final double RAIL_RESTITUTION = 0.92;

    public static final double TABLE_WIDTH_M = 2.54;
    public static final double TABLE_HEIGHT_M = 1.27;

    private PhysicsConfig() {
    }
}
