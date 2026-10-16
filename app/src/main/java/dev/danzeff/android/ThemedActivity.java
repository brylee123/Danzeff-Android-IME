package dev.danzeff.android;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;

/** One appearance preference for every activity and the keyboard. */
public abstract class ThemedActivity extends AppCompatActivity {
    private boolean appliedDark;

    @Override
    public void onCreate(final Bundle state) {
        appliedDark = KeyboardAppearance.dark(this);
        setTheme(appliedDark ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
        super.onCreate(state);
        getWindow().setStatusBarColor(KeyboardAppearance.background(this));
        getWindow().setNavigationBarColor(KeyboardAppearance.background(this));
        getWindow()
                .getDecorView()
                .setSystemUiVisibility(
                        appliedDark
                                ? 0
                                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                                        | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content),
                (final View view, final WindowInsetsCompat insets) -> {
                    final Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });
    }

    @Override
    public void setContentView(final View view) {
        if (this instanceof DocumentActivity) {
            super.setContentView(view);
            return;
        }
        final LinearLayout frame = new LinearLayout(this);
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setBackgroundColor(KeyboardAppearance.background(this));
        final MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(
                this instanceof MainActivity
                        ? "Danzeff Keyboard"
                        : this instanceof ControllerSettingsActivity
                                ? "Controller profiles"
                                : "Typing & feedback");
        toolbar.setTitleTextColor(AppUi.ink(this));
        if (!(this instanceof MainActivity)) {
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            toolbar.setNavigationIconTint(AppUi.ink(this));
            toolbar.setNavigationContentDescription("Back");
            toolbar.setNavigationOnClickListener((final View v) -> finish());
        }
        frame.addView(toolbar);
        frame.addView(view, new LayoutParams(-1, 0, 1));
        super.setContentView(frame);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appliedDark != KeyboardAppearance.dark(this)) {
            recreate();
        }
    }
}
