# Physics Spec (Step 0)

Source of truth: `Physical and Mathematical Requirements for an Accurate 3D Billiards Simulation.txt`.

## Equations We Implement First

### Rigid body state (per ball)
- Position/velocity: `x_dot = v`, `m * v_dot = F`.
- Angular velocity: `I * omega_dot = tau`.
- Orientation update: `q_dot = 0.5 * q ⊗ (0, omega)`.

### Sphere inertia
- Solid sphere: `I = (2/5) * m * R^2`.

### Cloth slip kinematics
- Contact-point velocity: `v_c = v + omega x r`, where `r = -R * n`.
- Slip velocity: `u = v_c - (v_c . n) * n`.

### Sliding friction regime
- `F_slide = -mu_slide * N * u_hat`.
- Approximate linear decel: `a_slide = mu_slide * g`.
- Coupled angular accel from contact torque.

### Rolling regime
- `F_roll = -mu_roll * N * v_hat`.
- Approximate linear decel: `a_roll = mu_roll * g`.
- Sidespin decay: `omega_z_dot = -alpha_z * sign(omega_z)`.

### Impulse collisions
- Ball-ball normal impulse with restitution `e_bb`.
- Ball-ball tangential impulse clamped by `|J_t| <= mu_bb * |J_n|`.
- Ball-cushion normal impulse with `e_cushion`, tangential clamp with `mu_cushion`.

## Constants and Ranges (SI)

### Ball
- `BALL_DIAMETER_M = 0.05715`.
- `BALL_RADIUS_M = 0.028575`.
- `BALL_MASS_KG = 0.170`.
- `SOLID_SPHERE_INERTIA_SCALE = 2/5`.
- `G_M_PER_S2 = 9.81`.

### Cloth and spin
- `MU_SLIDING = 0.21` (spec range includes 0.178..0.245).
- `DEFAULT_MU_ROLLING = 0.013`.
- `SPIN_DECAY_RAD_PER_S2 = 22.0`.
- Rolling UI bounds: `MIN_MU_ROLLING = 0.005`, `MAX_MU_ROLLING = 0.024`.

### Impacts
- `BALL_BALL_RESTITUTION = 0.90`.
- `MU_BALL_BALL = 0.05`.
- `CUSHION_RESTITUTION = 0.95`.
- `MU_CUSHION = 0.14`.

### Cushion and pockets
- Cushion nose height target: `CUSHION_NOSE_HEIGHT_M = 0.635 * BALL_DIAMETER_M`.
- WPA pocket mouth ranges:
  - Corner: `CORNER_POCKET_MOUTH_MIN_M = 0.1143`, `CORNER_POCKET_MOUTH_MAX_M = 0.117475`.
  - Side: `SIDE_POCKET_MOUTH_MIN_M = 0.1270`, `SIDE_POCKET_MOUTH_MAX_M = 0.130175`.
- Shelf ranges:
  - Corner: `0.0254 .. 0.05715`.
  - Side: `0.0 .. 0.009525`.
- Pocket angles:
  - Back draft: `12 .. 15 deg`.
  - Corner cut: `142 deg`, side cut: `104 deg`.
- Pocket-model placeholders:
  - Jaw/liner material placeholders in `PhysicsConfig` (`POCKET_JAW_*`, `POCKET_LINER_*`).

## What We Implement First (Current Runtime Scope)

1. Deterministic 2D translation + 3D spin state.
2. Cloth sliding-to-rolling behavior with friction and spin decay.
3. Ball-ball and ball-cushion impulse collisions with Coulomb tangential clamps.
4. Pocket openings/capture approximation with configurable geometry multipliers.
5. Orientation integration from real `omega` for rendering.

## Parameter Ownership

All physics parameters live in `src/main/java/com/billiardgame/physics/PhysicsConfig.java`.
Gameplay/UI code reads physics values through `PhysicsConfig` rather than hardcoded literals.
