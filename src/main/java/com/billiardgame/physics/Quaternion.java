package com.billiardgame.physics;

public final class Quaternion {
    public static final Quaternion IDENTITY = new Quaternion(1, 0, 0, 0);

    private final double w;
    private final double x;
    private final double y;
    private final double z;

    public Quaternion(double w, double x, double y, double z) {
        this.w = w;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double w() {
        return w;
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

    public static Quaternion fromAxisAngle(Vector3 axisUnit, double angleRadians) {
        double half = angleRadians * 0.5;
        double s = Math.sin(half);
        return new Quaternion(
                Math.cos(half),
                axisUnit.x() * s,
                axisUnit.y() * s,
                axisUnit.z() * s
        );
    }

    public Quaternion mul(Quaternion o) {
        return new Quaternion(
                (w * o.w) - (x * o.x) - (y * o.y) - (z * o.z),
                (w * o.x) + (x * o.w) + (y * o.z) - (z * o.y),
                (w * o.y) - (x * o.z) + (y * o.w) + (z * o.x),
                (w * o.z) + (x * o.y) - (y * o.x) + (z * o.w)
        );
    }

    public Quaternion normalize() {
        double n = Math.sqrt((w * w) + (x * x) + (y * y) + (z * z));
        if (n <= 1e-12) {
            return IDENTITY;
        }
        return new Quaternion(w / n, x / n, y / n, z / n);
    }

    public Vector3 rotate(Vector3 v) {
        Quaternion p = new Quaternion(0, v.x(), v.y(), v.z());
        Quaternion qInv = new Quaternion(w, -x, -y, -z).normalize();
        Quaternion out = this.mul(p).mul(qInv);
        return new Vector3(out.x, out.y, out.z);
    }
}
