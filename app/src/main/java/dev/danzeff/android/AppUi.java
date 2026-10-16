package dev.danzeff.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.ContextThemeWrapper;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

/** Shared solid Material surfaces, typography and controls. */
public final class AppUi {
    private AppUi() {}

    public static int ink(final Context c) {
        return KeyboardAppearance.dark(c) ? 0xFFE8EAED : 0xFF202124;
    }

    public static int secondary(final Context c) {
        return KeyboardAppearance.dark(c) ? 0xFFC4C7C5 : 0xFF44474B;
    }

    public static int panel(final Context c) {
        return KeyboardAppearance.dark(c) ? 0xFF3C4043 : 0xFFDEE1E6;
    }

    public static int accent(final Context c) {
        return KeyboardAppearance.dark(c) ? 0xFFF3C35A : 0xFF735500;
    }

    public static Context themed(final Context c) {
        return new ContextThemeWrapper(
                c, KeyboardAppearance.dark(c) ? R.style.AppTheme_Dark : R.style.AppTheme_Light);
    }

    public static MaterialButton button(final Context c, final String label) {
        final MaterialButton b =
                new MaterialButton(
                        themed(c),
                        null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(c.getResources().getFont(R.font.google_sans_medium));
        b.setBackgroundTintList(ColorStateList.valueOf(panel(c)));
        b.setTextColor(ink(c));
        b.setStrokeWidth(0);
        b.setCornerRadius(Math.round(24 * c.getResources().getDisplayMetrics().density));
        b.setMinHeight(Math.round(48 * c.getResources().getDisplayMetrics().density));
        return b;
    }

    public static TextInputLayout input(final Context c, final String hint) {
        final TextInputLayout box =
                new TextInputLayout(
                        c, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        box.setHint(hint);
        box.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        final int pad = Math.round(16 * c.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, pad / 2);
        return box;
    }
}
