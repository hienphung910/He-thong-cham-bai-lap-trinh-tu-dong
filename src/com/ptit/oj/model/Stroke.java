package com.ptit.oj.model;

/** Mot net ve tren bang trang: mau + day toa do "x1,y1 x2,y2 ...". */
public class Stroke {

    private final String color;
    private final String points;

    public Stroke(String color, String points) {
        this.color = color;
        this.points = points;
    }

    public String getColor() { return color; }
    public String getPoints() { return points; }
}
