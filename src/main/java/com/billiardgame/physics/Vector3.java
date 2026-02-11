package com.billiardgame.physics;

public final class Vector3 {
    public static final Vector3 ZERO = new Vector3(0, 0, 0);

    private final double x;
    private final double y;
    private final double z;

    public Vector3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public Vector3 add(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    public Vector3 mul(double scalar) {
        return new Vector3(x * scalar, y * scalar, z * scalar);
    }

    public double length() {
        return Math.sqrt((x * x) + (y * y) + (z * z));
    }
}
