package dev.danzeff.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.input.InputManager;
import android.text.InputType;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** Records actual drawing and sends hardware input through the IME mapping path. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UnifiedInputLayoutTest {
    public static class ControllerIme extends ImeIntegrationTest.TestIme {
        @Override public boolean isInputViewShown() { return true; }
    }
    private static final class RecordingCanvas extends Canvas {
        final List<String> labels = new ArrayList<>();
        RecordingCanvas(Bitmap bitmap) { super(bitmap); }
        @Override public void drawText(String text, float x, float y, Paint paint) {
            labels.add(text); super.drawText(text, x, y, paint);
        }
    }
    @Test public void gridNeverChangesWithDeviceEventsAndHardwareAndTouchBothType() throws Exception {
        var app = RuntimeEnvironment.getApplication();
        for (int mode : new int[]{1, 2}) {
            // A legacy saved touch-mode preference must not bring the old panel back.
            app.getSharedPreferences("danzeff_preferences", 0).edit().clear()
                    .putBoolean("direct_touch", true).putBoolean("capitalization", false).commit();
            KeyboardAppearance.select(app, mode);
            new ControllerProfiles(app).save(ControllerProfiles.defaults("saved-controller", "Gamepad"));
            var service = Robolectric.buildService(ControllerIme.class).create();
            var ime = service.get();
            EditorInfo field = new EditorInfo(); field.inputType = InputType.TYPE_CLASS_TEXT;
            ime.onStartInput(field, false);
            View root = ime.onCreateInputView();
            DanzeffKeyboardView keyboard = findKeyboard(root);
            ime.onStartInputView(field, false);
            assertNotNull(keyboard);
            keyboard.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(592, View.MeasureSpec.EXACTLY));
            keyboard.layout(0, 0, 720, 592);
            Bitmap initial = render(keyboard);
            java.lang.reflect.Field listenerField = DanzeffImeService.class.getDeclaredField("deviceListener");
            listenerField.setAccessible(true);
            InputManager.InputDeviceListener devices = (InputManager.InputDeviceListener)listenerField.get(ime);
            devices.onInputDeviceAdded(99);
            assertTrue("Connecting a device must not change any pixels", initial.sameAs(render(keyboard)));

            ime.onChooseGroup(0, 2);
            KeyEvent down = new KeyEvent(1, 1, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_X,
                    0, 0, -1, 0, 0, InputDevice.SOURCE_GAMEPAD);
            assertTrue(ime.onKeyDown(down.getKeyCode(), down));
            assertTrue(ime.onKeyUp(down.getKeyCode(), KeyEvent.changeAction(down, KeyEvent.ACTION_UP)));
            assertEquals("g", ime.editor.text.toString());
            render(keyboard);

            // A touchscreen swipe still works in the former right-side panel area.
            send(keyboard, MotionEvent.ACTION_DOWN, 600, 150);
            send(keyboard, MotionEvent.ACTION_MOVE, 540, 150);
            send(keyboard, MotionEvent.ACTION_UP, 540, 150);
            assertEquals("gg", ime.editor.text.toString());
            devices.onInputDeviceChanged(99); devices.onInputDeviceRemoved(99);
            render(keyboard);

            ImeIntegrationTest.button(root, "Edit").performClick();
            assertNotNull(ImeIntegrationTest.button(root, "Copy"));
            root.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST));
            root.layout(0, 0, 720, root.getMeasuredHeight());
            Bitmap screenshot = Bitmap.createBitmap(720, root.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(screenshot));
            save(screenshot, "unified-keyboard-" + (mode == 1 ? "light" : "dark") + ".png");
            service.destroy();
        }
    }
    private static Bitmap render(DanzeffKeyboardView view) {
        Bitmap bitmap = Bitmap.createBitmap(720, 592, Bitmap.Config.ARGB_8888);
        RecordingCanvas canvas = new RecordingCanvas(bitmap); view.draw(canvas);
        for (String forbidden : Arrays.asList("△", "□", "×", "○", "DIRECT TAP  ⇄", "TWO-STEP  ⇄"))
            assertFalse("Removed control rendered: " + forbidden, canvas.labels.contains(forbidden));
        assertTrue(canvas.labels.contains("HOLD & SWIPE · OR TAP"));
        return bitmap;
    }
    private static DanzeffKeyboardView findKeyboard(View view) {
        if (view instanceof DanzeffKeyboardView) return (DanzeffKeyboardView)view;
        if (view instanceof ViewGroup) for (int i=0;i<((ViewGroup)view).getChildCount();i++) {
            DanzeffKeyboardView found = findKeyboard(((ViewGroup)view).getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    private static void send(View view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(1, 2, action, x, y, 0); view.onTouchEvent(event); event.recycle();
    }
    private static void save(Bitmap bitmap, String name) throws Exception {
        File file = new File("build/reports/" + name); file.getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(file)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)); }
    }
}
