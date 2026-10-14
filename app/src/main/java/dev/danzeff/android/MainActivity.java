package dev.danzeff.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** Quick onboarding and in-app WPM/accuracy experiment. No test text is saved. */
public class MainActivity extends Activity {
    private static final int BACKGROUND = 0xFF0D1721;
    private static final int PANEL = 0xFF182935;
    private static final int AQUA = 0xFF55D6C2;
    private static final int LIGHT = 0xFFF2F7F9;
    private static final int SECONDARY = 0xFFB2C2CC;
    private static final String TARGET = "The quick brown fox jumps over the lazy dog.";

    private TextView status;
    private TextView metrics;
    private EditText practice;
    private long startedAt = -1;
    private long completedAt = -1;
    private int observedDeletions = 0;
    private String previousText = "";

    private int dp(float p) { return (int) (p * getResources().getDisplayMetrics().density + 0.5f); }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radius));
        return bg;
    }

    private TextView text(String s, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private void add(LinearLayout parent, View v, int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(bottomMargin);
        parent.addView(v, params);
    }

    private Button button(String caption) {
        Button b = new Button(this);
        b.setText(caption);
        b.setAllCaps(false);
        b.setTextColor(BACKGROUND);
        b.setTextSize(14);
        b.setBackground(shape(AQUA, 11));
        b.setPadding(dp(10), dp(12), dp(10), dp(12));
        return b;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(22), dp(28), dp(22), dp(30));
        scroll.addView(layout);
        setContentView(scroll);

        add(layout, text("DANZEFF / ANDROID", 26, AQUA, true), 8);
        add(layout, text("A classic PSP input method, adapted for Android touchscreens and gamepads.",
                15, SECONDARY, false), 25);
        status = text("Checking keyboard status…", 14, LIGHT, true);
        add(layout, status, 18);

        Button enable = button("1  Enable keyboard in Android Settings");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        add(layout, enable, 9);

        Button select = button("2  Choose Danzeff as the current keyboard");
        select.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        add(layout, select, 21);

        add(layout, text("TYPING EXPERIMENT", 15, AQUA, true), 8);
        TextView target = text(TARGET, 17, LIGHT, false);
        target.setPadding(dp(14), dp(14), dp(14), dp(14));
        target.setBackground(shape(PANEL, 9));
        add(layout, target, 11);

        practice = new EditText(this);
        practice.setHint("Type the sentence above here…");
        practice.setTextColor(LIGHT);
        practice.setHintTextColor(SECONDARY);
        practice.setTextSize(17);
        practice.setMinLines(2);
        practice.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        practice.setPadding(dp(12), dp(12), dp(12), dp(12));
        practice.setBackground(shape(PANEL, 9));
        add(layout, practice, 8);
        metrics = text("Start typing to measure speed and accuracy.", 14, SECONDARY, false);
        add(layout, metrics, 12);

        Button reset = button("Reset typing test");
        reset.setOnClickListener(v -> {
            startedAt = -1;
            completedAt = -1;
            observedDeletions = 0;
            previousText = "";
            practice.setText("");
            metrics.setText("Start typing to measure speed and accuracy.");
            practice.requestFocus();
        });
        add(layout, reset, 16);

        add(layout, text("TWO-STEP: Select a 3×3 cell on the left, then tap the matching top, left, bottom, or right face button. You can hold the grid with one thumb while tapping a face button with the other.\n\nDIRECT TAP: Tap a quadrant of a cell to enter its character with one touch. Toggle modes using the keyboard header.\n\nGAMEPAD: Left stick or D-pad selects a cell; Y/X/A/B selects top/left/bottom/right. L1 switches letters/numbers, R1 shifts.",
                13, SECONDARY, false), 14);
        add(layout, text("Offline prototype. No internet permission, no typed-text logging, no predictions, and no autocorrect. Avoid entering sensitive credentials until you have personally reviewed the code.",
                12, SECONDARY, false), 0);

        practice.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String current = s.toString();
                if (current.length() < previousText.length()) observedDeletions += previousText.length() - current.length();
                previousText = current;
                if (startedAt < 0 && !current.isEmpty()) startedAt = SystemClock.elapsedRealtime();
                if (completedAt < 0 && TARGET.equals(current)) completedAt = SystemClock.elapsedRealtime();
                refreshMetrics(current);
            }
            @Override public void afterTextChanged(Editable e) {}
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (status == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        boolean enabled = false;
        if (imm != null) {
            List<InputMethodInfo> list = imm.getEnabledInputMethodList();
            for (InputMethodInfo info : list) {
                if (getPackageName().equals(info.getPackageName())) { enabled = true; break; }
            }
        }
        String selectedIme = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean active = selectedIme != null && selectedIme.startsWith(getPackageName() + "/");
        status.setText(active ? "Status: active Android keyboard ✓" :
                enabled ? "Status: enabled — choose Danzeff as current keyboard" :
                          "Status: not yet enabled");
    }

    private void refreshMetrics(String current) {
        if (startedAt < 0) return;
        long end = completedAt >= 0 ? completedAt : SystemClock.elapsedRealtime();
        long elapsed = Math.max(1, end - startedAt);
        double mins = elapsed / 60000.0;
        double wpm = (current.length() / 5.0) / mins;
        // Compare the text actually typed to the corresponding target prefix.
        String expected = TARGET.substring(0, Math.min(TARGET.length(), current.length()));
        int distance = levenshtein(current, expected);
        int denominator = Math.max(1, Math.max(current.length(), expected.length()));
        double accuracy = 100.0 * (1.0 - (double) distance / denominator);
        metrics.setText(String.format(Locale.US,
                "%s  •  %.1f s  •  %.1f WPM  •  %.0f%% prefix accuracy  •  %d deletions",
                completedAt >= 0 ? "Complete ✓" : "In progress",
                elapsed / 1000.0, wpm, accuracy, observedDeletions));
    }

    private static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] cur = new int[b.length() + 1];
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            }
            prev = cur;
        }
        return prev[b.length()];
    }
}
