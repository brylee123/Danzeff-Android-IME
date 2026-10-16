package dev.danzeff.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
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

import dev.danzeff.android.UnifiedInputLayoutTest.ControllerIme;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Records actual drawing and sends hardware input through the IME mapping path. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UnifiedInputLayoutTest {
    public static class ControllerIme extends ImeIntegrationTest.TestIme {
        @Override
        public boolean isInputViewShown() {
            return true;
        }
    }

    private static final class RecordingCanvas extends Canvas {
        final List<String> labels = new ArrayList<>();

        RecordingCanvas(final Bitmap bitmap) {
            super(bitmap);
        }

        @Override
        public void drawText(final String text, final float x, final float y, final Paint paint) {
            labels.add(text);
            super.drawText(text, x, y, paint);
        }
    }

    @Test
    public void gridNeverChangesWithDeviceEventsAndHardwareAndTouchBothType() throws Exception {
        final Application app = RuntimeEnvironment.getApplication();
        for (final int mode : new int[] {1, 2}) {
            // A legacy saved touch-mode preference must not bring the old panel back.
            app.getSharedPreferences("danzeff_preferences", 0)
                    .edit()
                    .clear()
                    .putBoolean("direct_touch", true)
                    .putBoolean("capitalization", false)
                    .commit();
            KeyboardAppearance.select(app, mode);
            new ControllerProfiles(app)
                    .save(ControllerProfiles.defaults("saved-controller", "Gamepad"));
            final ServiceController<ControllerIme> service =
                    Robolectric.buildService(ControllerIme.class).create();
            final ControllerIme ime = service.get();
            final EditorInfo field = new EditorInfo();
            field.inputType = InputType.TYPE_CLASS_TEXT;
            ime.onStartInput(field, false);
            final View root = ime.onCreateInputView();
            final DanzeffKeyboardView keyboard = findKeyboard(root);
            ime.onStartInputView(field, false);
            assertNotNull(keyboard);
            keyboard.measure(
                    View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(592, View.MeasureSpec.EXACTLY));
            keyboard.layout(0, 0, 720, 592);
            final Bitmap initial = render(keyboard);
            final Field listenerField = DanzeffImeService.class.getDeclaredField("deviceListener");
            listenerField.setAccessible(true);
            final InputManager.InputDeviceListener devices =
                    (InputManager.InputDeviceListener) listenerField.get(ime);
            devices.onInputDeviceAdded(99);
            assertTrue(
                    "Connecting a device must not change any pixels",
                    initial.sameAs(render(keyboard)));

            ime.onChooseGroup(0, 2);
            final KeyEvent down =
                    new KeyEvent(
                            1,
                            1,
                            KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_BUTTON_X,
                            0,
                            0,
                            -1,
                            0,
                            0,
                            InputDevice.SOURCE_GAMEPAD);
            assertTrue(ime.onKeyDown(down.getKeyCode(), down));
            assertTrue(
                    ime.onKeyUp(
                            down.getKeyCode(), KeyEvent.changeAction(down, KeyEvent.ACTION_UP)));
            assertEquals("g", ime.editor.text.toString());
            render(keyboard);

            // A touchscreen swipe still works in the former right-side panel area.
            send(keyboard, MotionEvent.ACTION_DOWN, 600, 150);
            send(keyboard, MotionEvent.ACTION_MOVE, 540, 150);
            send(keyboard, MotionEvent.ACTION_UP, 540, 150);
            assertEquals("gg", ime.editor.text.toString());
            devices.onInputDeviceChanged(99);
            devices.onInputDeviceRemoved(99);
            render(keyboard);

            ImeIntegrationTest.button(root, "Edit").performClick();
            assertNotNull(ImeIntegrationTest.button(root, "Copy"));
            root.measure(
                    View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST));
            root.layout(0, 0, 720, root.getMeasuredHeight());
            final Bitmap screenshot =
                    Bitmap.createBitmap(720, root.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(screenshot));
            save(screenshot, "unified-keyboard-" + (mode == 1 ? "light" : "dark") + ".png");
            service.destroy();
        }
    }

    private static Bitmap render(final DanzeffKeyboardView view) {
        final Bitmap bitmap = Bitmap.createBitmap(720, 592, Bitmap.Config.ARGB_8888);
        final RecordingCanvas canvas = new RecordingCanvas(bitmap);
        view.draw(canvas);
        for (final String forbidden :
                Arrays.asList("△", "□", "×", "○", "DIRECT TAP  ⇄", "TWO-STEP  ⇄")) {
            assertFalse(
                    "Removed control rendered: " + forbidden, canvas.labels.contains(forbidden));
        }
        assertTrue(canvas.labels.contains("HOLD & SWIPE · OR TAP"));
        return bitmap;
    }

    private static DanzeffKeyboardView findKeyboard(final View view) {
        if (view instanceof DanzeffKeyboardView) {
            return (DanzeffKeyboardView) view;
        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                final DanzeffKeyboardView found = findKeyboard(((ViewGroup) view).getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void send(final View view, final int action, final float x, final float y) {
        final MotionEvent event = MotionEvent.obtain(1, 2, action, x, y, 0);
        view.onTouchEvent(event);
        event.recycle();
    }

    private static void save(final Bitmap bitmap, final String name) throws Exception {
        final File file = new File("build/reports/" + name);
        file.getParentFile().mkdirs();
        try (final FileOutputStream stream = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
    }
}
