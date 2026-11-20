package com.chartwall.app;

import android.graphics.Color;

public final class Palette {
    public final String id, name;
    public final int bg, bg2, up, down, ink, dim, hl;

    private Palette(String id, String name, String bg, String bg2, String up, String down, String ink, String dim, String hl) {
        this.id = id; this.name = name;
        this.bg = Color.parseColor(bg); this.bg2 = Color.parseColor(bg2);
        this.up = Color.parseColor(up); this.down = Color.parseColor(down);
        this.ink = Color.parseColor(ink); this.dim = Color.parseColor(dim); this.hl = Color.parseColor(hl);
    }

    public static final Palette[] ALL = {
            new Palette("neon", "Neon", "#04070a", "#06231a", "#16f29a", "#ff3b5c", "#e9f6f0", "#5f7d73", "#16f29a"),
            new Palette("gold", "Gold", "#0a0806", "#241906", "#f2c14e", "#d9483b", "#f6ecd6", "#857457", "#f2c14e"),
            new Palette("ice", "Ice", "#050912", "#0b2142", "#45d4ff", "#ff6f91", "#e4f3ff", "#5d7598", "#45d4ff"),
            new Palette("violet", "Violet", "#08060f", "#22123f", "#a98bff", "#ff5fa2", "#f0eaff", "#706594", "#a98bff"),
            new Palette("mono", "Mono", "#000000", "#1a1a1a", "#f4f4f4", "#707070", "#ffffff", "#6c6c6c", "#ffffff"),
    };

    public static Palette get(String id) {
        for (Palette p : ALL) if (p.id.equals(id)) return p;
        return ALL[0];
    }

    public static int alpha(int color, float a) {
        return Color.argb(Math.round(Math.max(0, Math.min(1, a)) * 255), Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int mix(int c1, int c2, float t) {
        return Color.rgb(
                Math.round(Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t),
                Math.round(Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t),
                Math.round(Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t));
    }
}
