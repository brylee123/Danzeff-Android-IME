package dev.danzeff.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ImePreviewTest {
    @Test public void newToolbarsAndFieldShortcutsRenderInBothThemes() throws Exception {
        var prefs = RuntimeEnvironment.getApplication().getSharedPreferences("danzeff_preferences", 0);
        prefs.edit().clear().commit();
        for (int mode : new int[]{1, 2}) {
            KeyboardAppearance.select(RuntimeEnvironment.getApplication(), mode);
            var controller = Robolectric.buildService(ImeIntegrationTest.TestIme.class).create();
            var ime = controller.get();
            EditorInfo info = new EditorInfo(); info.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
            ime.editor.set("hel"); ime.onStartInput(info, false);
            View root = ime.onCreateInputView(); ime.onStartInputView(info, false);
            assertNotNull(ImeIntegrationTest.button(root, "hello"));
            save(root, "ime-v0.6-suggestions-" + (mode == 1 ? "light" : "dark") + ".png");
            ImeIntegrationTest.button(root, "Edit").performClick();
            assertNotNull(ImeIntegrationTest.button(root, "Select"));
            save(root, "ime-v0.6-edit-" + (mode == 1 ? "light" : "dark") + ".png");
            info.inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED;
            ime.onStartInput(info, false); ime.onStartInputView(info, false);
            assertNotNull(ImeIntegrationTest.button(root, "."));
            assertEquals(KeyboardLayout.DIGITS, ime.currentLayer());
            save(root, "ime-v0.6-number-" + (mode == 1 ? "light" : "dark") + ".png");
            controller.destroy();
        }
    }
    private static void save(View root, String name) throws Exception {
        root.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST));
        root.layout(0, 0, 720, root.getMeasuredHeight());
        Bitmap image = Bitmap.createBitmap(720, root.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(image));
        File output = new File("build/reports/" + name); output.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(output)) { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out)); }
    }
}
