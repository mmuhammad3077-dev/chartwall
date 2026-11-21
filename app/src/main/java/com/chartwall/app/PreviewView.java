package com.chartwall.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewOutlineProvider;

/** Phone-shaped live preview inside the settings screen. */
public class PreviewView extends View {
    private final Renderer renderer;
    private Config cfg;
    private Sim sim;
    private long t0;
    private final float ratio;

    public PreviewView(Context ctx) {
        super(ctx);
        renderer = new Renderer(ctx);
        renderer.onLockScreen = false; // preview shows the home-screen layout
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        float w = Math.min(dm.widthPixels, dm.heightPixels), h = Math.max(dm.widthPixels, dm.heightPixels);
        ratio = h / w;
        final float radius = 22 * dm.density;
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });
        setClipToOutline(true);
    }

    public void setConfig(Config c, boolean resetSim) {
        cfg = c;
        if (resetSim || sim == null) { sim = new Sim(c); t0 = SystemClock.uptimeMillis(); }
        invalidate();
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, Math.round(w * ratio));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (cfg == null || getWidth() == 0) return;
        long t = SystemClock.uptimeMillis() - t0;
        sim.step(t);
        renderer.draw(canvas, getWidth(), getHeight(), cfg, sim, t);
        if (sim.moving() && isShown()) postInvalidateOnAnimation();
    }
}
