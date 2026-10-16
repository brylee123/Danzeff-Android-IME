package dev.danzeff.android;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class KeyboardRenderingTest {
    private static final class Keyboard implements DanzeffKeyboardView.Listener {
        final DanzeffKeyboardView view;
        final List<String> output = new ArrayList<>();
        int row = 1;
        int col = 1;
        int layer;
        int numbers;
        int picker;

        Keyboard() {
            RuntimeEnvironment.getApplication()
                    .getSharedPreferences("danzeff_preferences", 0)
                    .edit()
                    .clear()
                    .commit();
            KeyboardAppearance.select(RuntimeEnvironment.getApplication(), 2);
            view = new DanzeffKeyboardView(RuntimeEnvironment.getApplication(), this);
            view.measure(
                    View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(592, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, 720, 592);
        }

        @Override
        public void onChooseGroup(final int r, final int c) {
            row = r;
            col = c;
            view.updateState(layer, row, col);
        }

        @Override
        public void onChooseSlot(final int slot) {
            output.add(KeyboardLayout.at(layer, row, col, slot));
        }

        @Override
        public void onNumbers() {
            numbers++;
            layer = layer == 0 ? 2 : 0;
            view.updateState(layer, row, col);
        }

        @Override
        public void onSwitchKeyboard() {
            picker++;
        }

        @Override
        public void onBackspace() {}

        @Override
        public void onSpace() {}

        @Override
        public void onEnter() {}

        @Override
        public void onShift() {}
    }

    @Test
    public void solidPaletteAndExpandedCellRender() throws Exception {
        final Keyboard keyboard = new Keyboard();
        final Bitmap preview = bitmap(keyboard.view);
        assertEquals(0xFF303134, preview.getPixel(70, 130));
        assertEquals(preview.getPixel(70, 130), preview.getPixel(70, 170));
        assertEquals(0xFFF3C35A, preview.getPixel(300, 250));
        save(preview, "keyboard-preview.png");
        tap(keyboard.view, 120, 150);
        final Bitmap expanded = bitmap(keyboard.view);
        assertTrue(
                "The cell should expand over the neighboring grid area",
                Color.red(expanded.getPixel(360, 115)) > 200);
        save(expanded, "keyboard-expanded-preview.png");
        assertTrue(keyboard.output.isEmpty());
    }

    @Test
    public void fourSwipeDirectionsTypeOnceAndResetTheGroup() {
        final Keyboard keyboard = new Keyboard();
        swipe(keyboard.view, 120, 150, 120, 100);
        swipe(keyboard.view, 120, 150, 60, 150);
        swipe(keyboard.view, 120, 150, 120, 230);
        swipe(keyboard.view, 120, 150, 190, 150);
        assertArrayEquals(
                new String[] {",", "a", "b", "c"}, keyboard.output.toArray(new String[0]));
        assertEquals(1, keyboard.row);
        assertEquals(1, keyboard.col);
    }

    @Test
    public void crossingOtherCellsKeepsTheOriginalGroupLocked() {
        final Keyboard keyboard = new Keyboard();
        swipe(keyboard.view, 120, 150, 620, 150);
        assertEquals("c", keyboard.output.get(0));
        assertEquals(1, keyboard.output.size());
    }

    @Test
    public void tapCellThenTapCharacterResetsForTheNextCharacter() {
        final Keyboard keyboard = new Keyboard();
        tap(keyboard.view, 120, 150);
        assertTrue(keyboard.output.isEmpty());
        tap(keyboard.view, 90, 191);
        assertEquals("a", keyboard.output.get(0));
        tap(keyboard.view, 120, 150);
        assertEquals("A fresh tap should select, not type", 1, keyboard.output.size());
        tap(keyboard.view, 320, 191);
        assertEquals("c", keyboard.output.get(1));
    }

    @Test
    public void centerTapCancellationAndDraggingToFooterCannotTypeAccidentally() {
        final Keyboard keyboard = new Keyboard();
        tap(keyboard.view, 120, 150);
        tap(keyboard.view, 205, 191);
        assertTrue(keyboard.output.isEmpty());
        send(keyboard.view, MotionEvent.ACTION_CANCEL, 205, 191);
        swipe(keyboard.view, 120, 150, 120, 550);
        assertTrue(keyboard.output.isEmpty());
        tap(keyboard.view, 120, 150);
        tap(keyboard.view, 90, 191);
        assertEquals("a", keyboard.output.get(0));
    }

    @Test
    public void footerAndEmptyNumericSlotsRemainUsableWithAnExpandedCell() {
        final Keyboard keyboard = new Keyboard();
        tap(keyboard.view, 120, 150);
        tap(keyboard.view, 60, 535);
        assertEquals(1, keyboard.numbers);
        tap(keyboard.view, 120, 150);
        tap(keyboard.view, 90, 191);
        assertTrue("An empty numeric slot must not emit text", keyboard.output.isEmpty());
        tap(keyboard.view, 320, 191);
        assertEquals("1", keyboard.output.get(0));
        tap(keyboard.view, 660, 535);
        assertEquals(1, keyboard.picker);
    }

    @Test
    public void extraSymbolsSupportSwipeAndTwoTapAndKeepThePage() throws Exception {
        final Keyboard keyboard = new Keyboard();
        keyboard.layer = KeyboardLayout.EXTRA_SYMBOLS;
        keyboard.view.updateState(keyboard.layer, 1, 1);
        save(bitmap(keyboard.view), "keyboard-extra-symbols.png");
        swipe(keyboard.view, 120, 150, 120, 100);
        tap(keyboard.view, 120, 150);
        tap(keyboard.view, 90, 191);
        assertArrayEquals(new String[] {"@", "#"}, keyboard.output.toArray(new String[0]));
        assertEquals(KeyboardLayout.EXTRA_SYMBOLS, keyboard.layer);
        assertEquals(1, keyboard.row);
        assertEquals(1, keyboard.col);
    }

    @Test
    public void hapticsAreOptInAndCommitsHaveVisibleFeedback() {
        final Keyboard keyboard = new Keyboard();
        final int previousFeedback = Shadows.shadowOf(keyboard.view).lastHapticFeedbackPerformed();
        final int previousPixel = bitmap(keyboard.view).getPixel(300, 220);
        keyboard.view.notifyCommitted();
        assertEquals(
                previousFeedback, Shadows.shadowOf(keyboard.view).lastHapticFeedbackPerformed());
        assertNotEquals(previousPixel, bitmap(keyboard.view).getPixel(300, 220));
        RuntimeEnvironment.getApplication()
                .getSharedPreferences("danzeff_preferences", 0)
                .edit()
                .putBoolean("haptics", true)
                .commit();
        keyboard.view.notifyCommitted();
        assertEquals(
                HapticFeedbackConstants.KEYBOARD_TAP,
                Shadows.shadowOf(keyboard.view).lastHapticFeedbackPerformed());
    }

    private static Bitmap bitmap(final View view) {
        final Bitmap bitmap = Bitmap.createBitmap(720, 592, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static void save(final Bitmap bitmap, final String name) throws Exception {
        final File file = new File("build/reports/" + name);
        file.getParentFile().mkdirs();
        try (final FileOutputStream stream = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
    }

    private static void tap(final View view, final float x, final float y) {
        send(view, MotionEvent.ACTION_DOWN, x, y);
        send(view, MotionEvent.ACTION_UP, x, y);
    }

    private static void swipe(
            final View view, final float x, final float y, final float endX, final float endY) {
        send(view, MotionEvent.ACTION_DOWN, x, y);
        send(view, MotionEvent.ACTION_MOVE, endX, endY);
        send(view, MotionEvent.ACTION_UP, endX, endY);
    }

    private static void send(final View view, final int action, final float x, final float y) {
        final MotionEvent event = MotionEvent.obtain(1, 2, action, x, y, 0);
        view.onTouchEvent(event);
        event.recycle();
    }
}
