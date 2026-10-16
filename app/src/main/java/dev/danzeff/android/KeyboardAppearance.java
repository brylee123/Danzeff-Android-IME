package dev.danzeff.android;

import android.content.Context;
import android.content.res.Configuration;

/** Shared, persisted keyboard appearance. System is the default. */
public final class KeyboardAppearance {
    public static final String KEY = "appearance";
    public static final String[] MODES = {"system", "light", "dark"};
    public static final String[] LABELS = {"System (auto)", "Light", "Dark"};

    private KeyboardAppearance() {}

    public static int selected(final Context context) {
        final String mode =
                context.getSharedPreferences("danzeff_preferences", 0).getString(KEY, "system");
        for (int i = 0; i < MODES.length; i++) {
            if (MODES[i].equals(mode)) {
                return i;
            }
        }
        return 0;
    }

    public static void select(final Context context, final int index) {
        if (index < 0 || index >= MODES.length) {
            throw new IllegalArgumentException("Unknown appearance");
        }
        context.getSharedPreferences("danzeff_preferences", 0)
                .edit()
                .putString(KEY, MODES[index])
                .apply();
    }

    public static boolean dark(final Context context) {
        final int mode = selected(context);
        return mode == 2
                || mode == 0
                        && (context.getResources().getConfiguration().uiMode
                                        & Configuration.UI_MODE_NIGHT_MASK)
                                == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int background(final Context context) {
        return dark(context) ? 0xFF202124 : 0xFFF1F3F4;
    }
}
