package com.billiardgame.ui;

import com.billiardgame.game.Ball;
import com.billiardgame.game.TableState;
import com.billiardgame.physics.PhysicsConfig;
import com.billiardgame.physics.PhysicsConstants;
import com.billiardgame.physics.PhysicsWorld;
import com.billiardgame.physics.TableBounds;
import com.billiardgame.physics.Vector2;
import com.billiardgame.physics.Vector3;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.effect.BlendMode;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.util.List;

public final class BilliardApp extends Application {
    private static final double WINDOW_WIDTH = 960;
    private static final double WINDOW_HEIGHT = 540;
    private static final double TABLE_X = 80;
    private static final double TABLE_Y = 60;
    private static final double TABLE_WIDTH = 800;
    private static final double TABLE_HEIGHT = 420;
    private static final double POCKET_RADIUS = 18;
    private static final double PIXELS_PER_METER = TABLE_WIDTH / PhysicsConfig.TABLE_WIDTH_M;
    private static final double BALL_RADIUS_PX = PhysicsConfig.BALL_RADIUS_M * PIXELS_PER_METER;

    private static final double FIXED_DT_SECONDS = 1.0 / 120.0;
    private static final double PREDICTION_MAX_DISTANCE = 1400.0;
    private static final double POST_COLLISION_PREVIEW_DISTANCE = 260.0;
    private static final double TIP_OFFSET_MAX = 0.60;
    private static final double AMBIENT = 0.25;
    private static final double DIFFUSE = 0.75;
    private static final double SPECULAR = 0.6;
    private static final double SPECULAR_SIZE_RATIO = 0.35;
    private static final Vector2 LIGHT_DIR_SCREEN = new Vector2(-0.76, -0.65).normalized();
    private static final Vector3 MARKER_LOCAL_1 = unit(0.6, 0.2, 0.77).mul(PhysicsConfig.BALL_RADIUS_M);
    private static final Vector3 MARKER_LOCAL_2 = unit(-0.3, 0.7, 0.64).mul(PhysicsConfig.BALL_RADIUS_M);

    private enum SimulatorState {
        AIMING,
        MOVING
    }

    private enum HitType {
        NONE,
        RAIL,
        BALL
    }

    private final TableState state = new TableState(
            TABLE_X,
            TABLE_Y,
            TABLE_WIDTH,
            TABLE_HEIGHT,
            List.of(
                    new Vector2(TABLE_X, TABLE_Y),
                    new Vector2(TABLE_X + TABLE_WIDTH / 2, TABLE_Y),
                    new Vector2(TABLE_X + TABLE_WIDTH, TABLE_Y),
                    new Vector2(TABLE_X, TABLE_Y + TABLE_HEIGHT),
                    new Vector2(TABLE_X + TABLE_WIDTH / 2, TABLE_Y + TABLE_HEIGHT),
                    new Vector2(TABLE_X + TABLE_WIDTH, TABLE_Y + TABLE_HEIGHT)
            ),
            new Ball(new Vector2(TABLE_X + TABLE_WIDTH * 0.33, TABLE_Y + TABLE_HEIGHT * 0.5), BALL_RADIUS_PX)
    );

    private PhysicsWorld world = createInitialWorld();

    private Vector2 mousePosition = state.cueBall().position();
    private SimulatorState simulatorState = SimulatorState.AIMING;
    private boolean chargingShot = false;
    private long shotChargeStartNanos = 0L;
    private double lastChargeSeconds = 0.0;
    private boolean showSpinDebug = false;
    private boolean showSpinMarkers = true;
    private boolean showBallShading = true;
    private boolean showPocketDebug = false;
    private Vector2 tipOffsetNorm = Vector2.ZERO;
    private int tipPresetIndex = 0;
    private boolean rightDragTip = false;

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(WINDOW_WIDTH, WINDOW_HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        canvas.setOnMouseMoved(event -> mousePosition = new Vector2(event.getX(), event.getY()));
        canvas.setOnMousePressed(event -> {
            mousePosition = new Vector2(event.getX(), event.getY());
            if (event.getButton() == MouseButton.PRIMARY && world.allBallsNearlyStopped()) {
                chargingShot = true;
                shotChargeStartNanos = System.nanoTime();
                lastChargeSeconds = 0.0;
            } else if (event.getButton() == MouseButton.SECONDARY) {
                rightDragTip = true;
            }
        });
        canvas.setOnMouseReleased(event -> {
            mousePosition = new Vector2(event.getX(), event.getY());
            if (event.getButton() == MouseButton.PRIMARY) {
                releaseShot();
            } else if (event.getButton() == MouseButton.SECONDARY) {
                rightDragTip = false;
            }
        });
        canvas.setOnMouseDragged(event -> {
            mousePosition = new Vector2(event.getX(), event.getY());
            if (rightDragTip) {
                adjustTipOffset(event.getX() - cueBallScreenX(), event.getY() - cueBallScreenY());
            }
        });
        canvas.setOnScroll(event -> {
            if (!world.allBallsNearlyStopped()) {
                return;
            }
            double delta = event.getDeltaY() > 0 ? 0.04 : -0.04;
            tipOffsetNorm = clampTipOffset(tipOffsetNorm.add(new Vector2(0, delta)));
        });

        Scene scene = new Scene(new StackPane(canvas), WINDOW_WIDTH, WINDOW_HEIGHT, Color.web("#101518"));
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.R) {
                resetWorld();
            } else if (event.getCode() == KeyCode.OPEN_BRACKET) {
                world.decreaseRollingFriction(0.001);
            } else if (event.getCode() == KeyCode.CLOSE_BRACKET) {
                world.increaseRollingFriction(0.001);
            } else if (event.getCode() == KeyCode.T) {
                showSpinDebug = !showSpinDebug;
            } else if (event.getCode() == KeyCode.V) {
                showSpinMarkers = !showSpinMarkers;
            } else if (event.getCode() == KeyCode.H) {
                showBallShading = !showBallShading;
            } else if (event.getCode() == KeyCode.P) {
                showPocketDebug = !showPocketDebug;
            } else if (event.getCode() == KeyCode.C) {
                tipOffsetNorm = Vector2.ZERO;
                tipPresetIndex = 0;
            } else if (event.getCode() == KeyCode.X) {
                cycleTipPreset();
            }
        });

        stage.setTitle("2D Billiards Simulator");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        canvas.setFocusTraversable(true);
        canvas.requestFocus();

        AnimationTimer timer = new AnimationTimer() {
            private long lastFrameNanos = -1;
            private double accumulator = 0.0;

            private long fpsWindowStartNanos = -1;
            private int framesInWindow = 0;
            private double currentFps = 0.0;

            @Override
            public void handle(long now) {
                if (lastFrameNanos < 0) {
                    lastFrameNanos = now;
                    fpsWindowStartNanos = now;
                    return;
                }

                double frameSeconds = (now - lastFrameNanos) / 1_000_000_000.0;
                lastFrameNanos = now;

                if (frameSeconds > 0.25) {
                    frameSeconds = 0.25;
                }

                accumulator += frameSeconds;

                while (accumulator >= FIXED_DT_SECONDS) {
                    update(FIXED_DT_SECONDS);
                    accumulator -= FIXED_DT_SECONDS;
                }

                framesInWindow++;
                double fpsWindowSeconds = (now - fpsWindowStartNanos) / 1_000_000_000.0;
                if (fpsWindowSeconds >= 0.5) {
                    currentFps = framesInWindow / fpsWindowSeconds;
                    framesInWindow = 0;
                    fpsWindowStartNanos = now;
                }

                render(gc, currentFps, now / 1_000_000_000.0);
            }
        };
        timer.start();
    }

    private void update(double dtSeconds) {
        world.step(dtSeconds);
        if (chargingShot) {
            long elapsedNanos = Math.max(0L, System.nanoTime() - shotChargeStartNanos);
            lastChargeSeconds = elapsedNanos / 1_000_000_000.0;
        }
        simulatorState = world.cueBallSpeed() > 0 ? SimulatorState.MOVING : SimulatorState.AIMING;
    }

    private PhysicsWorld createInitialWorld() {
        Ball cueBall = state.cueBall();
        Ball targetBall = new Ball(
                new Vector2(
                        cueBall.position().x() + (cueBall.radius() * 3.2),
                        cueBall.position().y()
                ),
                cueBall.radius()
        );
        TableBounds tableBounds = new TableBounds(
                state.tableX(),
                state.tableX() + state.tableWidth(),
                state.tableY(),
                state.tableY() + state.tableHeight()
        );
        return new PhysicsWorld(List.of(cueBall, targetBall), tableBounds, PIXELS_PER_METER);
    }

    private void releaseShot() {
        if (!chargingShot) {
            return;
        }
        chargingShot = false;

        Ball cueBall = world.cueBall();
        Vector2 toCursor = mousePosition.sub(cueBall.position());
        double len = toCursor.length();
        if (len < 1e-9) {
            lastChargeSeconds = 0.0;
            return;
        }

        double chargeRatio = Math.min(1.0, lastChargeSeconds / PhysicsConstants.CHARGE_TIME_TO_MAX);
        double speed = chargeRatio * PhysicsConstants.MAX_SHOT_SPEED;
        Vector2 direction = toCursor.mul(1.0 / len);
        world.strikeCueBall(direction, speed, tipOffsetNorm);
        lastChargeSeconds = 0.0;
    }

    private void resetWorld() {
        world = createInitialWorld();
        simulatorState = SimulatorState.AIMING;
        chargingShot = false;
        lastChargeSeconds = 0.0;
        tipOffsetNorm = Vector2.ZERO;
        tipPresetIndex = 0;
    }

    private void render(GraphicsContext gc, double fps, double timeSeconds) {
        drawRoomBackground(gc, timeSeconds);
        drawTable(gc, timeSeconds);
        drawPocketDebug(gc);
        drawAimGuide(gc);
        drawBalls(gc);
        drawBloomPass(gc, timeSeconds);
        drawHud(gc, fps);
    }

    private void drawPocketDebug(GraphicsContext gc) {
        if (!showPocketDebug) {
            return;
        }
        gc.setStroke(Color.color(0.40, 0.95, 1.0, 0.75));
        gc.setLineWidth(1.6);
        for (PhysicsWorld.DebugSegment s : world.pocketMouthSegmentsPx()) {
            gc.strokeLine(s.a().x(), s.a().y(), s.b().x(), s.b().y());
        }
        gc.setStroke(Color.color(1.0, 0.65, 0.35, 0.70));
        gc.setLineWidth(1.4);
        for (PhysicsWorld.DebugArc a : world.pocketJawArcsPx()) {
            gc.strokeArc(
                    a.center().x() - a.radius(),
                    a.center().y() - a.radius(),
                    a.radius() * 2,
                    a.radius() * 2,
                    a.startDeg(),
                    a.sweepDeg(),
                    javafx.scene.shape.ArcType.OPEN
            );
        }
    }

    private void drawRoomBackground(GraphicsContext gc, double t) {
        gc.setFill(new LinearGradient(
                0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#0d161b")),
                new Stop(0.55, Color.web("#101a20")),
                new Stop(1.0, Color.web("#070c10"))
        ));
        gc.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

        double leftGlowX = 220 + Math.sin(t * 0.4) * 28;
        double rightGlowX = WINDOW_WIDTH - 220 + Math.cos(t * 0.5) * 24;

        gc.setFill(new RadialGradient(
                0, 0, leftGlowX, 140, 290, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(0.22, 0.52, 0.45, 0.33)),
                new Stop(1.0, Color.TRANSPARENT)
        ));
        gc.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

        gc.setFill(new RadialGradient(
                0, 0, rightGlowX, 120, 270, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(0.14, 0.35, 0.50, 0.28)),
                new Stop(1.0, Color.TRANSPARENT)
        ));
        gc.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

        gc.setStroke(Color.color(1.0, 1.0, 1.0, 0.025));
        gc.setLineWidth(1.0);
        for (int i = -20; i < 26; i++) {
            double y = i * 22 + ((t * 8.0) % 22.0);
            gc.strokeLine(0, y, WINDOW_WIDTH, y + 28);
        }
    }

    private void drawTable(GraphicsContext gc, double t) {
        double x = state.tableX();
        double y = state.tableY();
        double w = state.tableWidth();
        double h = state.tableHeight();

        gc.setFill(Color.color(0, 0, 0, 0.42));
        gc.fillRoundRect(x - 28, y - 18, w + 56, h + 52, 42, 42);

        gc.setFill(new LinearGradient(
                0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#7e4d2a")),
                new Stop(0.45, Color.web("#5f381f")),
                new Stop(1.0, Color.web("#3f2514"))
        ));
        gc.fillRoundRect(x - 20, y - 20, w + 40, h + 40, 34, 34);

        gc.setStroke(Color.color(1.0, 1.0, 1.0, 0.20));
        gc.setLineWidth(2.0);
        gc.strokeRoundRect(x - 19, y - 19, w + 38, h + 38, 32, 32);

        gc.setFill(new LinearGradient(
                0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#1f8a5b")),
                new Stop(0.60, Color.web("#11683f")),
                new Stop(1.0, Color.web("#0f5434"))
        ));
        gc.fillRoundRect(x, y, w, h, 18, 18);

        drawFeltMicroTexture(gc, x, y, w, h, t);
        drawRailNormalShading(gc, x, y, w, h);

        gc.setFill(new RadialGradient(
                0, 0, x + w * 0.45 + Math.sin(t * 0.3) * 18.0, y + h * 0.30, w * 0.7, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(1.0, 1.0, 1.0, 0.10)),
                new Stop(1.0, Color.TRANSPARENT)
        ));
        gc.fillRoundRect(x, y, w, h, 18, 18);

        gc.setStroke(Color.color(1.0, 1.0, 1.0, 0.05));
        gc.setLineWidth(1.0);
        for (double gy = y + 18; gy < y + h; gy += 22) {
            gc.strokeLine(x + 10, gy, x + w - 10, gy);
        }

        gc.setStroke(Color.color(0, 0, 0, 0.28));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(x, y, w, h, 18, 18);

        gc.setFill(Color.color(0, 0, 0, 0.33));
        for (Vector2 pocket : state.pockets()) {
            gc.fillOval(pocket.x() - POCKET_RADIUS - 4, pocket.y() - POCKET_RADIUS - 2, (POCKET_RADIUS + 4) * 2, (POCKET_RADIUS + 4) * 2);
        }

        for (Vector2 pocket : state.pockets()) {
            gc.setFill(new RadialGradient(
                    0, 0, pocket.x(), pocket.y() - 2, POCKET_RADIUS + 2, false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.web("#0f0f0f")),
                    new Stop(0.75, Color.web("#050505")),
                    new Stop(1.0, Color.web("#2c1b10"))
            ));
            gc.fillOval(pocket.x() - POCKET_RADIUS, pocket.y() - POCKET_RADIUS, POCKET_RADIUS * 2, POCKET_RADIUS * 2);

            gc.setStroke(Color.color(1.0, 0.82, 0.58, 0.25));
            gc.setLineWidth(1.2);
            gc.strokeOval(pocket.x() - POCKET_RADIUS, pocket.y() - POCKET_RADIUS, POCKET_RADIUS * 2, POCKET_RADIUS * 2);
        }
    }

    private void drawFeltMicroTexture(GraphicsContext gc, double x, double y, double w, double h, double t) {
        gc.setStroke(Color.color(0.04, 0.14, 0.09, 0.08));
        gc.setLineWidth(1.0);
        for (int i = 0; i < 165; i++) {
            double yPos = y + ((i * 2.6 + (t * 3.0)) % h);
            double wobble = Math.sin(i * 0.71 + t * 0.42) * 4.0;
            gc.strokeLine(x + 6 + wobble, yPos, x + w - 6 + wobble * 0.25, yPos + 1.2);
        }

        gc.setStroke(Color.color(0.70, 1.0, 0.84, 0.03));
        gc.setLineWidth(1.0);
        for (int i = 0; i < 90; i++) {
            double xPos = x + ((i * 9.0 + t * 1.9) % w);
            gc.strokeLine(xPos, y + 8, xPos + 2.4, y + h - 8);
        }
    }

    private void drawRailNormalShading(GraphicsContext gc, double x, double y, double w, double h) {
        gc.setFill(new LinearGradient(
                0, y, 0, y + 18, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(1, 1, 1, 0.20)),
                new Stop(1.0, Color.TRANSPARENT)
        ));
        gc.fillRoundRect(x, y, w, 18, 16, 16);

        gc.setFill(new LinearGradient(
                0, y + h - 18, 0, y + h, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.TRANSPARENT),
                new Stop(1.0, Color.color(0, 0, 0, 0.22))
        ));
        gc.fillRoundRect(x, y + h - 18, w, 18, 16, 16);

        gc.setFill(new LinearGradient(
                x, 0, x + 16, 0, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(1, 1, 1, 0.14)),
                new Stop(1.0, Color.TRANSPARENT)
        ));
        gc.fillRoundRect(x, y, 16, h, 16, 16);

        gc.setFill(new LinearGradient(
                x + w - 16, 0, x + w, 0, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.TRANSPARENT),
                new Stop(1.0, Color.color(0, 0, 0, 0.16))
        ));
        gc.fillRoundRect(x + w - 16, y, 16, h, 16, 16);
    }

    private void drawAimGuide(GraphicsContext gc) {
        if (!world.allBallsNearlyStopped()) {
            return;
        }

        Ball cueBall = world.cueBall();
        double chargeRatio = Math.min(1.0, lastChargeSeconds / PhysicsConstants.CHARGE_TIME_TO_MAX);
        Vector2 origin = cueBall.position();
        Vector2 toMouse = mousePosition.sub(origin);
        if (toMouse.length() < 1e-9) {
            return;
        }
        Vector2 direction = toMouse.normalized();
        Prediction prediction = predictFirstCollision(cueBall, direction, world.balls());
        Vector2 endPoint = prediction.hitPoint != null
                ? prediction.hitPoint
                : origin.add(direction.mul(PREDICTION_MAX_DISTANCE));

        gc.setLineDashes(8, 8);
        gc.setLineWidth(2.0 + chargeRatio * 2.5);
        gc.setStroke(Color.color(0.95, 0.85, 0.40, 0.50 + chargeRatio * 0.35));
        gc.strokeLine(origin.x(), origin.y(), endPoint.x(), endPoint.y());
        gc.setLineDashes(null);

        if (prediction.type == HitType.RAIL && prediction.reflectionDirection != null) {
            Vector2 start = prediction.hitPoint.add(prediction.reflectionDirection.mul(0.5));
            double maxT = distanceToRail(start, prediction.reflectionDirection, cueBall.radius());
            double segment = Math.min(POST_COLLISION_PREVIEW_DISTANCE, maxT);
            Vector2 after = start.add(prediction.reflectionDirection.mul(segment));
            drawPostCollisionSegment(gc, start, after, Color.color(0.98, 0.93, 0.65, 0.60), Color.color(1.0, 0.97, 0.78, 0.22));
        } else if (prediction.type == HitType.BALL && prediction.objectBallDirection != null) {
            Vector2 objectStart = prediction.hitPoint.add(prediction.objectBallDirection.mul(0.5));
            double objectMaxT = distanceToRail(objectStart, prediction.objectBallDirection, cueBall.radius());
            Vector2 objectAfter = objectStart.add(prediction.objectBallDirection.mul(Math.min(POST_COLLISION_PREVIEW_DISTANCE * 0.22, objectMaxT)));
            drawPostCollisionSegment(gc, objectStart, objectAfter, Color.color(0.73, 0.90, 1.0, 0.78), Color.color(0.86, 0.96, 1.0, 0.24));

            if (prediction.cueDeflectDirection != null && prediction.cueDeflectDirection.length() > 1e-6) {
                Vector2 cueStart = prediction.hitPoint.add(prediction.cueDeflectDirection.mul(0.5));
                double cueMaxT = distanceToRail(cueStart, prediction.cueDeflectDirection, cueBall.radius());
                Vector2 cueAfter = cueStart.add(prediction.cueDeflectDirection.mul(Math.min(POST_COLLISION_PREVIEW_DISTANCE * 0.75, cueMaxT)));
                drawPostCollisionSegment(gc, cueStart, cueAfter, Color.color(1.0, 0.85, 0.58, 0.48), Color.color(1.0, 0.92, 0.72, 0.18));
            }
        }

        if (chargingShot) {
            Vector2 pull = cueBall.position().sub(mousePosition);
            Vector2 dir = pull.normalized();
            double stickLength = 120 + chargeRatio * 60;
            double startGap = cueBall.radius() + 8 + chargeRatio * 12;

            double sx = cueBall.position().x() + dir.x() * startGap;
            double sy = cueBall.position().y() + dir.y() * startGap;
            double ex = sx + dir.x() * stickLength;
            double ey = sy + dir.y() * stickLength;

            gc.setStroke(new LinearGradient(
                    sx, sy, ex, ey, false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.color(0.90, 0.82, 0.62, 0.9)),
                    new Stop(1.0, Color.color(0.55, 0.38, 0.21, 0.95))
            ));
            gc.setLineWidth(5.0);
            gc.strokeLine(sx, sy, ex, ey);
        }

        drawTipMarker(gc, cueBall);
    }

    private void drawTipMarker(GraphicsContext gc, Ball cueBall) {
        if (!world.allBallsNearlyStopped()) {
            return;
        }
        double markerX = cueBall.position().x() + tipOffsetNorm.x() * cueBall.radius();
        double markerY = cueBall.position().y() - tipOffsetNorm.y() * cueBall.radius();
        double r = Math.max(3.0, cueBall.radius() * 0.22);

        gc.setFill(Color.color(0.96, 0.96, 0.96, 0.92));
        gc.fillOval(markerX - r, markerY - r, r * 2, r * 2);
        gc.setStroke(Color.color(0.12, 0.12, 0.12, 0.75));
        gc.setLineWidth(1.2);
        gc.strokeOval(markerX - r, markerY - r, r * 2, r * 2);
    }

    private void drawPostCollisionSegment(GraphicsContext gc, Vector2 start, Vector2 end, Color coreColor, Color glowColor) {
        gc.setStroke(glowColor);
        gc.setLineWidth(6.0);
        gc.strokeLine(start.x(), start.y(), end.x(), end.y());

        gc.setLineDashes(5, 7);
        gc.setStroke(coreColor);
        gc.setLineWidth(2.2);
        gc.strokeLine(start.x(), start.y(), end.x(), end.y());
        gc.setLineDashes(null);
    }

    private double distanceToRail(Vector2 origin, Vector2 dir, double radius) {
        double left = state.tableX() + radius;
        double right = state.tableX() + state.tableWidth() - radius;
        double top = state.tableY() + radius;
        double bottom = state.tableY() + state.tableHeight() - radius;

        double bestT = Double.POSITIVE_INFINITY;
        if (dir.x() > 1e-9) {
            bestT = Math.min(bestT, (right - origin.x()) / dir.x());
        } else if (dir.x() < -1e-9) {
            bestT = Math.min(bestT, (left - origin.x()) / dir.x());
        }
        if (dir.y() > 1e-9) {
            bestT = Math.min(bestT, (bottom - origin.y()) / dir.y());
        } else if (dir.y() < -1e-9) {
            bestT = Math.min(bestT, (top - origin.y()) / dir.y());
        }
        if (bestT <= 0.0 || !Double.isFinite(bestT)) {
            return POST_COLLISION_PREVIEW_DISTANCE;
        }
        return bestT;
    }

    private Prediction predictFirstCollision(Ball cueBall, Vector2 direction, List<Ball> allBalls) {
        Vector2 origin = cueBall.position();
        double cueRadius = cueBall.radius();

        double left = state.tableX() + cueRadius;
        double right = state.tableX() + state.tableWidth() - cueRadius;
        double top = state.tableY() + cueRadius;
        double bottom = state.tableY() + state.tableHeight() - cueRadius;

        double bestT = Double.POSITIVE_INFINITY;
        HitType type = HitType.NONE;
        Vector2 hitPoint = null;
        Vector2 normal = null;

        if (direction.x() > 1e-9) {
            double t = (right - origin.x()) / direction.x();
            if (t > 1e-6 && t < bestT) {
                bestT = t;
                type = HitType.RAIL;
                hitPoint = origin.add(direction.mul(t));
                normal = new Vector2(-1, 0);
            }
        } else if (direction.x() < -1e-9) {
            double t = (left - origin.x()) / direction.x();
            if (t > 1e-6 && t < bestT) {
                bestT = t;
                type = HitType.RAIL;
                hitPoint = origin.add(direction.mul(t));
                normal = new Vector2(1, 0);
            }
        }

        if (direction.y() > 1e-9) {
            double t = (bottom - origin.y()) / direction.y();
            if (t > 1e-6 && t < bestT) {
                bestT = t;
                type = HitType.RAIL;
                hitPoint = origin.add(direction.mul(t));
                normal = new Vector2(0, -1);
            }
        } else if (direction.y() < -1e-9) {
            double t = (top - origin.y()) / direction.y();
            if (t > 1e-6 && t < bestT) {
                bestT = t;
                type = HitType.RAIL;
                hitPoint = origin.add(direction.mul(t));
                normal = new Vector2(0, 1);
            }
        }

        for (Ball ball : allBalls) {
            if (ball == cueBall) {
                continue;
            }
            Vector2 oc = origin.sub(ball.position());
            double sumR = cueRadius + ball.radius();
            double b = 2.0 * ((direction.x() * oc.x()) + (direction.y() * oc.y()));
            double c = oc.lengthSq() - (sumR * sumR);
            double disc = (b * b) - (4.0 * c);
            if (disc < 0.0) {
                continue;
            }

            double sqrtDisc = Math.sqrt(disc);
            double t = (-b - sqrtDisc) * 0.5;
            if (t <= 1e-6 || t >= bestT) {
                continue;
            }

            bestT = t;
            type = HitType.BALL;
            hitPoint = origin.add(direction.mul(t));
            normal = ball.position().sub(hitPoint).normalized();
        }

        if (type == HitType.NONE || hitPoint == null) {
            return new Prediction(HitType.NONE, null, null, null, null);
        }

        if (type == HitType.RAIL) {
            double dn = (direction.x() * normal.x()) + (direction.y() * normal.y());
            Vector2 reflection = direction.sub(normal.mul(2.0 * dn)).normalized();
            return new Prediction(type, hitPoint, reflection, null, null);
        }

        Vector2 n = normal != null ? normal : Vector2.ZERO;
        double dn = (direction.x() * n.x()) + (direction.y() * n.y());
        Vector2 objectDirection = n.normalized();
        Vector2 cueDeflect = direction.sub(n.mul(dn)).normalized();
        if (cueDeflect.length() < 1e-6) {
            cueDeflect = new Vector2(-n.y(), n.x()).normalized();
        }
        return new Prediction(type, hitPoint, null, objectDirection, cueDeflect);
    }

    private void drawBalls(GraphicsContext gc) {
        List<Ball> balls = world.balls();
        for (int i = 0; i < balls.size(); i++) {
            Ball ball = balls.get(i);
            drawBall(gc, ball, i);
            drawSpinMarkers(gc, ball, i);
            if (showSpinDebug) {
                double wz = world.ballAngularVelocity(i).z();
                gc.setFill(Color.color(0.90, 0.95, 1.0, 0.92));
                gc.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
                gc.fillText(String.format("wz %.2f", wz), ball.position().x() + ball.radius() + 5, ball.position().y() - ball.radius() - 3);
            }
        }

        Vector2 cueVel = world.ballVelocity(0);
        double speed = cueVel.length();
        if (speed > 20) {
            Ball cueBall = world.cueBall();
            Vector2 dir = cueVel.normalized();
            double trail = Math.min(80, speed * 0.08);

            gc.setStroke(new LinearGradient(
                    cueBall.position().x(), cueBall.position().y(),
                    cueBall.position().x() - dir.x() * trail, cueBall.position().y() - dir.y() * trail,
                    false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.color(1, 1, 1, 0.35)),
                    new Stop(1.0, Color.color(1, 1, 1, 0.0))
            ));
            gc.setLineWidth(5.0);
            gc.strokeLine(
                    cueBall.position().x(), cueBall.position().y(),
                    cueBall.position().x() - dir.x() * trail, cueBall.position().y() - dir.y() * trail
            );
        }
    }

    private void drawSpinMarkers(GraphicsContext gc, Ball ball, int index) {
        if (!showSpinMarkers) {
            return;
        }

        Vector3 p1 = world.ballOrientation(index).rotate(MARKER_LOCAL_1);
        Vector3 p2 = world.ballOrientation(index).rotate(MARKER_LOCAL_2);
        drawSpinMarker(gc, ball, p1, Color.color(0.08, 0.09, 0.10, 0.95));
        drawSpinMarker(gc, ball, p2, Color.color(0.93, 0.95, 0.98, 0.95));
    }

    private void drawSpinMarker(GraphicsContext gc, Ball ball, Vector3 p, Color color) {
        if (p.z() <= 0.0) {
            return;
        }
        double screenX = ball.position().x() + (p.x() * PIXELS_PER_METER);
        double screenY = ball.position().y() - (p.y() * PIXELS_PER_METER);
        double zNorm = clamp01(p.z() / PhysicsConfig.BALL_RADIUS_M);
        double zScale = 0.5 + 0.5 * zNorm;
        double r = Math.max(1.4, ball.radius() * 0.12 * zScale);
        double alpha = 0.20 + 0.80 * zNorm;
        Color fill = Color.color(color.getRed(), color.getGreen(), color.getBlue(), color.getOpacity() * alpha);

        gc.setFill(fill);
        gc.fillOval(screenX - r, screenY - r, r * 2, r * 2);
        gc.setStroke(Color.color(0, 0, 0, 0.45));
        gc.setLineWidth(0.8);
        gc.strokeOval(screenX - r, screenY - r, r * 2, r * 2);
    }

    private void drawBall(GraphicsContext gc, Ball ball, int index) {
        double x = ball.position().x();
        double y = ball.position().y();
        double r = ball.radius();
        Color midColor = index == 0 ? Color.web("#f4f4f4") : Color.web("#d84d4d");
        Color edgeColor = index == 0 ? Color.web("#cfcfcf") : Color.web("#8b2323");
        double lightX = LIGHT_DIR_SCREEN.x();
        double lightY = LIGHT_DIR_SCREEN.y();

        if (showBallShading) {
            double shadowOffsetX = -lightX * r * 0.24;
            double shadowOffsetY = -lightY * r * 0.24;
            gc.setFill(new RadialGradient(
                    0, 0,
                    x + shadowOffsetX, y + shadowOffsetY + r * 0.72,
                    r * 1.65,
                    false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.color(0, 0, 0, 0.34)),
                    new Stop(1.0, Color.TRANSPARENT)
            ));
            gc.fillOval(x - r * 1.55, y + r * 0.1, r * 3.1, r * 1.35);

            gc.setFill(new RadialGradient(
                    0, 0,
                    x + lightX * r * 0.48, y + lightY * r * 0.48,
                    r * (1.22 + (1.0 - AMBIENT) * 0.36),
                    false,
                    CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.WHITE.interpolate(midColor, 1.0 - DIFFUSE)),
                    new Stop(0.62, midColor),
                    new Stop(1.0, edgeColor.interpolate(Color.BLACK, 1.0 - AMBIENT))
            ));
            gc.fillOval(x - r, y - r, r * 2, r * 2);

            gc.setFill(new RadialGradient(
                    0, 0,
                    x - lightX * r * 0.58, y - lightY * r * 0.58,
                    r * 1.22,
                    false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.TRANSPARENT),
                    new Stop(1.0, Color.color(0, 0, 0, 0.24 + 0.11 * (1.0 - AMBIENT)))
            ));
            gc.fillOval(x - r, y - r, r * 2, r * 2);
        } else {
            gc.setFill(midColor);
            gc.fillOval(x - r, y - r, r * 2, r * 2);
        }

        if (index > 0) {
            gc.setFill(Color.color(0.97, 0.97, 0.97, 0.92));
            gc.fillOval(x - r * 0.65, y - r * 0.32, r * 1.3, r * 0.64);
            gc.setFill(Color.color(0.16, 0.16, 0.16, 0.60));
            gc.setFont(Font.font("Georgia", FontWeight.BOLD, r * 0.75));
            gc.fillText(String.valueOf(index + 2), x - r * 0.22, y + r * 0.22);
        }

        gc.setStroke(Color.color(1.0, 1.0, 1.0, 0.18));
        gc.setLineWidth(1.1);
        gc.strokeOval(x - r * 0.97, y - r * 0.97, r * 1.94, r * 1.94);

        gc.setStroke(Color.color(1.0, 1.0, 1.0, 0.45));
        gc.setLineWidth(1.0);
        gc.strokeOval(x - r, y - r, r * 2, r * 2);

        if (showBallShading) {
            double specX = x + lightX * r * 0.45;
            double specY = y + lightY * r * 0.45;
            double specR = r * SPECULAR_SIZE_RATIO;
            gc.setFill(new RadialGradient(
                    0, 0, specX, specY, specR, false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.color(1, 1, 1, 0.98 * SPECULAR)),
                    new Stop(0.45, Color.color(1, 1, 1, 0.52 * SPECULAR)),
                    new Stop(1.0, Color.TRANSPARENT)
            ));
            gc.fillOval(specX - specR, specY - specR, specR * 2, specR * 2);
        }
    }

    private void drawBloomPass(GraphicsContext gc, double t) {
        gc.save();
        gc.setGlobalBlendMode(BlendMode.SCREEN);

        for (Vector2 pocket : state.pockets()) {
            gc.setFill(new RadialGradient(
                    0, 0, pocket.x(), pocket.y(), POCKET_RADIUS * 2.8, false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.color(0.95, 0.70, 0.34, 0.10)),
                    new Stop(1.0, Color.TRANSPARENT)
            ));
            gc.fillOval(pocket.x() - POCKET_RADIUS * 2.8, pocket.y() - POCKET_RADIUS * 2.8, POCKET_RADIUS * 5.6, POCKET_RADIUS * 5.6);
        }

        List<Ball> balls = world.balls();
        for (int i = 0; i < balls.size(); i++) {
            Ball ball = balls.get(i);
            double r = ball.radius();
            Color bloom = i == 0
                    ? Color.color(0.88, 0.92, 1.0, 0.20)
                    : Color.color(1.0, 0.54, 0.45, 0.16);
            gc.setFill(new RadialGradient(
                    0, 0, ball.position().x(), ball.position().y(), r * 2.6, false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, bloom),
                    new Stop(1.0, Color.TRANSPARENT)
            ));
            gc.fillOval(ball.position().x() - r * 2.6, ball.position().y() - r * 2.6, r * 5.2, r * 5.2);
        }

        if (chargingShot) {
            Ball cueBall = world.cueBall();
            double pulse = 0.5 + 0.5 * Math.sin(t * 8.0);
            double radius = cueBall.radius() * (3.8 + pulse * 1.2);
            gc.setFill(new RadialGradient(
                    0, 0, cueBall.position().x(), cueBall.position().y(), radius, false, CycleMethod.NO_CYCLE,
                    new Stop(0.0, Color.color(1.0, 0.86, 0.44, 0.24)),
                    new Stop(1.0, Color.TRANSPARENT)
            ));
            gc.fillOval(cueBall.position().x() - radius, cueBall.position().y() - radius, radius * 2, radius * 2);
        }

        gc.restore();
    }

    private void drawHud(GraphicsContext gc, double fps) {
        double cueSpeed = world.cueBallSpeed();
        String cueMode = world.cueBallMotionMode() == PhysicsWorld.MotionMode.SLIDING ? "SLIDE" : "ROLL";
        double muR = world.rollingFriction();
        double rollAccel = world.rollingDecelMps2();
        double wMag = world.ballAngularVelocity(0).length();
        double wz = world.ballAngularVelocity(0).z();

        gc.setFill(Color.color(0.02, 0.06, 0.08, 0.73));
        gc.fillRoundRect(14, 14, 320, 152, 14, 14);
        gc.setStroke(Color.color(0.64, 0.86, 0.78, 0.35));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(14, 14, 320, 152, 14, 14);

        gc.setFill(Color.web("#d7efe6"));
        gc.setFont(Font.font("Georgia", FontWeight.BOLD, 15));
        gc.fillText(String.format("FPS %.1f", fps), 26, 36);

        gc.setFont(Font.font("Georgia", 14));
        gc.setFill(Color.web("#b6d8cc"));
        gc.fillText("State " + simulatorState, 26, 56);
        gc.fillText(String.format("Cue %s  %.1f px/s", cueMode, cueSpeed), 26, 76);
        gc.fillText(String.format("mu_r %.3f   a_roll %.3f m/s^2", muR, rollAccel), 26, 96);
        gc.fillText(String.format("mu_rail %.3f   e_rail %.2f", PhysicsConfig.MU_RAIL, PhysicsConfig.RAIL_RESTITUTION), 26, 116);
        gc.fillText(String.format("tip (%.2f, %.2f)", tipOffsetNorm.x(), tipOffsetNorm.y()), 26, 136);
        gc.fillText(String.format("|w| %.2f   wz %.2f", wMag, wz), 26, 156);

        double chargeRatio = Math.min(1.0, lastChargeSeconds / PhysicsConstants.CHARGE_TIME_TO_MAX);
        gc.setFill(Color.color(0.01, 0.03, 0.04, 0.78));
        gc.fillRoundRect(14, 184, 302, 34, 10, 10);
        gc.setFill(new LinearGradient(
                20, 0, 300, 0, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#f1cc58")),
                new Stop(0.7, Color.web("#ff9444")),
                new Stop(1.0, Color.web("#ff5e45"))
        ));
        gc.fillRoundRect(20, 190, 290 * chargeRatio, 22, 8, 8);

        gc.setFill(Color.web("#f7f7f7"));
        gc.setFont(Font.font("Georgia", FontWeight.BOLD, 13));
        gc.fillText(String.format("SHOT POWER %d%%", (int) Math.round(chargeRatio * 100)), 24, 206);

        gc.setFill(new RadialGradient(
                0, 0, WINDOW_WIDTH * 0.5, WINDOW_HEIGHT * 0.48, WINDOW_WIDTH * 0.68,
                false, CycleMethod.NO_CYCLE,
                new Stop(0.70, Color.TRANSPARENT),
                new Stop(1.0, Color.color(0, 0, 0, 0.46))
        ));
        gc.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void cycleTipPreset() {
        tipPresetIndex = (tipPresetIndex + 1) % 5;
        switch (tipPresetIndex) {
            case 0 -> tipOffsetNorm = Vector2.ZERO;
            case 1 -> tipOffsetNorm = new Vector2(0, 0.50);
            case 2 -> tipOffsetNorm = new Vector2(0, -0.50);
            case 3 -> tipOffsetNorm = new Vector2(-0.50, 0);
            case 4 -> tipOffsetNorm = new Vector2(0.50, 0);
            default -> tipOffsetNorm = Vector2.ZERO;
        }
    }

    private void adjustTipOffset(double dxPx, double dyPx) {
        Ball cueBall = world.cueBall();
        if (cueBall.radius() <= 1e-6) {
            return;
        }
        double nx = dxPx / cueBall.radius();
        double ny = -dyPx / cueBall.radius();
        tipOffsetNorm = clampTipOffset(new Vector2(nx, ny));
    }

    private Vector2 clampTipOffset(Vector2 offset) {
        double len = offset.length();
        if (len <= TIP_OFFSET_MAX) {
            return offset;
        }
        return offset.normalized().mul(TIP_OFFSET_MAX);
    }

    private double cueBallScreenX() {
        return world.cueBall().position().x();
    }

    private double cueBallScreenY() {
        return world.cueBall().position().y();
    }

    private static final class Prediction {
        private final HitType type;
        private final Vector2 hitPoint;
        private final Vector2 reflectionDirection;
        private final Vector2 objectBallDirection;
        private final Vector2 cueDeflectDirection;

        private Prediction(HitType type, Vector2 hitPoint, Vector2 reflectionDirection, Vector2 objectBallDirection, Vector2 cueDeflectDirection) {
            this.type = type;
            this.hitPoint = hitPoint;
            this.reflectionDirection = reflectionDirection;
            this.objectBallDirection = objectBallDirection;
            this.cueDeflectDirection = cueDeflectDirection;
        }
    }

    private static Vector3 unit(double x, double y, double z) {
        double len = Math.sqrt((x * x) + (y * y) + (z * z));
        if (len <= 1e-12) {
            return Vector3.ZERO;
        }
        return new Vector3(x / len, y / len, z / len);
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        return Math.min(1.0, value);
    }
}
