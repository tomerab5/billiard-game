package com.billiardgame.physics;

public final class PhysicsConfig {
    // [Parameters and defaults] Pool ball diameter/radius from regulation values.
    public static final double BALL_DIAMETER_M = 0.05715;
    public static final double BALL_RADIUS_M = BALL_DIAMETER_M * 0.5;
    // [Parameters and defaults] 0.170 kg is near upper WPA range.
    public static final double BALL_MASS_KG = 0.170;
    // [Continuous dynamics on cloth] Gravity.
    public static final double G_M_PER_S2 = 9.81;
    // [Continuous dynamics on cloth] Solid sphere inertia scale I = 2/5 mR^2.
    public static final double SOLID_SPHERE_INERTIA_SCALE = 2.0 / 5.0;

    // [Continuous dynamics on cloth] Sliding friction (spec default ~0.21).
    public static final double MU_SLIDING = 0.21;
    // [Continuous dynamics on cloth] Rolling resistance (spec default ~0.013).
    public static final double DEFAULT_MU_ROLLING = 0.013;
    // [Continuous dynamics on cloth] UI tuning bounds for rolling mu.
    public static final double MIN_MU_ROLLING = 0.005;
    public static final double MAX_MU_ROLLING = 0.024;
    // [Sidespin decay] alpha_z around 22 rad/s^2.
    public static final double SPIN_DECAY_RAD_PER_S2 = 22.0;

    // [Numerical stability] Sleep / transition epsilons.
    public static final double STOP_EPS_M_PER_S = 0.030;
    public static final double SLIP_EPS_M_PER_S = 0.030;
    public static final double STOP_SPIN_EPS_RAD_PER_S = 0.25;
    public static final double SPIN_DECAY_SMOOTH_EPS_RAD_PER_S = 1.0;
    public static final double COLLISION_EPS = 1e-9;
    public static final double ORIENTATION_INTEGRATION_EPS_RAD = 1e-8;
    public static final double BALL_COLLISION_TANGENTIAL_EPS_M_PER_S = 1e-4;
    public static final double SLIP_TO_ROLLING_EPS_SCALE = 1.0;
    public static final double FIXED_TIME_STEP_SECONDS = 1.0 / 120.0;

    // [Ball-ball collision physics] Impulse coefficients.
    public static final double BALL_BALL_RESTITUTION = 0.90;
    public static final double MU_BALL_BALL = 0.05;
    // [Ball-cushion collisions] e_bw and mu_bw defaults from cited ranges.
    public static final double CUSHION_RESTITUTION = 0.95;
    public static final double MU_CUSHION = 0.14;
    // Backward-compatible aliases used by existing UI/HUD text.
    public static final double RAIL_RESTITUTION = CUSHION_RESTITUTION;
    public static final double MU_RAIL = MU_CUSHION;
    // [Cue-ball strike] Tangential tip offset cap proxy.
    public static final double MU_TIP = 0.60;
    public static final double ROLLING_FRICTION_UI_STEP = 0.001;

    // [Regulation-driven geometry] 8-foot style table playing surface.
    public static final double TABLE_WIDTH_M = 2.54;
    public static final double TABLE_HEIGHT_M = 1.27;
    // [Ball-cushion geometry] Nose height 63.5% of ball diameter.
    public static final double CUSHION_NOSE_HEIGHT_M = 0.635 * BALL_DIAMETER_M;

    // [Ball-pocket physics] WPA mouth and shelf ranges (pool).
    public static final double CORNER_POCKET_MOUTH_MIN_M = 0.1143;
    public static final double CORNER_POCKET_MOUTH_MAX_M = 0.117475;
    public static final double SIDE_POCKET_MOUTH_MIN_M = 0.1270;
    public static final double SIDE_POCKET_MOUTH_MAX_M = 0.130175;
    public static final double CORNER_POCKET_RADIUS_M = CORNER_POCKET_MOUTH_MIN_M * 0.5;
    public static final double CORNER_SHELF_MIN_M = 0.0254;
    public static final double CORNER_SHELF_MAX_M = 0.05715;
    public static final double SIDE_SHELF_MIN_M = 0.0;
    public static final double SIDE_SHELF_MAX_M = 0.009525;
    public static final double POCKET_BACK_DRAFT_MIN_DEG = 12.0;
    public static final double POCKET_BACK_DRAFT_MAX_DEG = 15.0;
    public static final double POCKET_CORNER_CUT_ANGLE_DEG = 142.0;
    public static final double POCKET_SIDE_CUT_ANGLE_DEG = 104.0;
    // [Ball-pocket physics] Current jaw model multipliers.
    public static final double POCKET_SIDE_MOUTH_HALF_MULTIPLIER = 2.2;
    public static final double POCKET_CORNER_MOUTH_MULTIPLIER = 2.9;
    public static final double POCKET_DROP_DEPTH_MULTIPLIER = 1.55;
    public static final double POCKET_JAW_RADIUS_MULTIPLIER = 0.92;
    public static final double POCKET_CAPTURE_LIP_MARGIN = 0.25;
    public static final double POCKET_APPROACH_DAMPING_RATE = 0.28;

    // [Ball-pocket physics] Placeholder material coefficients for jaw/liner models.
    public static final double POCKET_JAW_RESTITUTION = 0.40;
    public static final double POCKET_JAW_FRICTION = 0.35;
    public static final double POCKET_LINER_RESTITUTION = 0.30;
    public static final double POCKET_LINER_FRICTION = 0.45;

    private PhysicsConfig() {
    }
}
