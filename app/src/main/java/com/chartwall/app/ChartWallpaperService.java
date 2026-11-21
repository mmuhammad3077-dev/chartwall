package com.chartwall.app;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

/** The live wallpaper. Draws only while visible, so it sleeps when the screen is off or an app covers it. */
public class ChartWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() { return new ChartEngine(); }

    private final class ChartEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private Renderer renderer;
        private Config cfg;
        private Sim sim;
        private long t0;
        private boolean visible;
        private int width, height;
        private final Runnable drawRunner = this::drawFrame;
        private KeyguardManager keyguard;
        /** Redraw when the phone locks or unlocks, so the layout switches even when the candles are still. */
        private final BroadcastReceiver lockReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) { schedule(0); }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            renderer = new Renderer(ChartWallpaperService.this);
            keyguard = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_USER_PRESENT);
            f.addAction(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            try { registerReceiver(lockReceiver, f); } catch (Throwable ignored) { }
            reload();
            getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(this);
        }

        private void reload() {
            cfg = Config.load(ChartWallpaperService.this);
            cfg.applyFreeLimits();
            sim = new Sim(cfg);
            t0 = SystemClock.uptimeMillis();
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            reload();
            schedule(0);
        }

        @Override
        public void onVisibilityChanged(boolean v) {
            visible = v;
            if (v) schedule(0); else handler.removeCallbacks(drawRunner);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            super.onSurfaceChanged(holder, format, w, h);
            width = w; height = h;
            schedule(0);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            handler.removeCallbacks(drawRunner);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onDestroy() {
            handler.removeCallbacks(drawRunner);
            try { unregisterReceiver(lockReceiver); } catch (Throwable ignored) { }
            getSharedPreferences(Config.PREFS, Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this);
            super.onDestroy();
        }

        private void schedule(long delay) {
            handler.removeCallbacks(drawRunner);
            if (visible) handler.postDelayed(drawRunner, delay);
        }

        private void drawFrame() {
            if (width <= 0 || height <= 0) return;
            long start = SystemClock.uptimeMillis();
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    try { canvas = holder.getSurface().lockHardwareCanvas(); } catch (Throwable e) { canvas = null; }
                }
                if (canvas == null) canvas = holder.lockCanvas();
                if (canvas != null) {
                    long t = start - t0;
                    sim.step(t);
                    renderer.onLockScreen = keyguard == null || keyguard.isKeyguardLocked();
                    renderer.draw(canvas, width, height, cfg, sim, t);
                }
            } catch (Throwable ignored) {
            } finally {
                if (canvas != null) {
                    try {
                        if (Build.VERSION.SDK_INT >= 26) holder.getSurface().unlockCanvasAndPost(canvas);
                        else holder.unlockCanvasAndPost(canvas);
                    } catch (Throwable ignored) { }
                }
            }
            if (visible && sim.moving()) {
                long frameMs = cfg.speed >= 3 ? 33 : 40; // 30 fps fast, 25 fps otherwise
                schedule(Math.max(5, frameMs - (SystemClock.uptimeMillis() - start)));
            }
        }
    }
}
