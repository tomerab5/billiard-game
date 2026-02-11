package com.billiardgame.physics;

public final class Vector2 {
    public static final Vector2 ZERO = new Vector2(0, 0);

    private final double x;
    private final double y;

    public Vector2(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public Vector2 add(Vector2 other) {
        return new Vector2(x + other.x, y + other.y);
    }

    public Vector2 sub(Vector2 other) {
        return new Vector2(x - other.x, y - other.y);
    }

    public Vector2 mul(double scalar) {
        return new Vector2(x * scalar, y * scalar);
    }

    public double length() {
        return Math.sqrt(lengthSq());
    }

    public double lengthSq() {
        return (x * x) + (y * y);
    }
}
