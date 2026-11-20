package com.chartwall.app;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Random;

/** Settings screen, built in code (no XML layouts) so the app stays tiny. */
public class MainActivity extends Activity {
    private static final int PICK_LOGO = 41;
    private static final int BG = 0xFF080A0D, PANEL = 0xFF0E1217, RAISE = 0xFF1A2129, LINE = 0xFF222A33,
            INK = 0xFFDDE5EC, MUTE = 0xFF7B8A97, ACCENT = 0xFF7CC4FF;

    private Config cfg;
    private PreviewView preview;
    private Typeface disp, mono;
    private float dp;
    private LinearLayout symbolRow;
    private TextView logoState;
    private int quoteIdx = 0;
    private EditText quoteEdit, symbolEdit;
    private final ArrayList<Runnable> refreshers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Window w = getWindow();
        w.setStatusBarColor(BG); w.setNavigationBarColor(BG);
        dp = getResources().getDisplayMetrics().density;
        disp = loadFont("fonts/ChakraPetch-Bold.ttf", Typeface.DEFAULT_BOLD);
        mono = loadFont("fonts/IBMPlexMono-Medium.ttf", Typeface.MONOSPACE);
        cfg = Config.load(this);
        for (int i = 0; i < Config.QUOTES.length; i++) if (Config.QUOTES[i].equals(cfg.quote)) quoteIdx = i;
        setContentView(build());
        preview.setConfig(cfg, true);
    }

    private Typeface loadFont(String a, Typeface fb) {
        try { return Typeface.createFromAsset(getAssets(), a); } catch (Throwable t) { return fb; }
    }

    private int px(float v) { return Math.round(v * dp); }

    /* ---------- layout ---------- */
    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(18), px(20), px(18), px(32));
        scroll.addView(root);

        TextView title = text("Chartwall", 26, INK, disp);
        root.addView(title);
        TextView sub = text("Trader live wallpaper · prices are for design, not live data", 12, MUTE, mono);
        sub.setPadding(0, px(2), 0, px(16));
        root.addView(sub);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int pw = Math.min(px(250), Math.round(dm.widthPixels * 0.62f));
        LinearLayout frame = new LinearLayout(this);
        frame.setPadding(px(7), px(7), px(7), px(7));
        frame.setBackground(rounded(0xFF000000, px(28), LINE));
        preview = new PreviewView(this);
        frame.addView(preview, new LinearLayout.LayoutParams(pw, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(frame, flp);

        Button setBtn = button("Set as live wallpaper", true);
        setBtn.setOnClickListener(v -> setLiveWallpaper());
        root.addView(setBtn, fullWidth(px(18)));

        section(root, "Lock screen clock");
        root.addView(chips(new String[]{"Big clock (chart lower)", "Small clock"}, () -> cfg.bigClock ? 0 : 1, i -> { cfg.bigClock = i == 0; changed(false); }));

        section(root, "Style");
        root.addView(chips(Config.STYLE_NAMES, () -> indexOf(Config.STYLES, cfg.style), i -> { cfg.style = Config.STYLES[i]; changed(false); }));

        section(root, "Colors");
        String[] pn = new String[Palette.ALL.length];
        for (int i = 0; i < pn.length; i++) pn[i] = Palette.ALL[i].name;
        root.addView(chips(pn, () -> { for (int i = 0; i < Palette.ALL.length; i++) if (Palette.ALL[i].id.equals(cfg.pal)) return i; return 0; },
                i -> { cfg.pal = Palette.ALL[i].id; changed(false); }));

        section(root, "Market");
        root.addView(chips(Config.MARKET_NAMES, () -> indexOf(Config.MARKETS, cfg.market), i -> {
            cfg.market = Config.MARKETS[i];
            cfg.symbol = cfg.marketData().symbols[0];
            symbolEdit.setText("");
            rebuildSymbols();
            changed(true);
        }));

        section(root, "Symbol");
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        symbolRow = new LinearLayout(this);
        hs.addView(symbolRow);
        root.addView(hs);
        rebuildSymbols();
        symbolEdit = input("Or type your own, e.g. PSX:OGDC", 14);
        if (!isPreset(cfg.symbol)) symbolEdit.setText(cfg.symbol);
        symbolEdit.addTextChangedListener(new Watcher(s -> {
            String v = s.trim();
            cfg.symbol = v.isEmpty() ? cfg.marketData().symbols[0] : v.toUpperCase();
            refreshAll(); changed(true);
        }));
        root.addView(symbolEdit, fullWidth(px(8)));

        section(root, "Mood");
        root.addView(chips(Config.TREND_NAMES, () -> indexOf(Config.TRENDS, cfg.trend), i -> { cfg.trend = Config.TRENDS[i]; changed(true); }));

        section(root, "Candle speed");
        root.addView(chips(Config.SPEED_NAMES, () -> cfg.speed, i -> { cfg.speed = i; changed(true); }));

        section(root, "Quote");
        quoteEdit = input("Apna trading rule likho", 90);
        quoteEdit.setText(cfg.quote);
        quoteEdit.addTextChangedListener(new Watcher(s -> { cfg.quote = s; changed(false); }));
        root.addView(quoteEdit, fullWidth(0));
        LinearLayout qrow = new LinearLayout(this);
        qrow.setGravity(Gravity.CENTER_VERTICAL);
        Button next = button("Next quote", false);
        next.setOnClickListener(v -> { quoteIdx = (quoteIdx + 1) % Config.QUOTES.length; quoteEdit.setText(Config.QUOTES[quoteIdx]); });
        qrow.addView(next);
        CheckBox show = new CheckBox(this);
        show.setText("Show quote");
        show.setTextColor(INK);
        show.setChecked(cfg.showQuote);
        show.setButtonTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        show.setOnCheckedChangeListener((v, c) -> { cfg.showQuote = c; changed(false); });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = px(14);
        qrow.addView(show, slp);
        root.addView(qrow, fullWidth(px(8)));

        TextView bh = text("Your branding", 18, INK, disp);
        bh.setPadding(0, px(26), 0, 0);
        root.addView(bh);
        section(root, "Your name");
        EditText name = input("Ali Muhammad", 28);
        name.setText(cfg.name);
        name.addTextChangedListener(new Watcher(s -> { cfg.name = s.trim(); changed(false); }));
        root.addView(name, fullWidth(0));
        section(root, "Company / brand");
        EditText company = input("Ali Capital", 32);
        company.setText(cfg.company);
        company.addTextChangedListener(new Watcher(s -> { cfg.company = s.trim(); changed(false); }));
        root.addView(company, fullWidth(0));
        section(root, "Logo");
        LinearLayout lrow = new LinearLayout(this);
        lrow.setGravity(Gravity.CENTER_VERTICAL);
        Button up = button("Upload logo", false);
        up.setOnClickListener(v -> pickLogo());
        Button rm = button("Remove", false);
        rm.setOnClickListener(v -> { new File(getFilesDir(), Config.LOGO_FILE).delete(); cfg.logo = null; updateLogoState(); changed(false); });
        lrow.addView(up);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.leftMargin = px(10);
        lrow.addView(rm, rlp);
        root.addView(lrow);
        logoState = text("", 12, MUTE, mono);
        logoState.setPadding(0, px(6), 0, 0);
        root.addView(logoState);
        updateLogoState();

        LinearLayout arow = new LinearLayout(this);
        arow.setPadding(0, px(26), 0, 0);
        Button shuffle = button("New chart", false);
        shuffle.setOnClickListener(v -> { cfg.seed = new Random().nextInt(Integer.MAX_VALUE); changed(true); });
        Button still = button("Save still image", false);
        still.setOnClickListener(v -> saveStill());
        arow.addView(shuffle);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.leftMargin = px(10);
        arow.addView(still, alp);
        root.addView(arow);

        Button setBtn2 = button("Set as live wallpaper", true);
        setBtn2.setOnClickListener(v -> setLiveWallpaper());
        root.addView(setBtn2, fullWidth(px(14)));

        TextView how = text("Set as live wallpaper dabao, phir Home screen ya Home and lock screen choose karo. "
                + "Settings yahan badlo, wallpaper khud update ho jayega. Screen band ho to animation ruk jati hai, is liye battery kam lagti hai.", 13, MUTE, null);
        how.setPadding(0, px(18), 0, 0);
        how.setLineSpacing(0, 1.25f);
        root.addView(how);
        return scroll;
    }

    private void section(LinearLayout root, String label) {
        TextView t = text(label.toUpperCase(), 11, MUTE, mono);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, px(18), 0, px(8));
        root.addView(t);
    }

    private TextView text(String s, float sp, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private GradientDrawable rounded(int fill, int radius, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(radius);
        if (strokeColor != 0) g.setStroke(px(1), strokeColor);
        return g;
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? 0xFF04121F : INK);
        b.setBackground(rounded(primary ? ACCENT : RAISE, px(12), primary ? 0 : LINE));
        b.setPadding(px(18), px(12), px(18), px(12));
        b.setStateListAnimator(null);
        return b;
    }

    private EditText input(String hint, int max) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF55626D);
        e.setTextColor(INK);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});
        e.setBackground(rounded(PANEL, px(12), LINE));
        e.setPadding(px(12), px(11), px(12), px(11));
        return e;
    }

    private LinearLayout.LayoutParams fullWidth(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = top;
        return lp;
    }

    interface IntGetter { int get(); }
    interface IntSetter { void set(int i); }

    private View chips(String[] labels, IntGetter selected, IntSetter onPick) {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        hs.addView(row);
        ArrayList<TextView> views = new ArrayList<>();
        Runnable refresh = () -> {
            int sel = selected.get();
            for (int i = 0; i < views.size(); i++) styleChip(views.get(i), i == sel);
        };
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView c = chip(labels[i]);
            c.setOnClickListener(v -> { onPick.set(idx); refreshAll(); });
            views.add(c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = px(8);
            row.addView(c, lp);
        }
        refreshers.add(refresh);
        refresh.run();
        return hs;
    }

    private TextView chip(String label) {
        TextView c = text(label, 14, INK, null);
        c.setPadding(px(14), px(9), px(14), px(9));
        c.setGravity(Gravity.CENTER);
        return c;
    }

    private void styleChip(TextView c, boolean on) {
        c.setBackground(rounded(on ? RAISE : PANEL, px(20), on ? ACCENT : LINE));
        c.setTextColor(on ? INK : MUTE);
    }

    private void rebuildSymbols() {
        symbolRow.removeAllViews();
        Market m = cfg.marketData();
        ArrayList<TextView> views = new ArrayList<>();
        for (String sym : m.symbols) {
            TextView c = chip(sym);
            c.setOnClickListener(v -> {
                cfg.symbol = sym;
                if (symbolEdit != null && symbolEdit.length() > 0) symbolEdit.setText("");
                for (TextView o : views) styleChip(o, o == c);
                changed(true);
            });
            views.add(c);
            styleChip(c, sym.equalsIgnoreCase(cfg.symbol));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = px(8);
            symbolRow.addView(c, lp);
        }
    }

    private boolean isPreset(String sym) {
        for (String s : cfg.marketData().symbols) if (s.equalsIgnoreCase(sym)) return true;
        return false;
    }

    private void refreshAll() {
        for (Runnable r : refreshers) r.run();
        if (symbolRow != null) {
            for (int i = 0; i < symbolRow.getChildCount(); i++) {
                TextView c = (TextView) symbolRow.getChildAt(i);
                styleChip(c, c.getText().toString().equalsIgnoreCase(cfg.symbol));
            }
        }
    }

    private static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }

    private void changed(boolean resetSim) {
        cfg.save(this);
        preview.setConfig(cfg, resetSim);
    }

    private void updateLogoState() {
        logoState.setText(cfg.logo != null ? "Logo added." : "No logo? Your initials are used.");
    }

    /* ---------- actions ---------- */
    private void setLiveWallpaper() {
        cfg.save(this);
        try {
            Intent i = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(this, ChartWallpaperService.class));
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
                Toast.makeText(this, "List mein Chartwall choose karo", Toast.LENGTH_LONG).show();
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(this, "Is phone pe live wallpaper ka option nahi mila", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void pickLogo() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        try { startActivityForResult(i, PICK_LOGO); }
        catch (ActivityNotFoundException e) {
            Intent g = new Intent(Intent.ACTION_GET_CONTENT); g.setType("image/*");
            try { startActivityForResult(g, PICK_LOGO); } catch (ActivityNotFoundException ignored) { }
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != PICK_LOGO || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
            int sample = 1;
            while (Math.min(o.outWidth, o.outHeight) / (sample * 2) >= 320) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap src;
            try (InputStream in = getContentResolver().openInputStream(uri)) { src = BitmapFactory.decodeStream(in, null, o2); }
            if (src == null) throw new IllegalStateException("decode");
            int n = 320;
            Bitmap sq = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(sq);
            float sc = Math.max(n / (float) src.getWidth(), n / (float) src.getHeight());
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.setScale(sc, sc);
            m.postTranslate((n - src.getWidth() * sc) / 2f, (n - src.getHeight() * sc) / 2f);
            cv.drawBitmap(src, m, new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
            try (FileOutputStream out = new FileOutputStream(new File(getFilesDir(), Config.LOGO_FILE))) { sq.compress(Bitmap.CompressFormat.PNG, 100, out); }
            cfg.logo = sq;
            updateLogoState();
            changed(false);
        } catch (Throwable e) {
            Toast.makeText(this, "Ye image nahi khul saki. PNG ya JPG try karo.", Toast.LENGTH_LONG).show();
        }
    }

    private void saveStill() {
        if (Build.VERSION.SDK_INT < 29) {
            Toast.makeText(this, "Still image save Android 10+ pe chalta hai. Live wallpaper use karo.", Toast.LENGTH_LONG).show();
            return;
        }
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        int w = Math.min(dm.widthPixels, dm.heightPixels), h = Math.max(dm.widthPixels, dm.heightPixels);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Sim sim = new Sim(cfg);
        sim.step(0);
        new Renderer(this).draw(new Canvas(bmp), w, h, cfg, sim, 0);
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, "chartwall-" + System.currentTimeMillis() + ".png");
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Chartwall");
        try {
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IllegalStateException();
            try (OutputStream out = getContentResolver().openOutputStream(uri)) { bmp.compress(Bitmap.CompressFormat.PNG, 100, out); }
            Toast.makeText(this, "Gallery mein save ho gaya (Pictures/Chartwall)", Toast.LENGTH_LONG).show();
        } catch (Throwable e) {
            Toast.makeText(this, "Save nahi ho saka", Toast.LENGTH_LONG).show();
        }
    }

    /** TextWatcher that waits a moment after typing stops. */
    private final class Watcher implements TextWatcher {
        private final java.util.function.Consumer<String> fn;
        private final Runnable run;
        private String last = "";
        Watcher(java.util.function.Consumer<String> fn) { this.fn = fn; this.run = () -> fn.accept(last); }
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void afterTextChanged(Editable s) {
            last = s.toString();
            preview.removeCallbacks(run);
            preview.postDelayed(run, 300);
        }
    }
}
