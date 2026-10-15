package dev.danzeff.android;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** One appearance preference for every activity and the keyboard. */
public abstract class ThemedActivity extends AppCompatActivity {
    private boolean appliedDark;
    @Override public void onCreate(Bundle state) {
        appliedDark = KeyboardAppearance.dark(this);
        setTheme(appliedDark ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
        super.onCreate(state);
        getWindow().setStatusBarColor(KeyboardAppearance.background(this));
        getWindow().setNavigationBarColor(KeyboardAppearance.background(this));
        getWindow().getDecorView().setSystemUiVisibility(appliedDark ? 0 :
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (view, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }
    @Override public void setContentView(View view) {
        if (this instanceof DocumentActivity) { super.setContentView(view); return; }
        android.widget.LinearLayout frame = new android.widget.LinearLayout(this);
        frame.setOrientation(android.widget.LinearLayout.VERTICAL);
        frame.setBackgroundColor(KeyboardAppearance.background(this));
        com.google.android.material.appbar.MaterialToolbar toolbar = new com.google.android.material.appbar.MaterialToolbar(this);
        toolbar.setTitle(this instanceof MainActivity ? "Danzeff Keyboard" :
                this instanceof ControllerSettingsActivity ? "Controller profiles" : "Typing & feedback");
        toolbar.setTitleTextColor(AppUi.ink(this));
        if (!(this instanceof MainActivity)) {
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            toolbar.setNavigationIconTint(AppUi.ink(this)); toolbar.setNavigationContentDescription("Back");
            toolbar.setNavigationOnClickListener(v -> finish());
        }
        frame.addView(toolbar); frame.addView(view, new android.widget.LinearLayout.LayoutParams(-1, 0, 1));
        super.setContentView(frame);
    }
    @Override protected void onResume() {
        super.onResume();
        if (appliedDark != KeyboardAppearance.dark(this)) recreate();
    }
}
