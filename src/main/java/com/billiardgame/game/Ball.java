package com.billiardgame.game;

import com.billiardgame.physics.Vector2;

public final class Ball {
    private final Vector2 position;
    private final double radius;

    public Ball(Vector2 position, double radius) {
        this.position = position;
        this.radius = radius;
    }

    public Vector2 position() {
        return position;
    }

    public double radius() {
        return radius;
    }
}