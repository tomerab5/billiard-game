package com.billiardgame.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class QuaternionTest {

    @Test
    void rotatingVectorPreservesLength() {
        Vector3 axis = new Vector3(0.3, 0.7, 0.2).normalized();
        Quaternion q = Quaternion.fromAxisAngle(axis, 1.234).normalize();
        Vector3 v = new Vector3(2.1, -0.7, 1.4);

        Vector3 rotated = q.rotate(v);
        assertEquals(v.length(), rotated.length(), 1e-9);
    }

    @Test
    void constantAngularVelocityAroundZReturnsNearIdentityAfterFullTurn() {
        Vector3 omega = new Vector3(0, 0, 12.0);
        double period = (Math.PI * 2.0) / omega.length();
        double dt = 1.0 / 1200.0;
        int steps = (int) Math.round(period / dt);

        Quaternion orientation = Quaternion.IDENTITY;
        for (int i = 0; i < steps; i++) {
            double angle = omega.length() * dt;
            Quaternion dq = Quaternion.fromAxisAngle(omega.normalized(), angle);
            orientation = dq.mul(orientation).normalize();
        }

        Vector3 original = new Vector3(1, 0, 0);
        Vector3 rotated = orientation.rotate(original);
        assertEquals(original.x(), rotated.x(), 5e-3);
        assertEquals(original.y(), rotated.y(), 5e-3);
        assertEquals(original.z(), rotated.z(), 5e-3);
    }
}
