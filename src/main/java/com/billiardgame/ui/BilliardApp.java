package com.billiardgame.ui;

import com.billiardgame.game.Ball;
import com.billiardgame.game.TableState;
import com.billiardgame.physics.Vector2;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
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
        AIMING
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

    private Vector2 mousePosition = state.cueBall().position();
    private SimulatorState simulatorState = SimulatorState.AIMING;

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(WINDOW_WIDTH, WINDOW_HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        canvas.setOnMouseMoved(event -> mousePosition = new Vector2(event.getX(), event.getY()));
        canvas.setOnMouseDragged(event -> mousePosition = new Vector2(event.getX(), event.getY()));

        Scene scene = new Scene(new StackPane(canvas), WINDOW_WIDTH, WINDOW_HEIGHT, Color.web("#1a1a1a"));

        stage.setTitle("2D Billiards Simulator");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

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

                render(gc, currentFps);
            }
        };
        timer.start();
    }

    private void update(double dtSeconds) {
        // Fixed-timestep placeholder for simulation updates.
        if (dtSeconds <= 0) {
            throw new IllegalArgumentException("dtSeconds must be positive");
        }
    }

    private void render(GraphicsContext gc, double fps) {
        gc.setFill(Color.web("#1a1a1a"));
        gc.fillRect(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

        gc.setFill(Color.web("#5f3a22"));
        gc.fillRoundRect(state.tableX() - 18, state.tableY() - 18, state.tableWidth() + 36, state.tableHeight() + 36, 30, 30);

        gc.setFill(Color.web("#177245"));
        gc.fillRoundRect(state.tableX(), state.tableY(), state.tableWidth(), state.tableHeight(), 18, 18);

        gc.setFill(Color.BLACK);
        for (Vector2 pocket : state.pockets()) {
            gc.fillOval(pocket.x() - POCKET_RADIUS, pocket.y() - POCKET_RADIUS, POCKET_RADIUS * 2, POCKET_RADIUS * 2);
        }

        Ball cueBall = state.cueBall();

        gc.setStroke(Color.web("#f5d142"));
        gc.setLineWidth(2.0);
        gc.strokeLine(cueBall.position().x(), cueBall.position().y(), mousePosition.x(), mousePosition.y());

        gc.setFill(Color.WHITE);
        gc.fillOval(cueBall.position().x() - cueBall.radius(), cueBall.position().y() - cueBall.radius(), cueBall.radius() * 2, cueBall.radius() * 2);

        gc.setFill(Color.color(0.0, 0.0, 0.0, 0.6));
        gc.fillRoundRect(12, 12, 210, 52, 10, 10);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Consolas", 14));
        gc.fillText(String.format("FPS: %.1f", fps), 22, 33);
        gc.fillText("State: " + simulatorState, 22, 52);
    }

    public static void main(String[] args) {
        launch(args);
    }
}