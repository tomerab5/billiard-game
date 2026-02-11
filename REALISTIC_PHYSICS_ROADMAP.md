# High-Realism Billiards Physics Roadmap (Java)

This document is a **living reference** for building a high-fidelity pool/billiards simulator.
It combines (1) **assumptions and constants**, (2) **models to implement**, and (3) a **step-by-step implementation plan**.

> Goal: a simulator that reproduces real cue-sport behavior: sliding→rolling transition, draw/follow, side-spin (“english”), throw, rail effects, and (optionally) small hops/jumps.

---

## 0) Core design decisions

### Units
- Use **SI units internally** (meters, seconds, kg).
- Rendering uses pixels, so define a single conversion `PX_PER_M` from table geometry.

### State per ball (3D dynamics, 2D position is OK at first)
Even if the center stays on the table plane, realism requires **3D angular velocity**.
Store per ball:
- position `p = (x, y)` (m)
- linear velocity `v = (vx, vy)` (m/s)
- angular velocity `w = (wx, wy, wz)` (rad/s)

Ball orientation is unnecessary (sphere symmetry), unless you want texture rotation.

### Key constants
- Ball radius `R` and mass `m` (regulation values are documented by WPA; see references).
- Moment of inertia for a solid sphere: `I = 2/5 m R^2`.

---

## 1) Recommended starting physical constants (tunable)

Use these as defaults; we’ll calibrate them later.

### Ball specs (regulation ranges)
- Diameter: 2.25 in = 57.15 mm
- Mass range: 156–170 g (pick 0.168 kg as a default)

### Cloth / table interaction
- Sliding friction coefficient `μ_s`: start at **0.20**
- Rolling resistance coefficient `μ_r`: start at **0.010**
- Spin deceleration (about vertical axis) `α_sp`: start at **10 rad/s²**

### Ball-ball collision
- Normal COR `e_bb`: start at **0.94**
- Ball-ball friction coefficient `μ_bb`: start at **0.06**

### Ball-rail collision (simplified)
- If using a coefficient model: start with rail COR in **0.75–0.85**
- Rail friction: start at **0.14** (see Mathavan et al. estimate)

---

## 2) Ball–cloth model (the biggest realism win)

We treat ball motion as phases driven by **slip velocity at the contact point** with the cloth.

### Contact point slip velocity (relative to cloth)
Let `k = (0,0,1)` be up.
The vector from center to contact point is `r_c = -R k`.

Velocity of contact point:
`v_c = v + w × r_c`

Slip velocity relative to cloth:
`u = v_c`  (cloth is stationary)

### Phase logic
- If `|u| > slip_eps`: **SLIDING**
- Else if `|v| > roll_eps`: **ROLLING** (no slip, but still energy loss via rolling resistance)
- Else if `|w| > spin_eps`: **SPINNING IN PLACE**
- Else: **REST**

### Sliding dynamics (Coulomb friction)
Friction force magnitude: `|F| = μ_s m g`, direction opposite slip: `F = - μ_s m g * u_hat`

- Linear acceleration: `a = F / m = - μ_s g * u_hat`
- Torque about center: `τ = r_c × F`
- Angular accel: `α = τ / I`

This naturally produces draw/follow and side-spin coupling.  
(We can implement this with sub-steps or a stable semi-implicit integrator. Later we can switch to closed-form updates.)

### Rolling dynamics
No slip constraint implies approximately:
`v ≈ (w × r_c)`  (horizontal components)

Energy loss via rolling resistance:
`a = - μ_r g * v_hat`

Also decay `wz` (“spin”) using `α_sp`:
`wz = approach_to_zero(wz, α_sp * dt)`

---

## 3) Ball–ball collision with spin + friction (impulse model)

We treat collision as **instantaneous** but include friction impulse at the contact.

At impact:
- Normal `n = normalize(p2 - p1)` (in the table plane)
- Contact points relative to centers: `r1 = R n`, `r2 = -R n`

Relative velocity at contact:
`v_rel = (v2 + w2×r2) - (v1 + w1×r1)`

Split into:
- normal component `vn = (v_rel·n)`
- tangential component `vt = v_rel - vn n`

Compute normal impulse `J_n` using restitution `e_bb`.
Compute tangential impulse `J_t` to oppose `vt` but clamp by Coulomb friction:
`|J_t| ≤ μ_bb * J_n`
- If sticking possible, solve for `J_t` that makes tangential relative velocity zero.
- Else slipping: `J_t = - μ_bb * J_n * normalize(vt)`

Apply impulses:
- `v += ± J / m`
- `w += I^{-1} (r × (±J))`

This produces throw, spin transfer, and more realistic post-collision outcomes.

---

## 4) Ball–rail (cushion) with spin + (optional) hop

Ball-rail is notoriously hard because the cushion deforms and contact is not point-like.
We can start with an **instantaneous impulse** model that uses:
- a rail contact normal and tangential direction
- rail restitution and friction
- *optionally* an “effective contact height” parameter ε (rail apex above center) to generate vertical effects later

We can later upgrade to the Mathavan 2010 cushion ODE model or a lookup-table fit.

---

## 5) Cue–ball strike (tip offset, masse, jump)

Model cue strike as an impulse applied at a point on the ball:
- Choose tip contact point from user input: `(offsetX, offsetY, height)` on the ball surface
- Impulse direction from cue direction + elevation angle
- Use cue-tip friction to limit tangential impulse (miscue condition)

This lets us reproduce:
- follow/draw (top/bottom)
- side spin
- masse/jump (advanced, optional)

---

## 6) Implementation plan (Codex-friendly milestones)

### Milestone A — Switch to SI units + constants
- Add `PhysicsConfig` with all parameters above.
- Convert table geometry to meters; define `PX_PER_M`.

### Milestone B — 3D ball state + ball-cloth phases
- Add `Vector3`, `BallState {p2, v2, w3, phase}`
- Implement slip-based friction and rolling resistance.
- Unit tests: sliding slows speed; transitions to rolling; rest state.

### Milestone C — Ball-ball with spin/friction impulses
- Replace current 2D impulse with 3D contact impulse.
- Tests: head-on swap; throw with side spin.

### Milestone D — Rail model upgrade
- Replace simple reflection with impulse + friction.
- Tests: bounce angle consistency; spin change on rail.

### Milestone E — Cue strike model
- Implement tip offset, max human cue speed, miscue limit.
- Tests: top spin vs draw outcomes.

### Milestone F — Optional high-end upgrades
- Compliant contact (Hertz spring-damper) for collisions (requires smaller dt or event solver).
- Airborne motion for jump/masse.
- Pocket jaw geometry + rattle.

---

## 7) References we are basing this on (keep adding)
- WPA Recommended Equipment Specifications (ball diameter and mass range).
- Dr. Dave “Pool Physics Property Constants” (ranges for μ, e, etc.).
- Peskin (2020) “Collision of Billiard Balls in 3D with Spin and Friction” (Coulomb friction during collision).
- Mathavan/Jackson/Parkin (2010) cushion impact model and fitted coefficients.
- Your existing “Billiards Geometry” PDF summary (ideal baseline).

