package dev.danzeff.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.text.Spanned;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import io.noties.markwon.core.spans.StrongEmphasisSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class MaterialUiTest {
    @Test
    public void screensAndDocumentsRenderInBothModes() throws Exception {
        final Application app = RuntimeEnvironment.getApplication();
        for (final int mode : new int[] {1, 2}) {
            KeyboardAppearance.select(app, mode);
            final String suffix = mode == 1 ? "light" : "dark";
            final ActivityController<MainActivity> main =
                    Robolectric.buildActivity(MainActivity.class).setup();
            assertNotNull(
                    find(main.get().findViewById(android.R.id.content), MaterialButton.class));
            save(main.get().findViewById(android.R.id.content), "main-" + suffix);
            main.pause().stop().destroy();
            new ControllerProfiles(app).save(ControllerProfiles.defaults("preview", "Gamepad"));
            final Bundle profileState = new Bundle();
            profileState.putString("device", "preview");
            profileState.putString("deviceName", "Gamepad");
            final ActivityController<ControllerSettingsActivity> mappings =
                    Robolectric.buildActivity(ControllerSettingsActivity.class)
                            .create(profileState)
                            .start()
                            .resume()
                            .visible();
            final MaterialButton button =
                    find(mappings.get().findViewById(android.R.id.content), MaterialButton.class);
            assertNotNull(button);
            assertEquals(AppUi.ink(app), button.getCurrentTextColor());
            save(mappings.get().findViewById(android.R.id.content), "controller-" + suffix);
            mappings.pause().stop().destroy();
            final ActivityController<TypingSettingsActivity> settings =
                    Robolectric.buildActivity(TypingSettingsActivity.class).setup();
            assertNotNull(
                    find(settings.get().findViewById(android.R.id.content), MaterialSwitch.class));
            save(settings.get().findViewById(android.R.id.content), "settings-" + suffix);
            settings.pause().stop().destroy();
            final Intent intent =
                    new Intent(app, DocumentActivity.class)
                            .putExtra("title", "Credits & licenses")
                            .putExtra("asset", "licenses/credits.md");
            final ActivityController<DocumentActivity> docs =
                    Robolectric.buildActivity(DocumentActivity.class, intent).setup();
            final TextView body = document(docs.get().findViewById(android.R.id.content));
            assertNotNull(body);
            assertTrue(body.getText() instanceof Spanned);
            assertTrue(
                    ((Spanned) body.getText())
                                    .getSpans(0, body.length(), StrongEmphasisSpan.class)
                                    .length
                            > 0);
            assertFalse(body.getText().toString().contains("**danzel**"));
            assertTrue(body.getText().toString().contains("Redistribution and use"));
            save(docs.get().findViewById(android.R.id.content), "credits-" + suffix);
            docs.pause().stop().destroy();
        }
    }

    private static TextView document(final View view) {
        if (view instanceof TextView
                && ((TextView) view).getText().toString().contains("Credits and third-party")) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                final TextView found = document(((ViewGroup) view).getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T extends View> T find(final View view, final Class<T> type) {
        if (type.isInstance(view)) {
            return type.cast(view);
        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                final T found = find(((ViewGroup) view).getChildAt(i), type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void save(final View root, final String name) throws Exception {
        root.measure(
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1280, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 720, 1280);
        final Bitmap bitmap = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        final File output = new File("build/reports/material-" + name + ".png");
        output.getParentFile().mkdirs();
        try (final FileOutputStream stream = new FileOutputStream(output)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
    }
}
