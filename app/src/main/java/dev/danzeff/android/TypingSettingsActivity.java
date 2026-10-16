package dev.danzeff.android;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.materialswitch.MaterialSwitch;

/** Only preferences are saved; no dictionary learning or typed-text storage. */
public final class TypingSettingsActivity extends ThemedActivity {
    @Override
    public void onCreate(final Bundle state) {
        super.onCreate(state);
        final ScrollView scroll = new ScrollView(this);
        final LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        final int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        scroll.setBackgroundColor(KeyboardAppearance.background(this));
        setContentView(scroll);
        add(content, "Local English suggestions", "suggestions", true);
        add(content, "Autocorrect on Space (Backspace restores original)", "autocorrect", false);
        add(content, "Smart capitalization", "capitalization", true);
        add(content, "Double-space inserts period", "punctuation", true);
        add(content, "Haptic feedback", "haptics", false);
        final TextView note = new TextView(this);
        note.setText(
                "Suggestions use a small bundled English starter dictionary. Typed words are never"
                    + " saved or uploaded. Passwords, numbers, email, URL and literal fields do not"
                    + " receive automatic text transformations. Haptics follow Android's system"
                    + " setting. Hold delete to repeat; drag Space to move the cursor. Tap Edit for"
                    + " selection, copy, cut and paste.");
        note.setTextColor(KeyboardAppearance.dark(this) ? 0xFFE8EAED : 0xFF202124);
        note.setTypeface(getResources().getFont(R.font.google_sans_regular));
        note.setTextSize(15);
        note.setPadding(0, padding, 0, 0);
        content.addView(note);
    }

    private void add(
            final LinearLayout parent,
            final String label,
            final String key,
            final boolean fallback) {
        final SharedPreferences prefs = getSharedPreferences("danzeff_preferences", 0);
        final MaterialSwitch control = new MaterialSwitch(this);
        control.setText(label);
        control.setTypeface(getResources().getFont(R.font.google_sans_regular));
        control.setTextColor(KeyboardAppearance.dark(this) ? 0xFFE8EAED : 0xFF202124);
        control.setChecked(prefs.getBoolean(key, fallback));
        final int padding = Math.round(14 * getResources().getDisplayMetrics().density);
        control.setPadding(0, padding, 0, padding);
        control.setOnCheckedChangeListener(
                (final CompoundButton button, final boolean checked) ->
                        prefs.edit().putBoolean(key, checked).apply());
        parent.addView(control);
    }
}
