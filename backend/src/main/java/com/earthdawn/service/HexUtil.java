package com.earthdawn.service;

/**
 * Hexgitter-Mathematik für die Kampfkarte.
 *
 * Koordinatensystem: axial (q, r) mit spitzen Hexes ("pointy-top"), gespeichert als
 * "odd-r"-Versatzäquivalent: gültige Felder sind 0 ≤ q < width, 0 ≤ r < height, wobei q die
 * Spalte und r die Zeile bezeichnet. Für Distanz und Nachbarschaft wird in kubische
 * Koordinaten umgerechnet — die Zeilenparität bestimmt den Spaltenversatz.
 */
public final class HexUtil {

    private HexUtil() {
    }

    /** Nachbar-Offsets (dq) für gerade Zeilen, indexiert 0-5. */
    private static final int[][] NEIGHBORS_EVEN = {
            {+1, 0}, {0, -1}, {-1, -1}, {-1, 0}, {-1, +1}, {0, +1}
    };
    /** Nachbar-Offsets (dq) für ungerade Zeilen. */
    private static final int[][] NEIGHBORS_ODD = {
            {+1, 0}, {+1, -1}, {0, -1}, {-1, 0}, {0, +1}, {+1, +1}
    };

    /** Die 6 Nachbarfelder von (q, r) im odd-r-Gitter. */
    public static int[][] neighbors(int q, int r) {
        int[][] offsets = (r % 2 == 0) ? NEIGHBORS_EVEN : NEIGHBORS_ODD;
        int[][] out = new int[6][2];
        for (int i = 0; i < 6; i++) {
            out[i][0] = q + offsets[i][0];
            out[i][1] = r + offsets[i][1];
        }
        return out;
    }

    /** Hexdistanz zwischen zwei odd-r-Feldern (Anzahl Felder auf kürzestem Weg). */
    public static int distance(int q1, int r1, int q2, int r2) {
        // odd-r → kubisch
        int x1 = q1 - (r1 - (r1 & 1)) / 2;
        int z1 = r1;
        int y1 = -x1 - z1;
        int x2 = q2 - (r2 - (r2 & 1)) / 2;
        int z2 = r2;
        int y2 = -x2 - z2;
        return (Math.abs(x1 - x2) + Math.abs(y1 - y2) + Math.abs(z1 - z2)) / 2;
    }

    public static boolean inBounds(int q, int r, int width, int height) {
        return q >= 0 && q < width && r >= 0 && r < height;
    }
}
