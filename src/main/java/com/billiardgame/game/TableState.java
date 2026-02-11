package com.billiardgame.game;

import com.billiardgame.physics.Vector2;
import java.util.List;

public final class TableState {
    private final double tableX;
    private final double tableY;
    private final double tableWidth;
    private final double tableHeight;
    private final List<Vector2> pockets;
    private final Ball cueBall;

    public TableState(double tableX, double tableY, double tableWidth, double tableHeight, List<Vector2> pockets, Ball cueBall) {
        this.tableX = tableX;
        this.tableY = tableY;
        this.tableWidth = tableWidth;
        this.tableHeight = tableHeight;
        this.pockets = pockets;
        this.cueBall = cueBall;
    }

    public double tableX() {
        return tableX;
    }

    public double tableY() {
        return tableY;
    }

    public double tableWidth() {
        return tableWidth;
    }

    public double tableHeight() {
        return tableHeight;
    }

    public List<Vector2> pockets() {
        return pockets;
    }

    public Ball cueBall() {
        return cueBall;
    }
}