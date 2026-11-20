package com.chartwall.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** A made-up market that keeps printing candles. Time is passed in, so preview and wallpaper share it. */
public final class Sim {
    public static final int VISIBLE = 46;

    public static final class Candle {
        public double o, h, l, c, v, target, w1, w2, amp, vmax;
    }

    private final Random rng;
    private final double base, vol;
    private final String trend;
    private final int speedMs;
    public final ArrayList<Candle> done = new ArrayList<>();
    public Candle cur;
    private long idx;
    public double f, lo, hi;
    private boolean smoothed;

    public Sim(Config cfg) {
        Market m = cfg.marketData();
        base = cfg.basePrice();
        vol = m.vol;
        trend = cfg.trend;
        speedMs = cfg.speedMs();
        rng = new Random(cfg.seed ^ 0x9e3779b9L);
        List<Candle> hist = series(new Random(cfg.seed), VISIBLE + 14, base, vol, trend);
        done.addAll(hist.subList(hist.size() - VISIBLE, hist.size()));
        newCandle();
        double[] r = range(); lo = r[0]; hi = r[1];
    }

    public static List<Candle> series(Random rng, int n, double base, double vol, String trend) {
        double drift = "bull".equals(trend) ? 0.16 : "bear".equals(trend) ? -0.16 : 0;
        double p = base * ("bull".equals(trend) ? 0.96 : "bear".equals(trend) ? 1.04 : 1);
        double start = p;
        ArrayList<Candle> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double o = p;
            double r = (rng.nextDouble() - 0.5) * 2 * vol + drift * vol;
            if ("range".equals(trend)) r += (start - p) / start * 0.25;
            double c = o * (1 + r);
            Candle k = new Candle();
            k.o = o; k.c = c;
            k.h = Math.max(o, c) * (1 + rng.nextDouble() * vol * 0.7);
            k.l = Math.min(o, c) * (1 - rng.nextDouble() * vol * 0.7);
            k.v = k.vmax = 0.25 + rng.nextDouble() * (Math.abs(r) / vol + 0.3);
            out.add(k);
            p = c;
        }
        return out;
    }

    private void newCandle() {
        double o = cur != null ? cur.c : done.get(done.size() - 1).c;
        double drift = "bull".equals(trend) ? 0.16 : "bear".equals(trend) ? -0.16 : 0;
        Candle k = new Candle();
        k.o = k.h = k.l = k.c = o;
        k.target = (rng.nextDouble() - 0.5) * 2 * vol + drift * vol + (base - o) / base * 0.12;
        k.w1 = rng.nextDouble() * 6.28; k.w2 = rng.nextDouble() * 6.28;
        k.amp = vol * (0.25 + rng.nextDouble() * 0.4);
        k.vmax = 0.3 + rng.nextDouble() * (Math.abs(k.target) / vol + 0.4);
        k.v = 0.2;
        cur = k;
    }

    public boolean moving() { return speedMs > 0; }

    public void step(long tMs) {
        if (speedMs <= 0) {
            Candle c = cur;
            c.c = c.o * (1 + c.target);
            c.h = Math.max(c.o, c.c) * (1 + c.amp * 0.5);
            c.l = Math.min(c.o, c.c) * (1 - c.amp * 0.5);
            c.v = c.vmax; f = 1; return;
        }
        long n = tMs / speedMs;
        if (n - idx > VISIBLE * 2) idx = n - VISIBLE * 2; // after a long sleep, skip ahead
        while (idx < n) {
            Candle c = cur;
            c.c = c.o * (1 + c.target); c.h = Math.max(c.h, c.c); c.l = Math.min(c.l, c.c); c.v = c.vmax;
            done.add(c); done.remove(0); idx++; newCandle();
        }
        f = (tMs - n * (double) speedMs) / speedMs;
        Candle c = cur;
        double wig = Math.sin(f * 9 + c.w1) * Math.sin(f * 3.3 + c.w2) * c.amp * (1 - f * 0.6);
        double price = c.o * (1 + c.target * (f * f * (3 - 2 * f)) + wig);
        c.c = price; c.h = Math.max(c.h, price); c.l = Math.min(c.l, price); c.v = c.vmax * f;
        double[] r = range();
        double k = smoothed ? 0.12 : 1; smoothed = true;
        lo += (r[0] - lo) * k; hi += (r[1] - hi) * k;
    }

    private double[] range() {
        double l = Double.MAX_VALUE, h = -Double.MAX_VALUE;
        for (Candle d : done) { l = Math.min(l, d.l); h = Math.max(h, d.h); }
        if (cur != null) { l = Math.min(l, cur.l); h = Math.max(h, cur.h); }
        double pad = (h - l) * 0.08;
        return new double[]{l - pad, h + pad};
    }

    public List<Candle> candles() {
        ArrayList<Candle> all = new ArrayList<>(done);
        all.add(cur);
        return all;
    }
}
