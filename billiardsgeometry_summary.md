# Billiards Geometry (PDF) — Project Notes

**Source file:** `billiardsgeometry.pdf`  
**PDF metadata:** Created 2005-07-20 20:32:23 (from PDF `/CreationDate`)  
**What this text is:** A *mathematical billiards* monograph/lecture-notes style document (Serge Tabachnikov is credited in the preface).  
**Important scope note:** This document models *idealized* billiards (point-mass, perfectly elastic reflections/collisions). It is very useful for the **core collision + reflection math** in our 2D/3D simulator, but it **does not** give real-pool empirical parameters (cloth friction numbers, cushion compliance, spin transfer, etc.). Use it as a **foundation**, not as a complete physical model.

---

## 1) The “mathematical billiard” model (baseline assumptions)

A “billiard table” is a domain (often planar). A “ball” is a **point-mass** moving freely:
- **Free flight:** straight line at constant speed until boundary contact.
- **Boundary interaction:** **elastic/specular reflection** (“angle of incidence = angle of reflection” in Euclidean geometry).

### Implementation takeaway
For a smooth boundary with outward unit normal **n** at contact, ideal reflection of velocity **v** is:

- Decompose:  
  - normal component: `v_n = (v·n) n`  
  - tangential component: `v_t = v - v_n`
- Reflect:  
  - `v' = v_t - v_n = v - 2 (v·n) n`

This is the most important “clean” reflection rule to keep around for correctness tests.

---

## 2) Elastic ball–ball collisions (key formulas and geometry)

The document explains how elastic collisions of hard balls can be reduced to simple rules by decomposing velocities into:
- **Radial component** along the axis connecting centers at impact
- **Tangential component** perpendicular to that axis

### 2.1 Tangential vs radial behavior
At collision:
- Tangential components **do not change**
- Radial components update exactly like **1D elastic point-masses**

This is a standard (and very implementable) rule for 2D circle collisions and 3D sphere collisions.

### 2.2 Equal masses (pool balls) simplification
For identical masses, the **radial components are exchanged** between the two balls, while tangential components remain the same.

A famous consequence (useful for unit tests and debugging):
- If two identical balls collide and **one was initially at rest**, the two outgoing velocity directions are **orthogonal**.

### Implementation takeaway (our engine)
For two balls with equal mass and radii:
1. Compute collision normal `n = normalize(p2 - p1)`.
2. Relative velocity `dv = v2 - v1`.
3. Normal impulse scalar for perfectly elastic collision:
   - `j = -(1 + e) * (dv · n) / (1/m1 + 1/m2)` with `e=1`, `m1=m2`
4. Apply:
   - `v1' = v1 - (j/m1) n`
   - `v2' = v2 + (j/m2) n`

Where `e` is coefficient of restitution (ideal = 1). For real pools, `e < 1` for small energy loss.

---

## 3) Collisions as “billiards in configuration space” (conceptual tool)

One of the document’s most useful ideas: some multi-body elastic systems can be converted into a **single point** moving in a geometric “configuration space,” reflecting off boundaries.

Examples described:
- **Two point-masses on a half-line:** state `(x1, x2)` lives in a wedge `0 ≤ x1 ≤ x2`; collisions become reflections in the wedge boundaries.
- **Two identical discs on a torus (periodic box):** after reducing center-of-mass, the configuration space becomes a torus with a circular “hole” (collision set). Collision becomes a reflection off that boundary.

### Why we care
Even if we don’t use these reductions directly, they provide:
- A rigorous way to reason about “why reflection formulas are correct”
- A clean mental model for debugging multi-ball collisions

---

## 4) Optics analogy and generalized reflection (Snell / Fermat / Finsler)

The document connects billiards to **geometrical optics**:
- Light paths extremize travel time (Fermat principle).
- In anisotropic / inhomogeneous media, rays satisfy **Snell’s law** and can be described via a **Finsler metric** (indicatrices = direction-dependent “unit spheres”).

It then derives a **generalized billiard reflection law** for Finsler geometry.

### Why we care (practically)
We are building **real** pool physics, where:
- Ball experiences direction-dependent rolling/sliding friction and spin effects
- Cushions have compliance and non-ideal energy exchange

A Finsler-like viewpoint can inspire later extensions (e.g., direction-dependent dissipation), but it’s conceptual—this text doesn’t provide pool-specific friction models.

---

## 5) Polygon billiards + unfolding (useful for “perfect rail” math)

For billiards in polygons:
- Trajectories can be studied by **unfolding**: reflect the polygon instead of reflecting the ray, turning the path into a straight line in a tiled plane.
- In “rational” polygons (all angles rational multiples of π), directions belong to a **finite set** (action of a dihedral group).

### Implementation use cases
- Fast verification of ideal mirror reflections (great for debug modes)
- Deterministic “aim preview” lines for an idealized table model

---

## 6) What this PDF does NOT give us (but we need for “realistic pool”)

To reach “most realistic 3D physics possible,” we still need additional models that this PDF does not cover:

- **Spin / angular dynamics:** cue tip offset, torque, angular momentum, spin decay, spin–translation coupling
- **Sliding vs rolling friction:** transition from sliding to rolling, different coefficients
- **Cushion interaction:** compression, tangential friction, spin transfer, restitution (normal vs tangential)
- **Ball–ball collision with spin:** tangential impulse at contact, post-collision spin changes
- **Ball deformation / contact time:** Hertzian contact approximations (normal force vs penetration), damping
- **Pocket geometry + rattle behavior:** pocket acceptance, shelf, jaw, facings, rail angles

### Suggested next references (for later)
When you’re ready, we should read pool-specific physics references (e.g., resources focused on cue sports mechanics and validated coefficients), then decide what level we implement.

---

## 7) How we’ll use these notes in our codebase

Planned usage:
1. Use **specular reflection** and **elastic collision** rules as the “correctness baseline” (unit tests).
2. Add realism as controlled deviations:
   - coefficients of restitution `e < 1`
   - rolling/sliding friction with a clear energy model
   - spin, then spin–cushion/ball coupling
3. Keep a “toggle” debug mode:
   - **Idealized billiards** (this PDF)
   - **Realistic pool** (incremental extensions)

---

## Appendix: quick unit-test ideas derived from the document

- **Ball-ball orthogonality test:** equal masses, one at rest → outgoing velocities perpendicular (within tolerance).
- **Energy conservation test (ideal mode):** sum of kinetic energies conserved after collision.
- **Reflection law test:** incoming angle vs outgoing angle about normal are equal (ideal cushion mode).
