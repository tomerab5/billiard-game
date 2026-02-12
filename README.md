# Billiards Simulator (Java + JavaFX)

Deterministic 2D billiards simulator focused on realistic physics first, visuals second.

## Tech Stack

- Java 17
- JavaFX (desktop UI)
- Maven Wrapper (`mvnw.cmd`)
- JUnit 5

## Project Goals

- Deterministic SI-unit physics
- Full spin-aware ball simulation (`Vector3` angular velocity + `Quaternion` orientation)
- Realistic ball-ball and ball-rail responses
- Clean separation: physics engine independent from rendering
- Polished 2.5D visuals on Canvas

## Current Architecture

- `com.billiardgame.physics`
  - Pure physics domain (no JavaFX types)
  - Fixed-step updates
  - Ball state with linear + angular dynamics
  - Collision, friction, spin decay, pocket capture
- `com.billiardgame.ui`
  - JavaFX Canvas renderer and input loop
  - Aiming guides and cue animation
  - 2.5D lighting/shading effects
- `com.billiardgame.game`
  - Lightweight game data objects (`Ball`, `TableState`)

## Build, Run, Test

Run app:

```powershell
.\mvnw.cmd javafx:run
```

Run tests:

```powershell
.\mvnw.cmd test
```

## Controls

- `Mouse Move`: aim direction
- `Left Mouse Hold`: charge shot
- `Left Mouse Release`: cue moves forward, then strikes
- `Right Mouse Drag`: move cue tip offset on cue ball
- `Mouse Wheel`: adjust vertical tip offset
- `R`: reset table
- `[` / `]`: decrease/increase rolling friction
- `T`: spin debug text
- `V`: spin decals/markers
- `H`: table texture + table lighting toggle
- `L`: advanced ball lighting toggle
- `P`: pocket debug geometry
- `X`: cycle tip presets
- `C`: reset tip offset
- `8` / `9` / `0`: cue style (Classic / Luxury / Sport)

## Master Plan Status

### Phase 1 - Core Architecture
- `DONE` SI units in physics, fixed timestep, physics/UI separation
- `DONE` snapshot-style world-to-render data flow
- `DONE` ball model includes `position`, `velocity`, `angular velocity`, `orientation`, `mass`, `radius`

### Phase 2 - Realistic Physics Core
- `DONE` cloth interaction: sliding friction, rolling decel, slip transition, spin decay
- `DONE` ball-ball impulse with tangential friction and spin transfer
- `DONE` ball-rail restitution + tangential friction
- `PARTIAL` pocket system:
  - `DONE` deterministic pocket capture as non-colliding sensors
  - `PARTIAL` advanced jaw/rattle/shelf realism is not fully implemented

### Phase 3 - Cue Model
- `DONE` tip offset strike model and spin generation
- `DONE` shot speed cap behavior in current model
- `PARTIAL` detailed miscue/edge-case cue physics can be expanded

### Phase 4 - Game Layer
- `NOT STARTED` rules engine (8-ball/9-ball, fouls, turns, ball-in-hand)
- `PARTIAL` local simulation state flow exists (aiming/charging/moving), but no full rules-state engine yet

### Phase 5 - Advanced Systems
- `NOT STARTED` AI opponent
- `NOT STARTED` deterministic replay system
- `NOT STARTED` broad performance optimizations (spatial partitioning, solver tuning)

### Phase 6 - Visual Polish
- `DONE` strong 2.5D Canvas rendering baseline:
  - cloth texture + nap/lighting
  - rail and pocket AO/depth cues
  - improved contact shadows
  - rotating subtle spin decals
  - cue visual styles
- `NOT STARTED` camera zoom system
- `NOT ACTIVE` true JavaFX 3D mode in current code line

## What Is Left To Finish (Priority)

1. Implement game rules layer (8-ball/9-ball, fouls, turn resolution).
2. Upgrade pocket/jaw behavior to more realistic shelf + rattle interactions.
3. Add replay recording/playback for deterministic debugging and QA.
4. Add AI shot planning (spin-aware) after rules are stable.
5. Profile and optimize collision scaling for larger ball counts/more complexity.

## Development Principles

- Physics correctness before graphics
- Determinism over visual tricks
- Keep physics and rendering decoupled
- Prefer stable milestones with tests after each major change
