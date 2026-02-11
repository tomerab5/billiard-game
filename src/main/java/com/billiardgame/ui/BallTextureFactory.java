package com.billiardgame.ui;

import javafx.geometry.VPos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.Random;

final class BallTextureFactory {
    private BallTextureFactory() {
    }

    static WritableImage createDiffuseTexture(int ballId, int size) {
        if (ballId <= 0) {
            return createCueTexture(size, 1337L);
        }
        int number = ballId + 2;
        boolean striped = number >= 9;
        Color base = baseColor(number);
        return createNumberedTexture(size, base, number, striped, 7000L + ballId * 97L);
    }

    private static WritableImage createCueTexture(int size, long seed) {
        WritableImage image = new WritableImage(size, size);
        PixelWriter pw = image.getPixelWriter();
        Random random = new Random(seed);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double speckle = (random.nextDouble() - 0.5) * 0.025;
                double shade = 0.935 + speckle;
                pw.setColor(x, y, clampGray(shade));
            }
        }
        return image;
    }

    private static WritableImage createNumberedTexture(int size, Color base, int number, boolean striped, long seed) {
        WritableImage image = new WritableImage(size, size);
        PixelWriter pw = image.getPixelWriter();
        Random random = new Random(seed);

        int centerY = size / 2;
        double stripeHalf = size * 0.15;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double noise = (random.nextDouble() - 0.5) * 0.03;
                Color color = base.deriveColor(0.0, 1.0, 1.0 + noise, 1.0);
                if (striped && Math.abs(y - centerY) <= stripeHalf) {
                    color = Color.color(0.97 + noise * 0.2, 0.97 + noise * 0.2, 0.97 + noise * 0.2);
                }
                pw.setColor(x, y, clampColor(color));
            }
        }

        double cx = size * 0.5;
        double cy = size * 0.5;
        double circleR = size * 0.12;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double dx = x - cx;
                double dy = y - cy;
                if ((dx * dx) + (dy * dy) <= circleR * circleR) {
                    pw.setColor(x, y, Color.WHITE);
                }
            }
        }

        drawNumber(image, number);
        return image;
    }

    private static void drawNumber(WritableImage image, int number) {
        int size = (int) image.getWidth();
        Canvas textLayer = new Canvas(size, size);
        GraphicsContext gc = textLayer.getGraphicsContext2D();
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFont(Font.font("Georgia", FontWeight.BOLD, size * 0.12));
        gc.setFill(Color.BLACK);
        gc.fillText(Integer.toString(number), size * 0.5, size * 0.5);

        WritableImage textImage = new WritableImage(size, size);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        textLayer.snapshot(params, textImage);

        PixelReader pr = textImage.getPixelReader();
        PixelWriter pw = image.getPixelWriter();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                Color sample = pr.getColor(x, y);
                if (sample.getOpacity() > 0.2) {
                    pw.setColor(x, y, Color.BLACK);
                }
            }
        }
    }

    private static Color baseColor(int number) {
        return switch (number) {
            case 1, 9 -> Color.web("#f0c944");
            case 2, 10 -> Color.web("#2f5ebd");
            case 3, 11 -> Color.web("#c43a2d");
            case 4, 12 -> Color.web("#7a3aa8");
            case 5, 13 -> Color.web("#df7b2a");
            case 6, 14 -> Color.web("#2f8a45");
            case 7, 15 -> Color.web("#8c2f24");
            case 8 -> Color.web("#1b1b1b");
            default -> Color.web("#c43a2d");
        };
    }

    private static Color clampGray(double v) {
        double c = Math.max(0.0, Math.min(1.0, v));
        return Color.color(c, c, c);
    }

    private static Color clampColor(Color color) {
        return Color.color(
                Math.max(0.0, Math.min(1.0, color.getRed())),
                Math.max(0.0, Math.min(1.0, color.getGreen())),
                Math.max(0.0, Math.min(1.0, color.getBlue()))
        );
    }
}
