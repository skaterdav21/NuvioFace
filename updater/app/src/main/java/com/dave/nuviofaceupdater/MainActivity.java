package com.dave.nuviofaceupdater;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One screen: what's installed, what's out, and a button to get it. Works with touch and TV remotes. */
public class MainActivity extends Activity implements InstallReceiver.Listener {
    private static final int RED = Color.rgb(229, 9, 20);
    private static final int GREY = Color.rgb(179, 179, 179);

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private TextView installedText;
    private TextView latestText;
    private TextView statusText;
    private TextView notesText;
    private ProgressBar progress;
    private Button updateButton;
    private Button checkButton;
    private Button openButton;
    private Button uninstallButton;

    private Updater.Release latest;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        InstallReceiver.listener = this;
        checkNow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        InstallReceiver.listener = this;
        refreshInstalled();
    }

    @Override
    protected void onDestroy() {
        if (InstallReceiver.listener == this) InstallReceiver.listener = null;
        super.onDestroy();
    }

    // ---------------------------------------------------------------- UI

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.setFillViewport(true);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(28), dp(36), dp(28), dp(28));
        scroll.addView(col);

        TextView brand = text("NUVIO", 44, RED, true);
        brand.setLetterSpacing(0.08f);
        col.addView(brand);
        col.addView(text("Face Updater", 20, Color.WHITE, true));
        col.addView(space(20));

        installedText = text("", 16, Color.WHITE, false);
        latestText = text("", 16, Color.WHITE, false);
        statusText = text("", 15, GREY, false);
        col.addView(installedText);
        col.addView(space(4));
        col.addView(latestText);
        col.addView(space(14));
        col.addView(statusText);
        col.addView(space(10));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        progress.setProgressTintList(ColorStateList.valueOf(RED));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(90, 90, 90)));
        progress.setVisibility(View.GONE);
        col.addView(progress, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8)));
        col.addView(space(18));

        updateButton = button("Update", true);
        updateButton.setOnClickListener(v -> onUpdate());
        checkButton = button("Check again", false);
        checkButton.setOnClickListener(v -> checkNow());
        openButton = button("Open " + Updater.appLabel(this), false);
        openButton.setOnClickListener(v -> openFace());
        uninstallButton = button("Uninstall current " + Updater.appLabel(this), false);
        uninstallButton.setVisibility(View.GONE);
        uninstallButton.setOnClickListener(v -> uninstallFace());

        for (Button b : new Button[]{updateButton, checkButton, openButton, uninstallButton}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
            lp.bottomMargin = dp(10);
            col.addView(b, lp);
        }

        SharedPreferences prefs = getSharedPreferences(UpdateCheckJob.PREFS, MODE_PRIVATE);
        Switch daily = new Switch(this);
        daily.setText("Check every day and notify me");
        daily.setTextColor(Color.WHITE);
        daily.setTextSize(15);
        daily.setPadding(0, dp(10), 0, dp(10));
        daily.setChecked(prefs.getBoolean(UpdateCheckJob.KEY_DAILY, true));
        daily.setOnCheckedChangeListener((v, on) -> {
            prefs.edit().putBoolean(UpdateCheckJob.KEY_DAILY, on).apply();
            UpdateCheckJob.schedule(this, on);
            if (on) askNotificationPermission();
        });
        col.addView(daily);
        UpdateCheckJob.schedule(this, daily.isChecked());
        if (daily.isChecked()) askNotificationPermission();

        col.addView(space(16));
        notesText = text("", 13, GREY, false);
        col.addView(notesText);

        updateButton.requestFocus();
        return scroll;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private View space(int heightDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return v;
    }

    /** Netflix-style buttons: white primary, grey secondary, white outline when the remote focuses them. */
    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(primary ? Color.BLACK : Color.WHITE);
        b.setFocusable(true);
        int fill = primary ? Color.WHITE : Color.argb(180, 109, 109, 110);
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(fill);
        normal.setCornerRadius(dp(4));
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(primary ? Color.WHITE : Color.argb(230, 130, 130, 130));
        focused.setCornerRadius(dp(4));
        focused.setStroke(dp(3), primary ? RED : Color.WHITE);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, normal);
        b.setBackground(states);
        b.setStateListAnimator(null);
        return b;
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }

    // ---------------------------------------------------------------- state

    private void refreshInstalled() {
        String name = Updater.installedVersionName(this);
        installedText.setText(Updater.appLabel(this) + (name == null ? ": not installed" : ": " + name));
        openButton.setVisibility(name == null ? View.GONE : View.VISIBLE);
        updateButtonState();
    }

    private void updateButtonState() {
        long installed = Updater.installedVersionCode(this);
        if (latest == null) {
            updateButton.setEnabled(false);
            updateButton.setText("Update");
        } else if (installed < 0) {
            updateButton.setEnabled(!busy);
            updateButton.setText("Install " + Updater.appLabel(this));
        } else if (Updater.isNewer(latest, installed)) {
            updateButton.setEnabled(!busy);
            updateButton.setText("Update to " + shortName(latest));
        } else {
            updateButton.setEnabled(!busy);
            updateButton.setText("Reinstall");
        }
        checkButton.setEnabled(!busy);
    }

    private static String shortName(Updater.Release r) {
        return r.versionName != null ? r.versionName : r.tag;
    }

    private void setStatus(String s) {
        statusText.setText(s);
    }

    private void checkNow() {
        if (busy) return;
        busy = true;
        refreshInstalled();
        setStatus("Checking GitHub for the latest " + Updater.appLabel(this) + "…");
        latestText.setText("Latest: …");
        worker.execute(() -> {
            try {
                Updater.Release r = Updater.fetchLatest(this);
                main.post(() -> {
                    busy = false;
                    latest = r;
                    latestText.setText("Latest: " + shortName(r) + "  (" + Updater.abiFlavor() + ")");
                    notesText.setText(r.notes);
                    long installed = Updater.installedVersionCode(this);
                    setStatus(installed >= 0 && !Updater.isNewer(r, installed)
                            ? "You're up to date."
                            : "A new build is ready.");
                    refreshInstalled();
                });
            } catch (Exception e) {
                main.post(() -> {
                    busy = false;
                    latestText.setText("Latest: unknown");
                    setStatus("Couldn't check: " + e.getMessage());
                    refreshInstalled();
                });
            }
        });
    }

    private void onUpdate() {
        if (latest == null || busy) return;
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            setStatus("Allow \"Install unknown apps\" for Nuvio Face Updater, then come back and press Update.");
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
                startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            }
            return;
        }
        busy = true;
        uninstallButton.setVisibility(View.GONE);
        updateButtonState();
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        setStatus("Downloading " + latest.assetName + "…");
        final Updater.Release r = latest;
        worker.execute(() -> {
            try {
                File apk = Updater.download(this, r, (done, total) -> main.post(() -> {
                    if (total > 0) {
                        progress.setProgress((int) (done * 1000 / total));
                        setStatus(String.format(Locale.US, "Downloading… %.1f of %.1f MB  (%d%%)",
                                done / 1048576.0, total / 1048576.0, done * 100 / total));
                    } else {
                        setStatus(String.format(Locale.US, "Downloading… %.1f MB", done / 1048576.0));
                    }
                }));
                main.post(() -> setStatus("Installing… confirm on screen if Android asks."));
                Updater.install(this, apk);
            } catch (Exception e) {
                main.post(() -> {
                    busy = false;
                    progress.setVisibility(View.GONE);
                    setStatus("Update failed: " + e.getMessage());
                    updateButtonState();
                });
            }
        });
    }

    @Override
    public void onInstallResult(int status, String message) {
        main.post(() -> {
            busy = false;
            progress.setVisibility(View.GONE);
            if (status == PackageInstaller.STATUS_SUCCESS) {
                setStatus(Updater.appLabel(this) + " is up to date.");
            } else if (status == PackageInstaller.STATUS_FAILURE_CONFLICT
                    || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE) {
                setStatus("This build is signed with a different key than the Nuvio Face on this device. "
                        + "Uninstall it once, then press Install.");
                uninstallButton.setVisibility(View.VISIBLE);
                uninstallButton.requestFocus();
            } else if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
                setStatus("Install cancelled.");
            } else {
                setStatus("Install failed" + (message != null ? ": " + message : "."));
            }
            refreshInstalled();
        });
    }

    private void openFace() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(Updater.facePackage(this));
        if (launch == null) launch = getPackageManager().getLeanbackLaunchIntentForPackage(Updater.facePackage(this));
        if (launch != null) startActivity(launch);
    }

    @SuppressWarnings("deprecation")
    private void uninstallFace() {
        startActivity(new Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + Updater.facePackage(this))));
    }
}
