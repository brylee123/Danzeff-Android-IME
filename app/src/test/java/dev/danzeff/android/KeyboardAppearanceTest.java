package dev.danzeff.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "notnight-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class KeyboardAppearanceTest {
    private Context context;

    @Before
    public void reset() {
        RuntimeEnvironment.setQualifiers("notnight-xhdpi");
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("danzeff_preferences", 0).edit().clear().commit();
    }

    @Test
    public void systemTracksNightModeAndExplicitModesOverrideIt() {
        assertEquals(0, KeyboardAppearance.selected(context));
        assertFalse(KeyboardAppearance.dark(context));
        RuntimeEnvironment.setQualifiers("night-xhdpi");
        assertTrue(KeyboardAppearance.dark(context));
        KeyboardAppearance.select(context, 1);
        assertFalse(KeyboardAppearance.dark(context));
        RuntimeEnvironment.setQualifiers("notnight-xhdpi");
        KeyboardAppearance.select(context, 2);
        assertTrue(KeyboardAppearance.dark(context));
        KeyboardAppearance.select(context, 0);
        assertFalse(KeyboardAppearance.dark(context));
    }

    @Test
    public void bothThemesRenderWithRoundedSolidKeysAndBundledTypeface() throws Exception {
        assertNotNull(context.getResources().getFont(R.font.google_sans_regular));
        for (int mode = 1; mode <= 2; mode++) {
            KeyboardAppearance.select(context, mode);
            final DanzeffKeyboardView view =
                    new DanzeffKeyboardView(
                            context,
                            new DanzeffKeyboardView.Listener() {
                                public void onChooseGroup(final int r, final int c) {}

                                public void onChooseSlot(final int s) {}

                                public void onBackspace() {}

                                public void onSpace() {}

                                public void onEnter() {}

                                public void onShift() {}

                                public void onNumbers() {}

                                public void onSwitchKeyboard() {}
                            });
            view.measure(
                    View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(592, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, 720, 592);
            final Bitmap image = Bitmap.createBitmap(720, 592, Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(image));
            assertEquals(KeyboardAppearance.background(context), image.getPixel(0, 0));
            assertEquals(
                    "Rounded corner must expose background",
                    image.getPixel(0, 0),
                    image.getPixel(23, 105));
            final File file =
                    new File("build/reports/keyboard-" + (mode == 1 ? "light" : "dark") + ".png");
            file.getParentFile().mkdirs();
            try (final FileOutputStream out = new FileOutputStream(file)) {
                image.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        }
    }
}
