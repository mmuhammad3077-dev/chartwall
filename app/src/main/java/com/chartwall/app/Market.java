package com.chartwall.app;

/** Illustrative symbols and starting prices. Prices are for design only, not live data. */
public final class Market {
    public final String id;
    public final double vol;
    public final String[] symbols;
    public final double[] prices;

    private Market(String id, double vol, String[] symbols, double[] prices) {
        this.id = id; this.vol = vol; this.symbols = symbols; this.prices = prices;
    }

    private static final Market CRYPTO = new Market("crypto", 0.011,
            new String[]{"BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT", "DOGE/USDT", "ADA/USDT", "AVAX/USDT", "LINK/USDT", "TON/USDT", "DOT/USDT", "LTC/USDT"},
            new double[]{67240, 3420, 162.4, 592.1, 0.5862, 0.1534, 0.4521, 35.62, 15.48, 6.214, 7.105, 84.3});
    private static final Market FOREX = new Market("forex", 0.0028,
            new String[]{"EUR/USD", "GBP/USD", "USD/JPY", "XAU/USD", "AUD/USD", "USD/CAD", "USD/CHF", "NZD/USD", "EUR/JPY", "GBP/JPY", "USD/PKR", "EUR/GBP"},
            new double[]{1.08423, 1.27154, 151.284, 2348.6, 0.66118, 1.36042, 0.90514, 0.60247, 164.022, 192.371, 278.45, 0.85267});
    private static final Market STOCKS = new Market("stocks", 0.008,
            new String[]{"AAPL", "NVDA", "TSLA", "MSFT", "AMZN", "META", "GOOGL", "AMD", "NFLX", "SPY", "QQQ", "JPM"},
            new double[]{189.84, 121.36, 241.2, 421.9, 184.7, 503.1, 171.3, 158.6, 682.4, 541.2, 468.9, 198.5});

    public static Market get(String id) {
        if ("forex".equals(id)) return FOREX;
        if ("stocks".equals(id)) return STOCKS;
        return CRYPTO;
    }

    public int decimals(double p) {
        if ("forex".equals(id)) return p > 50 ? 3 : 5;
        return p >= 1 ? 2 : 4;
    }

    public String fmt(double p) {
        int d = decimals(p);
        return String.format(java.util.Locale.US, "%,." + d + "f", p);
    }

    public static String pct(double v) {
        return (v >= 0 ? "+" : "") + String.format(java.util.Locale.US, "%.2f", v * 100) + "%";
    }
}
