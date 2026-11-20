package com.chartwall.app;

import android.app.Activity;

/** GitHub test build: no Google Play, everything is unlocked (BuildConfig.UNLOCK_ALL). */
public final class Store {
    public interface Listener {
        void onProChanged(boolean pro);
        void onPrice(String formattedPrice);
        void onMessage(String msg);
    }

    private final Listener listener;

    public Store(Activity activity, Listener listener) { this.listener = listener; }

    public static boolean billingEnabled() { return false; }

    public void start() { listener.onProChanged(true); }

    public void end() { }

    public void buy() { listener.onMessage("Ye test version hai, is mein sab kuch pehle se unlocked hai."); }
}
