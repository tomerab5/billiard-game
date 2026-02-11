package com.billiardgame.physics;

public final class TableBounds {
    private final double left;
    private final double right;
    private final double top;
    private final double bottom;

    public TableBounds(double left, double right, double top, double bottom) {
        if (right <= left) {
            throw new IllegalArgumentException("right must be greater than left");
        }
        if (bottom <= top) {
            throw new IllegalArgumentException("bottom must be greater than top");
        }
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
    }

    public double left() {
        return left;
    }

    public double right() {
        return right;
    }

    public double top() {
        return top;
    }

    public double bottom() {
        return bottom;
    }
}
