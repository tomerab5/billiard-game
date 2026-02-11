package com.billiardgame.ui;

import com.billiardgame.game.Ball;
import com.billiardgame.game.TableState;
import com.billiardgame.physics.PhysicsConstants;
import com.billiardgame.physics.PhysicsWorld;
import com.billiardgame.physics.TableBounds;
import com.billiardgame.physics.Vector2;
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

    private static final double FIXED_DT_SECONDS = 1.0 / 120.0;

    private enum SimulatorState {
        AIMING,
        MOVING
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
            new Ball(new Vector2(TABLE_X + TABLE_WIDTH * 0.33, TABLE_Y + TABLE_HEIGHT * 0.5), 12)
    );

    private PhysicsWorld world = createInitialWorld();

    private Vector2 mousePosition = state.cueBall().position();
    private SimulatorState simulatorState = SimulatorState.AIMING;
    private boolean chargingShot = false;
    private long shotChargeStartNanos = 0L;
    private double lastChargeSeconds = 0.0;

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(WINDOW_WIDTH, WINDOW_HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        canvas.setOnMouseMoved(event -> mousePosition = new Vector2(event.getX(), event.getY()));
        canvas.setOnMouseDragged(event -> mousePosition = new Vector2(event.getX(), event.getY()));
        canvas.setOnMousePressed(event -> {
            mousePosition = new Vector2(event.getX(), event.getY());
            if (event.getButton() == MouseButton.PRIMARY && world.allBallsNearlyStopped()) {
                chargingShot = true;
                shotChargeStartNanos = System.nanoTime();
                lastChargeSeconds = 0.0;
            }
        });
        canvas.setOnMouseReleased(event -> {
            mousePosition = new Vector2(event.getX(), event.getY());
            if (event.getButton() == MouseButton.PRIMARY) {
                releaseShot();
            }
        });

        Scene scene = new Scene(new StackPane(canvas), WINDOW_WIDTH, WINDOW_HEIGHT, Color.web("#101518"));
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.R) {
                resetWorld();
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
        return new PhysicsWorld(List.of(cueBall, targetBall), tableBounds);
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
        world.setCueBallVelocity(direction.mul(speed));
        lastChargeSeconds = 0.0;
    }

    private void resetWorld() {
        world = createInitialWorld();
        simulatorState = SimulatorState.AIMING;
        chargingShot = false;
        lastChargeSeconds = 0.0;
    }

    private void render(GraphicsContext gc, double fps, double timeSeconds) {
        drawRoomBackground(gc, timeSeconds);
        drawTable(gc, timeSeconds);
        drawAimGuide(gc);
        drawBalls(gc);
        drawBloomPass(gc, timeSeconds);
        drawHud(gc, fps);
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
        Ball cueBall = world.cueBall();
        double chargeRatio = Math.min(1.0, lastChargeSeconds / PhysicsConstants.CHARGE_TIME_TO_MAX);

        gc.setLineDashes(8, 8);
        gc.setLineWidth(2.0 + chargeRatio * 2.5);
        gc.setStroke(Color.color(0.95, 0.85, 0.40, 0.50 + chargeRatio * 0.35));
        gc.strokeLine(cueBall.position().x(), cueBall.position().y(), mousePosition.x(), mousePosition.y());
        gc.setLineDashes(null);

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
    }

    private void drawBalls(GraphicsContext gc) {
        List<Ball> balls = world.balls();
        for (int i = 0; i < balls.size(); i++) {
            Ball ball = balls.get(i);
            drawBall(gc, ball, i);
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

    private void drawBall(GraphicsContext gc, Ball ball, int index) {
        double x = ball.position().x();
        double y = ball.position().y();
        double r = ball.radius();

        gc.setFill(Color.color(0, 0, 0, 0.35));
        gc.fillOval(x - r + 2.5, y - r + 4, r * 2, r * 2);

        Color midColor = index == 0 ? Color.web("#f4f4f4") : Color.web("#d84d4d");
        Color edgeColor = index == 0 ? Color.web("#cfcfcf") : Color.web("#8b2323");

        gc.setFill(new RadialGradient(
                0, 0,
                x - r * 0.35, y - r * 0.40,
                r * 1.45,
                false,
                CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.WHITE),
                new Stop(0.45, midColor),
                new Stop(1.0, edgeColor)
        ));
        gc.fillOval(x - r, y - r, r * 2, r * 2);

        gc.setFill(new RadialGradient(
                0, 0,
                x + r * 0.35, y + r * 0.40,
                r * 1.25,
                false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.TRANSPARENT),
                new Stop(1.0, Color.color(0, 0, 0, 0.25))
        ));
        gc.fillOval(x - r, y - r, r * 2, r * 2);

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

        gc.setFill(Color.color(1, 1, 1, 0.75));
        gc.fillOval(x - r * 0.45, y - r * 0.45, r * 0.45, r * 0.35);

        gc.setFill(Color.color(1, 1, 1, 0.24));
        gc.fillOval(x - r * 0.15, y - r * 0.65, r * 0.25, r * 0.14);
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
        String cueMode = cueSpeed > PhysicsConstants.ROLLING_THRESHOLD_PX_PER_S ? "SLIDE" : "ROLL";

        gc.setFill(Color.color(0.02, 0.06, 0.08, 0.73));
        gc.fillRoundRect(14, 14, 302, 86, 14, 14);
        gc.setStroke(Color.color(0.64, 0.86, 0.78, 0.35));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(14, 14, 302, 86, 14, 14);

        gc.setFill(Color.web("#d7efe6"));
        gc.setFont(Font.font("Georgia", FontWeight.BOLD, 15));
        gc.fillText(String.format("FPS %.1f", fps), 26, 36);

        gc.setFont(Font.font("Georgia", 14));
        gc.setFill(Color.web("#b6d8cc"));
        gc.fillText("State " + simulatorState, 26, 56);
        gc.fillText(String.format("Cue %s  %.1f px/s", cueMode, cueSpeed), 26, 76);

        double chargeRatio = Math.min(1.0, lastChargeSeconds / PhysicsConstants.CHARGE_TIME_TO_MAX);
        gc.setFill(Color.color(0.01, 0.03, 0.04, 0.78));
        gc.fillRoundRect(14, 104, 302, 34, 10, 10);
        gc.setFill(new LinearGradient(
                20, 0, 300, 0, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#f1cc58")),
                new Stop(0.7, Color.web("#ff9444")),
                new Stop(1.0, Color.web("#ff5e45"))
        ));
        gc.fillRoundRect(20, 110, 290 * chargeRatio, 22, 8, 8);

        gc.setFill(Color.web("#f7f7f7"));
        gc.setFont(Font.font("Georgia", FontWeight.BOLD, 13));
        gc.fillText(String.format("SHOT POWER %d%%", (int) Math.round(chargeRatio * 100)), 24, 126);

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
}
