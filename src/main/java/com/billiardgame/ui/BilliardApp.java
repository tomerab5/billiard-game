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

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(WINDOW_WIDTH, WINDOW_HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        Scene scene = new Scene(new StackPane(canvas), WINDOW_WIDTH, WINDOW_HEIGHT, Color.web("#1a1a1a"));

        stage.setTitle("2D Billiards Simulator");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                draw(gc, state);
            }
        };
        timer.start();
    }

    private void draw(GraphicsContext gc, TableState state) {
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
        gc.setFill(Color.WHITE);
        gc.fillOval(cueBall.position().x() - cueBall.radius(), cueBall.position().y() - cueBall.radius(), cueBall.radius() * 2, cueBall.radius() * 2);
    }

    public static void main(String[] args) {
        launch(args);
    }
}