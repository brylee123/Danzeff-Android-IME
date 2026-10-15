package dev.danzeff.android;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/** Only preferences are saved; no dictionary learning or typed-text storage. */
public final class TypingSettingsActivity extends ThemedActivity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding); scroll.addView(content);
        scroll.setBackgroundColor(KeyboardAppearance.background(this)); setContentView(scroll);
        add(content, "Local English suggestions", "suggestions", true);
        add(content, "Autocorrect on Space (Backspace restores original)", "autocorrect", false);
        add(content, "Smart capitalization", "capitalization", true);
        add(content, "Double-space inserts period", "punctuation", true);
        add(content, "Haptic feedback", "haptics", false);
        TextView note = new TextView(this);
        note.setText("Suggestions use a small bundled English starter dictionary. Typed words are never saved or uploaded. Passwords, numbers, email, URL and literal fields do not receive automatic text transformations. Haptics follow Android's system setting. Hold delete to repeat; drag Space to move the cursor. Tap Edit for selection, copy, cut and paste.");
        note.setTextColor(KeyboardAppearance.dark(this) ? 0xFFE8EAED : 0xFF202124);
        note.setTypeface(getResources().getFont(R.font.google_sans_regular)); note.setTextSize(15); note.setPadding(0, padding, 0, 0); content.addView(note);
    }
    private void add(LinearLayout parent, String label, String key, boolean fallback) {
        var prefs = getSharedPreferences("danzeff_preferences", 0);
        com.google.android.material.materialswitch.MaterialSwitch control = new com.google.android.material.materialswitch.MaterialSwitch(this); control.setText(label); control.setTypeface(getResources().getFont(R.font.google_sans_regular));
        control.setTextColor(KeyboardAppearance.dark(this) ? 0xFFE8EAED : 0xFF202124);
        control.setChecked(prefs.getBoolean(key, fallback));
        int padding = Math.round(14 * getResources().getDisplayMetrics().density);
        control.setPadding(0, padding, 0, padding);
        control.setOnCheckedChangeListener((button, checked) -> prefs.edit().putBoolean(key, checked).apply());
        parent.addView(control);
    }
}
