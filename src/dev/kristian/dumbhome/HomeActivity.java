package dev.kristian.dumbhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Random;

/**
 * Two-tile HOME screen: Apple TV and Gaming PC, a settings gear, and a
 * "Restore Google TV" checklist for undoing the launcher swap.
 *
 * Extras: live input status, time-of-day backdrop, idle dim with a drifting
 * clock, focus on the last-used input, and Back returns to that input.
 */
public class HomeActivity extends Activity {
    private static final int AMBER = 0xFFFFB347;
    private static final int IDLE_FG = 0xFFD5D8DE;
    private static final int BASE_BG = 0xFF07080B;
    private static final int GLOW_NIGHT = 0xFF3A2410;
    private static final int GLOW_DAY = 0xFF12233A;
    private static final long IDLE_AFTER_MS = 2 * 60 * 1000;
    private static final long TICK_MS = 15 * 1000;
    private static final float IDLE_ALPHA = 0.4f;
    private static final float BOUNCE_SPEED = 7f; // screen-width % per second
    private static final int[] BOUNCE_COLORS = {AMBER, 0xFF8FD3FF, 0xFF7DFFC4, 0xFFFF8FB1, 0xFFC9A7FF, Color.WHITE};

    private static final String GOOGLE_HOME = "com.google.android.apps.tv.launcherx";
    private static final String GOOGLE_SETUP = "com.google.android.tungsten.setupwraith";

    // Hardware input IDs don't match port numbers on this TV: HW2 is HDMI 1, HW3 is HDMI 2.
    private static final Tile[] TILES = {
        new Tile("Apple TV", "HDMI 2", "com.vt.source.external/.hdmi.HdmiTvInputService/HW3", R.drawable.ic_television_simple),
        new Tile("Gaming PC", "HDMI 1", "com.vt.source.external/.hdmi.HdmiTvInputService/HW2", R.drawable.ic_game_controller),
    };

    private static final class Tile {
        final String name, port, inputId;
        final int icon;
        View view;
        ImageView iconView;
        TextView nameView, statusView;
        View dot;

        Tile(String name, String port, String inputId, int icon) {
            this.name = name;
            this.port = port;
            this.inputId = inputId;
            this.icon = icon;
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private SharedPreferences prefs;
    private TvInputManager tim;
    private float u; // 1% of screen width, like CSS cqw

    private FrameLayout root;
    private GradientDrawable backdrop;
    private View content;
    private LinearLayout idleBadge;
    private TextClock idleTime, idleDate;
    private float bx, by, vx, vy;
    private long lastFrame;
    private int colorIndex;
    private View restorePanel;
    private TextView[] restoreSteps;
    private ImageView gear;
    private long lastInteraction;
    private boolean idle;

    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputStateChanged(String inputId, int state) {
            refreshStatus();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateBackdrop();
            if (!idle && restorePanel.getVisibility() != View.VISIBLE
                    && SystemClock.uptimeMillis() - lastInteraction > IDLE_AFTER_MS) {
                enterIdle();
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    // DVD-logo bounce: constant velocity, reflect off edges, new color per bounce.
    private final Runnable bounce = new Runnable() {
        @Override
        public void run() {
            if (!idle) return;
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
            lastFrame = now;
            float maxX = root.getWidth() - idleBadge.getWidth();
            float maxY = root.getHeight() - idleBadge.getHeight();
            if (idleBadge.getWidth() > 0) {
                bx += vx * dt;
                by += vy * dt;
                boolean hitX = false, hitY = false;
                if (bx <= 0 || bx >= maxX) {
                    bx = Math.max(0, Math.min(bx, maxX));
                    vx = -vx;
                    hitX = true;
                }
                if (by <= 0 || by >= maxY) {
                    by = Math.max(0, Math.min(by, maxY));
                    vy = -vy;
                    hitY = true;
                }
                if (hitX || hitY) onBounce(hitX && hitY);
                idleBadge.setTranslationX(bx);
                idleBadge.setTranslationY(by);
            }
            idleBadge.postOnAnimation(this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        u = getResources().getDisplayMetrics().widthPixels / 100f;

        root = new FrameLayout(this);
        backdrop = new GradientDrawable();
        backdrop.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        backdrop.setGradientCenter(0.5f, 1.15f);
        backdrop.setGradientRadius(getResources().getDisplayMetrics().heightPixels * 0.85f);
        root.setBackground(backdrop);

        content = buildContent();
        root.addView(content, match());

        idleBadge = new LinearLayout(this);
        idleBadge.setOrientation(LinearLayout.VERTICAL);
        idleBadge.setGravity(Gravity.CENTER_HORIZONTAL);
        idleBadge.setPadding(px(1.5f), px(1f), px(1.5f), px(1f));
        idleTime = clock(6, 1f);
        idleBadge.addView(idleTime);
        idleDate = clock(1.5f, 0.8f);
        idleDate.setFormat12Hour("EEE, MMM d");
        idleDate.setFormat24Hour("EEE, MMM d");
        idleDate.setLetterSpacing(0.08f);
        idleBadge.addView(idleDate);
        idleBadge.setVisibility(View.GONE);
        root.addView(idleBadge, new FrameLayout.LayoutParams(wrap(), wrap()));

        restorePanel = buildRestorePanel();
        restorePanel.setVisibility(View.GONE);
        root.addView(restorePanel, match());

        setContentView(root);
        updateBackdrop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tim != null) tim.registerCallback(inputCallback, handler);
        exitIdle();
        refreshStatus();
        refreshRestoreSteps();
        focusLastTile();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (tim != null) tim.unregisterCallback(inputCallback);
        handler.removeCallbacks(tick);
        exitIdle();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        lastInteraction = SystemClock.uptimeMillis();
        if (idle) {
            // The first press only wakes the screen.
            if (event.getAction() == KeyEvent.ACTION_UP) exitIdle();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (restorePanel.getVisibility() == View.VISIBLE) {
            restorePanel.setVisibility(View.GONE);
            content.setVisibility(View.VISIBLE);
            focusLastTile();
            return;
        }
        String last = prefs.getString("last", null);
        if (last != null) openInput(last);
    }

    // --- Layout ---

    private View buildContent() {
        FrameLayout f = new FrameLayout(this);

        TextClock clock = clock(2.4f, 0.85f);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(wrap(), wrap(), Gravity.TOP | Gravity.START);
        clp.setMargins(px(4.5f), px(3.0f), 0, 0);
        f.addView(clock, clp);

        gear = new ImageView(this);
        gear.setImageResource(R.drawable.ic_gear_six);
        gear.setFocusable(true);
        gear.setPadding(px(0.6f), px(0.6f), px(0.6f), px(0.6f));
        gear.setOnClickListener(v -> launch(new Intent(Settings.ACTION_SETTINGS)));
        gear.setOnFocusChangeListener((v, on) -> styleGear(on));
        styleGear(false);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(px(4f), px(4f), Gravity.TOP | Gravity.END);
        glp.setMargins(0, px(3.0f), px(4.0f), 0);
        f.addView(gear, glp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        // The focused tile scales up past its bounds.
        row.setClipChildren(false);
        f.setClipChildren(false);
        for (int i = 0; i < TILES.length; i++) {
            View tile = buildTile(TILES[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(24), px(17));
            if (i > 0) lp.leftMargin = px(3.5f);
            row.addView(tile, lp);
        }
        f.addView(row, new FrameLayout.LayoutParams(wrap(), wrap(), Gravity.CENTER));

        TextView restore = text("Restore Google TV", 1.1f, IDLE_FG);
        restore.setAlpha(0.3f);
        restore.setFocusable(true);
        restore.setPadding(px(1.2f), px(0.6f), px(1.2f), px(0.6f));
        restore.setOnFocusChangeListener((v, on) -> {
            restore.setTextColor(on ? AMBER : IDLE_FG);
            restore.setAlpha(on ? 1f : 0.3f);
        });
        restore.setOnClickListener(v -> showRestorePanel());
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(wrap(), wrap(), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        rlp.bottomMargin = px(2.5f);
        f.addView(restore, rlp);

        return f;
    }

    private View buildTile(Tile t) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setOutlineAmbientShadowColor(AMBER);
        tile.setOutlineSpotShadowColor(AMBER);

        t.iconView = new ImageView(this);
        t.iconView.setImageResource(t.icon);
        tile.addView(t.iconView, new LinearLayout.LayoutParams(px(6.2f), px(6.2f)));

        t.nameView = text(t.name, 1.9f, Color.WHITE);
        t.nameView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(wrap(), wrap());
        nlp.topMargin = px(1.1f);
        tile.addView(t.nameView, nlp);

        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        t.dot = new View(this);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        t.dot.setBackground(dot);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(px(0.7f), px(0.7f));
        dlp.rightMargin = px(0.6f);
        status.addView(t.dot, dlp);
        t.statusView = text(t.port, 1.1f, Color.WHITE);
        t.statusView.setAlpha(0.45f);
        status.addView(t.statusView);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(wrap(), wrap());
        slp.topMargin = px(0.6f);
        tile.addView(status, slp);

        tile.setOnClickListener(v -> {
            prefs.edit().putString("last", t.inputId).apply();
            openInput(t.inputId);
        });
        tile.setOnFocusChangeListener((v, on) -> styleTile(t, on));
        t.view = tile;
        styleTile(t, false);
        return tile;
    }

    private View buildRestorePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(px(18), 0, px(18), 0);
        panel.setBackgroundColor(0xF207080B);

        panel.addView(text("Restore Google TV", 3f, Color.WHITE));
        TextView hint = text("Do these in order. Each one opens a system screen. Press Enable or Uninstall there, then Back.", 1.3f, Color.WHITE);
        hint.setAlpha(0.55f);
        hint.setPadding(0, px(0.8f), 0, px(2.4f));
        panel.addView(hint);

        restoreSteps = new TextView[3];
        restoreSteps[0] = restoreStep(panel, v -> openAppInfo(GOOGLE_HOME));
        restoreSteps[1] = restoreStep(panel, v -> openAppInfo(GOOGLE_SETUP));
        restoreSteps[2] = restoreStep(panel, v -> {
            if (isEnabled(GOOGLE_HOME) && isEnabled(GOOGLE_SETUP)) {
                launch(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + getPackageName())));
            } else {
                Toast.makeText(this, "Do steps 1 and 2 first, so the TV still has a Home screen.", Toast.LENGTH_LONG).show();
            }
        });

        TextView sell = text("Selling the TV? Settings → System → Reset → Factory reset does all of this and also removes your accounts.", 1.1f, Color.WHITE);
        sell.setAlpha(0.4f);
        sell.setPadding(0, px(2.4f), 0, 0);
        panel.addView(sell);
        return panel;
    }

    private TextView restoreStep(LinearLayout panel, View.OnClickListener onClick) {
        TextView step = text("", 1.8f, IDLE_FG);
        step.setFocusable(true);
        step.setPadding(px(1.4f), px(0.9f), px(1.4f), px(0.9f));
        step.setOnClickListener(onClick);
        step.setOnFocusChangeListener((v, on) -> {
            step.setBackgroundColor(on ? 0x22FFFFFF : Color.TRANSPARENT);
            step.setTextColor(on ? AMBER : IDLE_FG);
        });
        panel.addView(step);
        return step;
    }

    // --- State ---

    private void styleTile(Tile t, boolean on) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(u * 1.8f);
        bg.setColor(on ? 0x21FFFFFF : 0x0BFFFFFF);
        bg.setStroke(Math.max(2, px(0.18f)), on ? AMBER : 0x12FFFFFF);
        t.view.setBackground(bg);
        t.view.animate().scaleX(on ? 1.08f : 1f).scaleY(on ? 1.08f : 1f).setDuration(180).start();
        t.view.setElevation(on ? u * 1.6f : 0);
        t.iconView.setColorFilter(on ? AMBER : IDLE_FG);
        t.nameView.setAlpha(on ? 1f : 0.75f);
        boolean asleep = inputState(t.inputId) != TvInputManager.INPUT_STATE_CONNECTED;
        t.view.setAlpha(asleep && !on ? 0.55f : 1f);
    }

    private void styleGear(boolean on) {
        gear.setColorFilter(on ? AMBER : Color.WHITE);
        gear.setAlpha(on ? 1f : 0.45f);
    }

    private void refreshStatus() {
        for (Tile t : TILES) {
            int state = inputState(t.inputId);
            String label;
            int dot;
            if (state == TvInputManager.INPUT_STATE_CONNECTED) {
                label = "Connected";
                dot = 0xFF4CD964;
            } else if (state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY) {
                label = "Standby";
                dot = 0xFF666666;
            } else {
                label = "No signal";
                dot = 0xFF444444;
            }
            t.statusView.setText(t.port + " · " + label);
            ((GradientDrawable) t.dot.getBackground()).setColor(dot);
            styleTile(t, t.view.hasFocus());
        }
    }

    private void refreshRestoreSteps() {
        boolean home = isEnabled(GOOGLE_HOME), setup = isEnabled(GOOGLE_SETUP);
        restoreSteps[0].setText((home ? "✓  " : "1  ") + "Turn on Google TV Home");
        restoreSteps[1].setText((setup ? "✓  " : "2  ") + "Turn on Google TV setup");
        restoreSteps[2].setText("3  Remove this launcher");
        restoreSteps[2].setAlpha(home && setup ? 1f : 0.35f);
    }

    private void showRestorePanel() {
        refreshRestoreSteps();
        content.setVisibility(View.GONE);
        restorePanel.setVisibility(View.VISIBLE);
        restoreSteps[!isEnabled(GOOGLE_HOME) ? 0 : !isEnabled(GOOGLE_SETUP) ? 1 : 2].requestFocus();
    }

    private void focusLastTile() {
        if (restorePanel.getVisibility() == View.VISIBLE) return;
        String last = prefs.getString("last", null);
        for (Tile t : TILES) {
            if (t.inputId.equals(last)) {
                t.view.requestFocus();
                return;
            }
        }
        TILES[0].view.requestFocus();
    }

    private void updateBackdrop() {
        Calendar now = Calendar.getInstance();
        float h = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60f;
        // 1 = warm night glow, 0 = cool day glow, with 3-hour ramps at dawn and dusk.
        float warm;
        if (h >= 9 && h < 17) warm = 0;
        else if (h >= 6 && h < 9) warm = 1 - (h - 6) / 3;
        else if (h >= 17 && h < 20) warm = (h - 17) / 3;
        else warm = 1;
        backdrop.setColors(new int[] {lerp(GLOW_DAY, GLOW_NIGHT, warm), BASE_BG});
    }

    private void enterIdle() {
        idle = true;
        content.animate().alpha(0f).setDuration(1200).start();

        bx = random.nextInt(px(50));
        by = random.nextInt(px(25));
        vx = (random.nextBoolean() ? 1 : -1) * u * BOUNCE_SPEED;
        vy = (random.nextBoolean() ? 1 : -1) * u * BOUNCE_SPEED;
        colorIndex = random.nextInt(BOUNCE_COLORS.length);
        tintBadge(BOUNCE_COLORS[colorIndex]);
        idleBadge.setTranslationX(bx);
        idleBadge.setTranslationY(by);
        idleBadge.setAlpha(0f);
        idleBadge.setVisibility(View.VISIBLE);
        idleBadge.animate().alpha(IDLE_ALPHA).setDuration(1200).start();
        lastFrame = SystemClock.uptimeMillis();
        idleBadge.postOnAnimation(bounce);
    }

    private void exitIdle() {
        idle = false;
        lastInteraction = SystemClock.uptimeMillis();
        content.animate().cancel();
        content.setAlpha(1f);
        idleBadge.removeCallbacks(bounce);
        idleBadge.animate().cancel();
        idleBadge.setVisibility(View.GONE);
    }

    private void onBounce(boolean corner) {
        colorIndex = (colorIndex + 1 + random.nextInt(BOUNCE_COLORS.length - 1)) % BOUNCE_COLORS.length;
        tintBadge(BOUNCE_COLORS[colorIndex]);
        if (corner) {
            // The moment everyone waits for.
            idleBadge.animate().alpha(1f).scaleX(1.15f).scaleY(1.15f).setDuration(250)
                    .withEndAction(() -> idleBadge.animate().alpha(IDLE_ALPHA).scaleX(1f).scaleY(1f).setDuration(900).start())
                    .start();
        }
    }

    private void tintBadge(int color) {
        idleTime.setTextColor(color);
        idleDate.setTextColor(color);
    }

    // --- Actions ---

    private void openInput(String inputId) {
        // Hisense's Live TV app only matches VIEW when the channel type is explicit.
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        launch(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, TvContract.Channels.CONTENT_ITEM_TYPE));
    }

    private void openAppInfo(String pkg) {
        launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
    }

    private void launch(Intent intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Can't open: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // --- Helpers ---

    private int inputState(String inputId) {
        try {
            return tim == null ? TvInputManager.INPUT_STATE_DISCONNECTED : tim.getInputState(inputId);
        } catch (IllegalArgumentException e) {
            return TvInputManager.INPUT_STATE_DISCONNECTED;
        }
    }

    private boolean isEnabled(String pkg) {
        try {
            int s = getPackageManager().getApplicationEnabledSetting(pkg);
            return s == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT || s == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private TextClock clock(float size, float alpha) {
        TextClock c = new TextClock(this);
        c.setFormat12Hour("h:mm");
        c.setFormat24Hour("H:mm");
        c.setTextColor(Color.WHITE);
        c.setAlpha(alpha);
        c.setTextSize(TypedValue.COMPLEX_UNIT_PX, u * size);
        c.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        return c;
    }

    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, u * size);
        return t;
    }

    private static int lerp(int a, int b, float f) {
        int r = (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * f);
        int g = (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * f);
        int bl = (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f);
        return Color.rgb(r, g, bl);
    }

    private int px(float units) {
        return Math.round(u * units);
    }

    private static int wrap() {
        return FrameLayout.LayoutParams.WRAP_CONTENT;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }
}
