package com.chartwall.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;

/** Everything the user can customise. Stored in SharedPreferences; the logo lives in a file. */
public final class Config {
    public static final String PREFS = "chartwall";
    public static final String LOGO_FILE = "logo.png";

    public static final String[] STYLES = {"candles", "terminal", "depth", "heatmap", "fib", "quote"};
    public static final String[] STYLE_NAMES = {"Candles", "Terminal", "Order book", "Heatmap", "Fibonacci", "Quote"};
    public static final String[] MARKETS = {"crypto", "forex", "stocks"};
    public static final String[] MARKET_NAMES = {"Crypto", "Forex", "Stocks"};
    public static final String[] TRENDS = {"bull", "range", "bear"};
    public static final String[] TREND_NAMES = {"Bullish", "Sideways", "Bearish"};
    public static final String[] SPEED_NAMES = {"Still", "Slow", "Normal", "Fast"};
    public static final int[] SPEED_MS = {0, 2600, 1400, 700};

    public static final String[] QUOTES = {
            "Patience is a position.",
            "No setup, no trade.",
            "Risk first. Profit later.",
            "Protect the capital. The rest follows.",
            "Cut losers fast. Let winners breathe.",
            "The chart doesn't care how you feel.",
            "One clean trade beats ten forced ones.",
            "Stop loss is not optional.",
            "Discipline compounds.",
            "Sabr karo, setup aayega.",
            "Loss chhota rakho, game lamba khelo.",
    };

    public String style = "candles";
    public String pal = "neon";
    public String market = "crypto";
    public String symbol = "BTC/USDT";
    public String trend = "bull";
    public int speed = 2;
    public String quote = QUOTES[0];
    public boolean showQuote = true;
    /** Keep the top half clear for phones with a very big lock-screen clock. */
    public boolean bigClock = true;
    /** True when Pro is bought (Play build) or always (GitHub test build). Never saved by save(). */
    public boolean pro = false;

    /* What the free version includes. Everything else needs Pro. */
    public static final String[] FREE_STYLES = {"candles", "terminal", "quote"};
    public static final String[] FREE_PALETTES = {"neon", "mono"};

    public static boolean isFreeStyle(String id) { for (String s : FREE_STYLES) if (s.equals(id)) return true; return false; }
    public static boolean isFreePalette(String id) { for (String s : FREE_PALETTES) if (s.equals(id)) return true; return false; }

    /** Pro features the current settings use, as short names; empty when everything is free. */
    public java.util.List<String> lockedInUse() {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        if (pro) return out;
        if (!isFreeStyle(style)) out.add("style");
        if (!isFreePalette(pal)) out.add("colors");
        if (!company.isEmpty()) out.add("company name");
        if (logo != null) out.add("logo");
        return out;
    }

    /** Drop Pro-only settings (used by the wallpaper itself when Pro is not owned). */
    public void applyFreeLimits() {
        if (pro) return;
        if (!isFreeStyle(style)) style = FREE_STYLES[0];
        if (!isFreePalette(pal)) pal = FREE_PALETTES[0];
        company = "";
        logo = null;
    }

    public static boolean isPro(Context ctx) {
        return BuildConfig.UNLOCK_ALL || ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("pro", false);
    }

    public static void setPro(Context ctx, boolean v) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("pro", v).putLong("savedAt", System.nanoTime()).apply();
    }
    public String name = "";
    public String company = "";
    public long seed = 20261120L;
    public Bitmap logo;

    public static Config load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Config c = new Config();
        c.style = p.getString("style", c.style);
        c.pal = p.getString("pal", c.pal);
        c.market = p.getString("market", c.market);
        c.symbol = p.getString("symbol", c.symbol);
        c.trend = p.getString("trend", c.trend);
        c.speed = p.getInt("speed", c.speed);
        c.quote = p.getString("quote", c.quote);
        c.showQuote = p.getBoolean("showQuote", c.showQuote);
        c.bigClock = p.getBoolean("bigClock", c.bigClock);
        c.name = p.getString("name", c.name);
        c.company = p.getString("company", c.company);
        c.seed = p.getLong("seed", c.seed);
        c.pro = isPro(ctx);
        File f = new File(ctx.getFilesDir(), LOGO_FILE);
        if (f.exists()) {
            try { c.logo = BitmapFactory.decodeFile(f.getAbsolutePath()); } catch (Throwable ignored) { c.logo = null; }
        }
        return c;
    }

    public void save(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit()
                .putString("style", style).putString("pal", pal).putString("market", market)
                .putString("symbol", symbol).putString("trend", trend).putInt("speed", speed)
                .putString("quote", quote).putBoolean("showQuote", showQuote).putBoolean("bigClock", bigClock)
                .putString("name", name).putString("company", company).putLong("seed", seed)
                .putLong("savedAt", System.nanoTime())
                .apply();
    }

    public int speedMs() {
        return SPEED_MS[Math.max(0, Math.min(SPEED_MS.length - 1, speed))];
    }

    public Market marketData() { return Market.get(market); }

    /** Base price of the chosen symbol, or the market's first symbol for a custom one. */
    public double basePrice() {
        Market m = marketData();
        for (int i = 0; i < m.symbols.length; i++) if (m.symbols[i].equalsIgnoreCase(symbol)) return m.prices[i];
        return m.prices[0];
    }

    public String displaySymbol() {
        String s = symbol == null ? "" : symbol.trim().toUpperCase();
        return s.isEmpty() ? marketData().symbols[0] : s;
    }

    public String quoteText() {
        String q = quote == null ? "" : quote.trim();
        return q.isEmpty() ? QUOTES[0] : q;
    }
}
