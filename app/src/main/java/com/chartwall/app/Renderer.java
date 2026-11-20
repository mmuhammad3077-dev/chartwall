package com.chartwall.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Draws one wallpaper frame. All sizes are designed on a 1080px-wide phone and scaled by s. */
public final class Renderer {
    private final Typeface disp, dispSemi, mono;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private Bitmap bg;
    private String bgKey = "";

    public Renderer(Context ctx) {
        disp = load(ctx, "fonts/ChakraPetch-Bold.ttf", Typeface.DEFAULT_BOLD);
        dispSemi = load(ctx, "fonts/ChakraPetch-SemiBold.ttf", Typeface.DEFAULT_BOLD);
        mono = load(ctx, "fonts/IBMPlexMono-Medium.ttf", Typeface.MONOSPACE);
    }

    private static Typeface load(Context ctx, String asset, Typeface fallback) {
        try { return Typeface.createFromAsset(ctx.getAssets(), asset); } catch (Throwable t) { return fallback; }
    }

    // Per-frame state
    private Canvas c; private int W, H; private float s; private Palette P; private Config cfg; private Sim sim; private long t; private Market mk;

    /* "Big clock" layout: everything designed between 30% and 100% of the height is pushed into the lower part,
       so phones with a huge lock-screen clock (Xiaomi, Realme...) keep the chart readable. */
    private static final float START = 0.545f, K = 0.52f;
    private boolean low;
    private float fy(float f) { return H * (low && f >= 0.3f ? START + (f - 0.3f) * K : f); }

    public void draw(Canvas canvas, int width, int height, Config config, Sim simulation, long tMs) {
        c = canvas; W = width; H = height; s = W / 1080f; cfg = config; sim = simulation; t = tMs;
        P = Palette.get(cfg.pal); mk = cfg.marketData(); low = cfg.bigClock;
        drawBg();
        Random rng = new Random(cfg.seed);
        switch (cfg.style) {
            case "terminal": terminal(); break;
            case "depth": depth(rng); break;
            case "heatmap": heatmap(rng); break;
            case "fib": fib(); break;
            case "quote": quote(); break;
            default: candles();
        }
        if (!cfg.pro) watermark();
        p.setShader(null); p.clearShadowLayer(); p.setPathEffect(null); p.setAlpha(255);
    }

    /** Small mark on free wallpapers; Pro removes it. */
    private void watermark() {
        font(mono, 22 * s, Palette.alpha(P.ink, 0.45f), Paint.Align.RIGHT);
        p.setLetterSpacing(0.1f);
        c.drawText("MADE WITH CHARTWALL", W * 0.93f, H * 0.985f - 24 * s, p);
        p.setLetterSpacing(0);
    }

    /* ---------- helpers ---------- */
    private void fill(int color) { p.reset(); p.setAntiAlias(true); p.setStyle(Paint.Style.FILL); p.setColor(color); }
    private void stroke(int color, float w) { p.reset(); p.setAntiAlias(true); p.setStyle(Paint.Style.STROKE); p.setColor(color); p.setStrokeWidth(w); p.setStrokeCap(Paint.Cap.BUTT); }
    private void font(Typeface tf, float size, int color, Paint.Align align) {
        p.reset(); p.setAntiAlias(true); p.setStyle(Paint.Style.FILL); p.setTypeface(tf); p.setTextSize(size); p.setColor(color); p.setTextAlign(align);
    }
    /** y is the vertical centre of the text. */
    private void textMid(String str, float x, float y) {
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(str, x, y - (fm.ascent + fm.descent) / 2f, p);
    }
    private void line(float x0, float y0, float x1, float y1) { c.drawLine(x0, y0, x1, y1, p); }
    private void dashed(float on, float off) { p.setPathEffect(new DashPathEffect(new float[]{on, off}, 0)); }
    private void glow(int color, float r) { p.setShadowLayer(r, 0, 0, color); }
    private static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }

    private List<String> wrap(String text, float maxW) {
        String[] words = text.split("\\s+");
        ArrayList<String> lines = new ArrayList<>();
        String cur = "";
        for (String w : words) {
            String tt = cur.isEmpty() ? w : cur + " " + w;
            if (p.measureText(tt) > maxW && !cur.isEmpty()) { lines.add(cur); cur = w; } else cur = tt;
        }
        if (!cur.isEmpty()) lines.add(cur);
        return lines;
    }

    private static float wob(long t, int i, float a) {
        return (float) (Math.sin(t / 900.0 + i * 2.1) * Math.sin(t / 2300.0 + i * 0.7) * a);
    }

    /* ---------- background (cached) ---------- */
    private void drawBg() {
        String key = W + "|" + H + "|" + cfg.pal + "|" + cfg.seed;
        if (bg == null || !key.equals(bgKey)) {
            if (bg != null) bg.recycle();
            bg = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas g = new Canvas(bg);
            Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.drawColor(P.bg);
            q.setShader(new RadialGradient(W * 0.78f, H * 0.58f, W * 1.1f, P.bg2, Palette.alpha(P.bg2, 0), Shader.TileMode.CLAMP));
            g.drawRect(0, 0, W, H, q);
            q.setShader(new RadialGradient(W * 0.1f, H * 0.1f, W * 0.9f, Palette.alpha(P.bg2, 0.5f), Palette.alpha(P.bg2, 0), Shader.TileMode.CLAMP));
            g.drawRect(0, 0, W, H, q);
            q.setShader(null);
            q.setStyle(Paint.Style.STROKE); q.setStrokeWidth(Math.max(1, s)); q.setColor(Palette.alpha(P.hl, 0.045f));
            float step = 90 * s;
            for (float x = step / 2; x < W; x += step) g.drawLine(x, 0, x, H, q);
            for (float y = step / 2; y < H; y += step) g.drawLine(0, y, W, y, q);
            q.setStyle(Paint.Style.FILL);
            Random rng = new Random(cfg.seed ^ 0x5bd1e995L);
            int n = Math.round(W * (float) H / 900f);
            for (int i = 0; i < n; i++) {
                q.setColor(Palette.alpha(P.ink, rng.nextFloat() * 0.035f));
                float x = rng.nextFloat() * W, y = rng.nextFloat() * H;
                g.drawRect(x, y, x + s * 1.5f, y + s * 1.5f, q);
            }
            q.setShader(new RadialGradient(W / 2f, H / 2f, H * 0.75f, new int[]{0, 0, 0x8C000000}, new float[]{0, 0.33f, 1}, Shader.TileMode.CLAMP));
            g.drawRect(0, 0, W, H, q);
            bgKey = key;
        }
        c.drawBitmap(bg, 0, 0, null);
    }

    /* ---------- shared blocks ---------- */
    private void header(float y, double change, String tf) {
        float x = W * 0.07f;
        font(disp, 96 * s, P.ink, Paint.Align.LEFT);
        c.drawText(cfg.displaySymbol(), x, y, p);
        font(mono, 30 * s, 0, Paint.Align.LEFT);
        int col = change >= 0 ? P.up : P.down;
        String txt = (change >= 0 ? "▲ " : "▼ ") + Market.pct(change);
        float tw = p.measureText("▲ +00.00%");
        p.setColor(Palette.alpha(col, 0.14f));
        rect.set(x, y + 26 * s, x + tw + 36 * s, y + 76 * s);
        c.drawRoundRect(rect, 10 * s, 10 * s, p);
        p.setColor(col); c.drawText(txt, x + 18 * s, y + 62 * s, p);
        p.setColor(P.dim); c.drawText(tf, x + tw + 60 * s, y + 62 * s, p);
    }

    private boolean hasBrand() { return !cfg.name.isEmpty() || !cfg.company.isEmpty() || cfg.logo != null; }

    private String initials() {
        String src = (!cfg.company.isEmpty() ? cfg.company : cfg.name).trim();
        if (src.isEmpty()) return "";
        String[] parts = src.split("\\s+");
        String r = parts.length > 1 ? "" + parts[0].charAt(0) + parts[1].charAt(0) : src.substring(0, Math.min(2, src.length()));
        return r.toUpperCase();
    }

    private void brand(float x, float y) {
        if (!hasBrand()) return;
        float d = 104 * s, cx = x + d / 2, cy = y + d / 2;
        if (cfg.logo != null) {
            Bitmap lg = cfg.logo;
            fill(0xFFFFFFFF); c.drawCircle(cx, cy, d / 2, p);
            BitmapShader sh = new BitmapShader(lg, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float sc = Math.max(d / lg.getWidth(), d / lg.getHeight());
            Matrix m = new Matrix(); m.setScale(sc, sc);
            m.postTranslate(cx - lg.getWidth() * sc / 2, cy - lg.getHeight() * sc / 2);
            sh.setLocalMatrix(m);
            p.setShader(sh); c.drawCircle(cx, cy, d / 2, p); p.setShader(null);
            stroke(Palette.alpha(P.hl, 0.5f), 2 * s); c.drawCircle(cx, cy, d / 2, p);
        } else {
            fill(Palette.alpha(P.hl, 0.14f)); c.drawCircle(cx, cy, d / 2, p);
            stroke(P.hl, 3 * s); c.drawCircle(cx, cy, d / 2 - 1.5f * s, p);
            font(disp, 44 * s, P.hl, Paint.Align.CENTER); textMid(initials(), cx, cy + 2 * s);
        }
        float tx = x + d + 28 * s;
        String top = !cfg.name.isEmpty() ? cfg.name : cfg.company;
        String bottom = !cfg.name.isEmpty() ? cfg.company : "";
        if (!top.isEmpty()) {
            font(disp, 44 * s, P.ink, Paint.Align.LEFT);
            c.drawText(top, tx, bottom.isEmpty() ? cy + 15 * s : cy - 4 * s, p);
        }
        if (!bottom.isEmpty()) {
            font(mono, 25 * s, P.hl, Paint.Align.LEFT); p.setLetterSpacing(0.12f);
            c.drawText(bottom.toUpperCase(), tx, cy + 36 * s, p);
            p.setLetterSpacing(0);
        }
    }

    private void quoteFooter(float y) {
        float x = W * 0.07f;
        if (hasBrand()) y -= 40 * s;
        if (low) y += 40 * s; // the chart above is squeezed, so give the quote its own room
        if (cfg.showQuote) {
            font(dispSemi, 46 * s, Palette.alpha(P.ink, 0.9f), Paint.Align.LEFT);
            List<String> lines = wrap(cfg.quoteText().toUpperCase(), W * 0.8f);
            for (int i = 0; i < lines.size(); i++) c.drawText(lines.get(i), x + 28 * s, y + i * 58 * s, p);
            fill(P.hl); c.drawRect(x, y - 40 * s, x + 6 * s, y - 40 * s + lines.size() * 58 * s - 10 * s, p);
            y += lines.size() * 58 * s + 10 * s;
        }
        brand(x, y + 6 * s);
    }

    /* ---------- styles ---------- */
    private void candles() {
        float x0 = W * 0.07f, x1 = W * 0.79f, y0 = fy(0.43f), y1 = fy(0.71f), vy1 = fy(0.775f);
        List<Sim.Candle> data = sim.candles();
        double lo = sim.lo, hi = sim.hi;
        font(mono, 24 * s, P.dim, Paint.Align.LEFT);
        for (int k = 0; k <= 5; k++) {
            float y = y0 + (y1 - y0) * k / 5f;
            double pr = hi - (hi - lo) * k / 5.0;
            font(mono, 24 * s, P.dim, Paint.Align.LEFT); textMid(mk.fmt(pr), W * 0.825f, y);
            stroke(Palette.alpha(P.ink, 0.07f), s); dashed(4 * s, 8 * s); line(x0, y, W * 0.81f, y);
        }
        float step = (x1 - x0) / Sim.VISIBLE, bw = step * 0.6f;
        double maxV = 0; for (Sim.Candle d : data) maxV = Math.max(maxV, d.vmax);
        float shift = sim.moving() ? (float) sim.f * step : step;
        c.save(); c.clipRect(x0 - bw, 0, x1 + bw * 0.5f, H);
        for (int i = 0; i < data.size(); i++) {
            Sim.Candle d = data.get(i);
            float x = x0 + step * (i + 0.5f) - shift;
            int col = d.c >= d.o ? P.up : P.down;
            float fade = clamp((x - x0 + bw) / (step * 2), 0, 1);
            fill(Palette.alpha(col, 0.22f * fade));
            float vh = (float) (d.v / maxV) * (vy1 - y1 - 24 * s);
            c.drawRect(x - bw / 2, vy1 - vh, x + bw / 2, vy1, p);
            stroke(Palette.alpha(col, fade), Math.max(1.5f, 2.2f * s));
            line(x, Y(d.h, lo, hi, y0, y1), x, Y(d.l, lo, hi, y0, y1));
            float top = Y(Math.max(d.o, d.c), lo, hi, y0, y1);
            float bh = Math.max(2 * s, Math.abs(Y(d.o, lo, hi, y0, y1) - Y(d.c, lo, hi, y0, y1)));
            fill(Palette.alpha(col, fade));
            if (i == data.size() - 1) glow(col, 30 * s);
            c.drawRect(x - bw / 2, top, x + bw / 2, top + bh, p);
        }
        double k = 2.0 / 10, e = data.get(0).c;
        path.reset();
        for (int i = 0; i < data.size(); i++) {
            e = data.get(i).c * k + e * (1 - k);
            float x = x0 + step * (i + 0.5f) - shift, y = Y(e, lo, hi, y0, y1);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        stroke(Palette.alpha(P.ink, 0.55f), 2.5f * s); p.setStrokeJoin(Paint.Join.ROUND); c.drawPath(path, p);
        c.restore();
        Sim.Candle last = data.get(data.size() - 1);
        float ly = Y(last.c, lo, hi, y0, y1);
        int lc = last.c >= last.o ? P.up : P.down;
        stroke(Palette.alpha(lc, 0.7f), 2 * s); dashed(10 * s, 8 * s); line(x0, ly, W * 0.81f, ly);
        font(mono, 26 * s, P.bg, Paint.Align.LEFT);
        String lt = mk.fmt(last.c);
        float lw = p.measureText(lt);
        fill(lc); rect.set(W * 0.815f, ly - 24 * s, W * 0.815f + Math.min(lw + 24 * s, W * 0.18f), ly + 24 * s);
        c.drawRoundRect(rect, 6 * s, 6 * s, p);
        font(mono, 26 * s, P.bg, Paint.Align.LEFT); textMid(lt, W * 0.815f + 12 * s, ly);
        header(fy(0.375f) - (low ? 36 * s : 0), last.c / data.get(0).o - 1, "1H · EMA 9");
        quoteFooter(fy(0.835f));
    }

    private static float Y(double pr, double lo, double hi, float y0, float y1) {
        return (float) (y1 - (pr - lo) / (hi - lo) * (y1 - y0));
    }

    private void terminal() {
        float x0 = W * 0.07f, x1 = W * 0.93f, y = fy(0.34f);
        font(mono, 28 * s, P.hl, Paint.Align.LEFT); c.drawText("▌MARKET WATCH", x0, y, p);
        font(mono, 28 * s, P.dim, Paint.Align.RIGHT); c.drawText(mk.id.toUpperCase() + " · 24H", x1, y, p);
        y += 56 * s;
        font(mono, 22 * s, P.dim, Paint.Align.LEFT); c.drawText("SYMBOL", x0, y, p);
        p.setTextAlign(Paint.Align.RIGHT); c.drawText("LAST", W * 0.58f, y, p); c.drawText("CHG", W * 0.76f, y, p);
        p.setTextAlign(Paint.Align.LEFT); c.drawText("TREND", W * 0.8f, y, p);
        y += 18 * s;
        fill(Palette.alpha(P.ink, 0.12f)); c.drawRect(x0, y, x1, y + 2 * s, p);

        String sym = cfg.displaySymbol();
        ArrayList<String> syms = new ArrayList<>(); ArrayList<Double> prices = new ArrayList<>();
        syms.add(sym); prices.add(cfg.basePrice());
        for (int i = 0; i < mk.symbols.length && syms.size() < 12; i++) {
            if (mk.symbols[i].equalsIgnoreCase(sym)) continue;
            syms.add(mk.symbols[i]); prices.add(mk.prices[i]);
        }
        float rowH = H * 0.037f * (low ? K : 1);
        for (int i = 0; i < syms.size(); i++) {
            double[] cl;
            if (i == 0) {
                List<Sim.Candle> cs = sim.candles();
                List<Sim.Candle> tail = cs.subList(Math.max(0, cs.size() - 24), cs.size());
                cl = new double[tail.size()];
                for (int j = 0; j < cl.length; j++) cl[j] = tail.get(j).c;
            } else {
                Random r2 = new Random(cfg.seed + i * 977L);
                String tr = r2.nextBoolean() ? "bull" : "bear";
                List<Sim.Candle> d = Sim.series(r2, 24, prices.get(i), mk.vol, tr);
                cl = new double[d.size()];
                for (int j = 0; j < cl.length; j++) cl[j] = d.get(j).c;
                if (sim.moving()) cl[cl.length - 1] *= 1 + wob(t, i, (float) mk.vol * 0.8f);
            }
            double last = cl[cl.length - 1], ch = last / cl[0] - 1;
            int col = ch >= 0 ? P.up : P.down;
            float ry = y + rowH * (i + 0.5f) + 8 * s;
            if (i == 0) {
                fill(Palette.alpha(P.hl, 0.1f)); c.drawRect(x0 - 12 * s, ry - rowH / 2, x1 + 12 * s, ry + rowH / 2, p);
                fill(P.hl); c.drawRect(x0 - 12 * s, ry - rowH / 2, x0 - 7 * s, ry + rowH / 2, p);
            }
            font(mono, 29 * s, P.ink, Paint.Align.LEFT); if (i == 0) p.setFakeBoldText(true);
            textMid(syms.get(i).replace("/USDT", ""), x0 + 8 * s, ry);
            p.setTextAlign(Paint.Align.RIGHT); textMid(mk.fmt(last), W * 0.58f, ry);
            p.setColor(col); textMid(Market.pct(ch), W * 0.76f, ry);
            float sx0 = W * 0.8f, sh = rowH * 0.5f;
            double mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE;
            for (double v : cl) { mn = Math.min(mn, v); mx = Math.max(mx, v); }
            path.reset();
            for (int j = 0; j < cl.length; j++) {
                float px = sx0 + (x1 - sx0) * j / (cl.length - 1f);
                float py = (float) (ry + sh / 2 - (cl[j] - mn) / ((mx - mn) == 0 ? 1 : (mx - mn)) * sh);
                if (j == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
            stroke(col, 2.2f * s); p.setStrokeJoin(Paint.Join.ROUND); c.drawPath(path, p);
            fill(Palette.alpha(P.ink, 0.05f)); c.drawRect(x0, ry + rowH / 2, x1, ry + rowH / 2 + s, p);
        }
        fill(0x29000000);
        for (float yy = 0; yy < H; yy += 5 * s) c.drawRect(0, yy, W, yy + 1.6f * s, p);
        float py = y + rowH * 12 + 90 * s;
        if (hasBrand()) py -= 30 * s;
        if (cfg.showQuote) {
            font(mono, 32 * s, P.ink, Paint.Align.LEFT);
            List<String> lines = wrap(cfg.quoteText(), W * 0.78f);
            for (int i = 0; i < lines.size(); i++) c.drawText(lines.get(i), x0 + 40 * s, py + i * 46 * s, p);
            float lw = p.measureText(lines.get(lines.size() - 1));
            p.setColor(P.hl); c.drawText(">", x0, py, p);
            if (!sim.moving() || (t / 530) % 2 == 0) {
                float cy = py + (lines.size() - 1) * 46 * s;
                fill(P.hl); c.drawRect(x0 + 52 * s + lw, cy - 30 * s, x0 + 70 * s + lw, cy + 6 * s, p);
            }
            py += lines.size() * 46 * s;
        }
        brand(x0, py + 4 * s);
    }

    private void depth(Random rng) {
        float x0 = W * 0.05f, x1 = W * 0.95f, yb = fy(0.76f), yt = fy(0.47f), mid = (x0 + x1) / 2;
        int levels = 42; float gap = W * 0.012f;
        float skew = "bull".equals(cfg.trend) ? 1.25f : "bear".equals(cfg.trend) ? 0.8f : 1f;
        float[] bids = side(rng, levels, 0), asks = side(rng, levels, 3);
        for (int i = 0; i < levels; i++) bids[i] *= skew;
        float maxC = Math.max(bids[levels - 1], asks[levels - 1]) * 1.05f;
        area(bids, -1, P.up, levels, gap, mid, x0, yb, yt, maxC);
        area(asks, 1, P.down, levels, gap, mid, x0, yb, yt, maxC);
        stroke(Palette.alpha(P.ink, 0.35f), 2 * s); dashed(8 * s, 10 * s); line(mid, yt - 30 * s, mid, yb);
        fill(Palette.alpha(P.ink, 0.15f)); c.drawRect(x0, yb, x1, yb + 2 * s, p);
        Sim.Candle last = sim.cur;
        double price = last.c, tick = cfg.basePrice() * mk.vol * 0.6;
        font(mono, 22 * s, P.dim, Paint.Align.CENTER);
        for (int k = -2; k <= 2; k++) {
            if (k == 0) continue;
            float x = mid + k * (mid - x0) / 2.4f;
            c.drawText(mk.fmt(price + k * tick * 10), x, yb + 40 * s, p);
        }
        font(mono, 30 * s, P.dim, Paint.Align.CENTER); c.drawText(cfg.displaySymbol() + "  ·  ORDER BOOK", W / 2f, fy(0.335f) - (low ? 24 * s : 0), p);
        font(disp, 120 * s, price >= last.o ? P.ink : P.down, Paint.Align.CENTER); c.drawText(mk.fmt(price), W / 2f, fy(0.40f) + (low ? 22 * s : 0), p);
        font(mono, 28 * s, P.up, Paint.Align.LEFT);
        c.drawText(String.format(java.util.Locale.US, "BIDS %.1fK", bids[levels - 1] * 12), x0, fy(0.445f), p);
        p.setColor(P.down); p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(String.format(java.util.Locale.US, "%.1fK ASKS", asks[levels - 1] * 12), x1, fy(0.445f), p);
        quoteFooter(fy(0.845f));
    }

    private float[] side(Random rng, int levels, double off) {
        float[] out = new float[levels]; float cum = 0;
        for (int i = 0; i < levels; i++) {
            float v = 0.4f + rng.nextFloat() * (i < 6 ? 0.6f : 2.2f) + (rng.nextFloat() < 0.08f ? 4 : 0);
            if (sim.moving()) v *= 1 + 0.35f * (float) (Math.sin(t / 700.0 + i * 0.9 + off) * Math.sin(t / 1700.0 + i * 0.4));
            cum += v; out[i] = cum;
        }
        return out;
    }

    private void area(float[] vals, int dir, int col, int levels, float gap, float mid, float x0, float yb, float yt, float maxC) {
        float w = (mid - gap - x0) / levels;
        path.reset(); path.moveTo(mid + dir * gap, yb);
        for (int i = 0; i < levels; i++) {
            float xa = mid + dir * (gap + w * i), xb = mid + dir * (gap + w * (i + 1)), y = yb - vals[i] / maxC * (yb - yt);
            path.lineTo(xa, y); path.lineTo(xb, y);
        }
        path.lineTo(mid + dir * (gap + w * levels), yb); path.close();
        fill(0); p.setShader(new LinearGradient(0, yt, 0, yb, Palette.alpha(col, 0.45f), Palette.alpha(col, 0.02f), Shader.TileMode.CLAMP));
        c.drawPath(path, p); p.setShader(null);
        path.reset();
        for (int i = 0; i < levels; i++) {
            float xa = mid + dir * (gap + w * i), xb = mid + dir * (gap + w * (i + 1)), y = yb - vals[i] / maxC * (yb - yt);
            if (i == 0) path.moveTo(xa, y); else path.lineTo(xa, y);
            path.lineTo(xb, y);
        }
        stroke(col, 3.5f * s); glow(col, 24 * s); c.drawPath(path, p); p.clearShadowLayer();
    }

    private static final class Tile { String sym; float w; float ch; RectF r; }

    private void heatmap(Random rng) {
        RectF R = new RectF(W * 0.05f, fy(0.36f), W * 0.95f, fy(0.79f));
        String sym = cfg.displaySymbol();
        ArrayList<String> list = new ArrayList<>();
        list.add(sym);
        for (String m : mk.symbols) if (!m.equalsIgnoreCase(sym) && list.size() < 12) list.add(m);
        float bias = "bull".equals(cfg.trend) ? 0.012f : "bear".equals(cfg.trend) ? -0.012f : 0;
        ArrayList<Tile> items = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Tile tl = new Tile(); tl.sym = list.get(i);
            tl.w = i == 0 ? 6 : 0.6f + rng.nextFloat() * 3;
            tl.ch = (rng.nextFloat() - 0.5f) * 0.08f + bias + (sim.moving() ? wob(t, i, 0.012f) : 0);
            items.add(tl);
        }
        Collections.sort(items, (a, b) -> Float.compare(b.w, a.w));
        ArrayList<Tile> out = new ArrayList<>();
        split(items, R, out);
        float g = 6 * s;
        for (Tile q : out) {
            int col = q.ch >= 0 ? P.up : P.down;
            float k = clamp(Math.abs(q.ch) / 0.045f, 0.15f, 1);
            rect.set(q.r.left + g / 2, q.r.top + g / 2, q.r.right - g / 2, q.r.bottom - g / 2);
            fill(Palette.mix(P.bg, col, 0.18f + k * 0.5f)); c.drawRoundRect(rect, 10 * s, 10 * s, p);
            if (q.sym.equalsIgnoreCase(sym)) { stroke(Palette.alpha(P.ink, 0.8f), 3 * s); c.drawRoundRect(rect, 10 * s, 10 * s, p); }
            float w = q.r.width(), h = q.r.height();
            float fs = clamp(Math.min(w, h) * 0.2f, 20 * s, 84 * s);
            String label = q.sym.replace("/USDT", "");
            font(disp, fs, P.ink, Paint.Align.CENTER);
            if (p.measureText(label) < w - 16 * s) {
                textMid(label, q.r.centerX(), q.r.centerY() - fs * 0.35f);
                font(mono, fs * 0.55f, Palette.alpha(P.ink, 0.85f), Paint.Align.CENTER);
                textMid(Market.pct(q.ch), q.r.centerX(), q.r.centerY() + fs * 0.5f);
            }
        }
        font(mono, 28 * s, P.dim, Paint.Align.LEFT);
        c.drawText("HEATMAP · " + mk.id.toUpperCase() + " · 24H", W * 0.05f, fy(0.34f), p);
        quoteFooter(fy(0.85f));
    }

    private static void split(List<Tile> arr, RectF r, List<Tile> out) {
        if (arr.size() == 1) { Tile tl = arr.get(0); tl.r = new RectF(r); out.add(tl); return; }
        float total = 0; for (Tile q : arr) total += q.w;
        float acc = 0; int k = 0;
        while (k < arr.size() - 1 && acc + arr.get(k).w <= total / 2) { acc += arr.get(k).w; k++; }
        if (k == 0) { acc = arr.get(0).w; k = 1; }
        float f = acc / total;
        List<Tile> a = arr.subList(0, k), b = arr.subList(k, arr.size());
        if (r.width() >= r.height()) {
            float xm = r.left + r.width() * f;
            split(a, new RectF(r.left, r.top, xm, r.bottom), out);
            split(b, new RectF(xm, r.top, r.right, r.bottom), out);
        } else {
            float ym = r.top + r.height() * f;
            split(a, new RectF(r.left, r.top, r.right, ym), out);
            split(b, new RectF(r.left, ym, r.right, r.bottom), out);
        }
    }

    private void fib() {
        float x0 = W * 0.07f, x1 = W * 0.74f, y0 = fy(0.44f), y1 = fy(0.75f);
        List<Sim.Candle> cs = sim.candles();
        double[] d = new double[cs.size() * 2];
        for (int i = 0; i < cs.size(); i++) {
            Sim.Candle k = cs.get(i);
            d[i * 2] = (k.o + k.c) / 2 + (k.h - k.l) * 0.15;
            d[i * 2 + 1] = k.c;
        }
        double lo = sim.lo, hi = sim.hi;
        float step = (x1 - x0) / (d.length - 2);
        float shift = sim.moving() ? (float) sim.f * step * 2 : step * 2;
        double[] levels = {0, 0.236, 0.382, 0.5, 0.618, 0.786, 1};
        boolean up = !"bear".equals(cfg.trend);
        float zA = Y(lp(0.5, up, lo, hi), lo, hi, y0, y1), zB = Y(lp(0.618, up, lo, hi), lo, hi, y0, y1);
        fill(Palette.alpha(P.hl, 0.09f)); c.drawRect(x0, Math.min(zA, zB), W * 0.93f, Math.max(zA, zB), p);
        for (double r : levels) {
            float y = Y(lp(r, up, lo, hi), lo, hi, y0, y1);
            boolean gold = r == 0.618 || r == 0.5;
            stroke(Palette.alpha(gold ? P.hl : P.ink, gold ? 0.6f : 0.18f), (gold ? 2.5f : 1.5f) * s); line(x0, y, W * 0.93f, y);
            font(mono, 23 * s, gold ? P.hl : P.dim, Paint.Align.LEFT);
            textMid(String.format(java.util.Locale.US, "%.3f", r), W * 0.765f, y - 16 * s);
            p.setColor(Palette.alpha(P.ink, 0.6f)); textMid(mk.fmt(lp(r, up, lo, hi)), W * 0.765f, y + 14 * s);
        }
        c.save(); c.clipRect(x0, 0, x1 + 30 * s, H);
        path.reset();
        for (int i = 0; i < d.length; i++) {
            float x = x0 + step * i - shift + step * 2, y = Y(d[i], lo, hi, y0, y1);
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        Path areaPath = new Path(path);
        float lastX = x0 + step * (d.length - 1) - shift + step * 2;
        areaPath.lineTo(lastX, y1); areaPath.lineTo(x0 - shift + step * 2, y1); areaPath.close();
        fill(0); p.setShader(new LinearGradient(0, y0, 0, y1, Palette.alpha(P.up, 0.25f), Palette.alpha(P.up, 0), Shader.TileMode.CLAMP));
        c.drawPath(areaPath, p); p.setShader(null);
        stroke(P.up, 4 * s); p.setStrokeJoin(Paint.Join.ROUND); glow(P.up, 22 * s); c.drawPath(path, p); p.clearShadowLayer();
        c.restore();
        float ly = Y(d[d.length - 1], lo, hi, y0, y1);
        float pulse = sim.moving() ? 1 + 0.35f * (float) Math.sin(t / 260.0) : 1;
        fill(P.ink); c.drawCircle(lastX, ly, 9 * s, p);
        stroke(Palette.alpha(P.up, 0.4f), 6 * s); c.drawCircle(lastX, ly, 22 * s * pulse, p);
        header(fy(0.375f) - (low ? 36 * s : 0), d[d.length - 1] / d[0] - 1, "4H · FIB");
        quoteFooter(fy(0.835f));
    }

    private static double lp(double r, boolean up, double lo, double hi) { return up ? hi - (hi - lo) * r : lo + (hi - lo) * r; }

    private void quote() {
        float x = W * 0.08f;
        float fs = 118 * s * (low ? 0.72f : 1);
        font(disp, fs, P.ink, Paint.Align.LEFT);
        List<String> lines = wrap(cfg.quoteText().toUpperCase(), W * 0.84f);
        while (lines.size() > (low ? 3 : 5) && fs > 50 * s) { fs -= 8 * s; p.setTextSize(fs); lines = wrap(cfg.quoteText().toUpperCase(), W * 0.84f); }
        float lh = fs * 1.02f, y = fy(0.42f);
        for (int i = 0; i < lines.size(); i++) { p.setColor(i == lines.size() - 1 ? P.hl : P.ink); c.drawText(lines.get(i), x, y + i * lh, p); }
        y += (lines.size() - 1) * lh + 70 * s;
        fill(P.hl); c.drawRect(x, y, x + 90 * s, y + 6 * s, p);
        List<Sim.Candle> cs = sim.candles();
        double mn = sim.lo, mx = sim.hi;
        float sy0 = fy(0.68f), sy1 = fy(0.76f), sx1 = W * 0.92f;
        float stp = (sx1 - x) / (cs.size() - 1), shift = sim.moving() ? (float) sim.f * stp : stp;
        double first = cs.get(0).c, lastP = cs.get(cs.size() - 1).c;
        int col = lastP >= first ? P.up : P.down;
        c.save(); c.clipRect(x, 0, sx1 + 20 * s, H);
        path.reset();
        for (int i = 0; i < cs.size(); i++) {
            float px = x + stp * (i + 1) - shift, py = Y(cs.get(i).c, mn, mx, sy0, sy1);
            if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        stroke(col, 3 * s); p.setStrokeJoin(Paint.Join.ROUND); glow(col, 18 * s); c.drawPath(path, p); p.clearShadowLayer();
        c.restore();
        fill(col); c.drawCircle(x + stp * cs.size() - shift, Y(lastP, mn, mx, sy0, sy1), 8 * s, p);
        font(mono, 28 * s, P.dim, Paint.Align.LEFT);
        String lbl = cfg.displaySymbol() + "   " + mk.fmt(lastP) + "   ";
        c.drawText(lbl, x, sy1 + 64 * s, p);
        p.setColor(col); c.drawText(Market.pct(lastP / first - 1), x + p.measureText(lbl), sy1 + 64 * s, p);
        brand(x, sy1 + 120 * s);
    }
}
