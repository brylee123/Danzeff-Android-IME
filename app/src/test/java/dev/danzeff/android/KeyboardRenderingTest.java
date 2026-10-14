package dev.danzeff.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.View;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class KeyboardRenderingTest {
    private static final class Keyboard implements DanzeffKeyboardView.Listener {
        final DanzeffKeyboardView view;
        final List<String> output = new ArrayList<>();
        int row = 1, col = 1, layer, numbers, picker;
        Keyboard() {
            RuntimeEnvironment.getApplication().getSharedPreferences("danzeff_preferences", 0).edit().clear().commit();
            KeyboardAppearance.select(RuntimeEnvironment.getApplication(), 2);
            view = new DanzeffKeyboardView(RuntimeEnvironment.getApplication(), this);
            view.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(592, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, 720, 592);
        }
        @Override public void onChooseGroup(int r, int c) { row = r; col = c; view.updateState(layer, row, col); }
        @Override public void onChooseSlot(int slot) { output.add(KeyboardLayout.at(layer, row, col, slot)); }
        @Override public void onNumbers() { numbers++; layer = layer == 0 ? 2 : 0; view.updateState(layer, row, col); }
        @Override public void onSwitchKeyboard() { picker++; }
        @Override public void onBackspace() {}
        @Override public void onSpace() {}
        @Override public void onEnter() {}
        @Override public void onShift() {}
    }
    @Test public void solidPaletteAndExpandedCellRender() throws Exception {
        Keyboard keyboard = new Keyboard();
        Bitmap preview = bitmap(keyboard.view);
        assertEquals(0xFF303134, preview.getPixel(70, 130));
        assertEquals(preview.getPixel(70, 130), preview.getPixel(70, 170));
        assertEquals(0xFFF3C35A, preview.getPixel(300, 250));
        save(preview, "keyboard-preview.png");
        tap(keyboard.view, 120, 150);
        Bitmap expanded = bitmap(keyboard.view);
        assertTrue("The cell should expand over the neighboring grid area", Color.red(expanded.getPixel(360, 115)) > 200);
        save(expanded, "keyboard-expanded-preview.png");
        assertTrue(keyboard.output.isEmpty());
    }
    @Test public void fourSwipeDirectionsTypeOnceAndResetTheGroup() {
        Keyboard keyboard = new Keyboard();
        swipe(keyboard.view, 120, 150, 120, 100);
        swipe(keyboard.view, 120, 150, 60, 150);
        swipe(keyboard.view, 120, 150, 120, 230);
        swipe(keyboard.view, 120, 150, 190, 150);
        assertArrayEquals(new String[]{",", "a", "b", "c"}, keyboard.output.toArray(new String[0]));
        assertEquals(1, keyboard.row); assertEquals(1, keyboard.col);
    }
    @Test public void crossingOtherCellsKeepsTheOriginalGroupLocked() {
        Keyboard keyboard = new Keyboard();
        swipe(keyboard.view, 120, 150, 620, 150);
        assertEquals("c", keyboard.output.get(0));
        assertEquals(1, keyboard.output.size());
    }
    @Test public void tapCellThenTapCharacterResetsForTheNextCharacter() {
        Keyboard keyboard = new Keyboard();
        tap(keyboard.view, 120, 150);
        assertTrue(keyboard.output.isEmpty());
        tap(keyboard.view, 90, 191);
        assertEquals("a", keyboard.output.get(0));
        tap(keyboard.view, 120, 150);
        assertEquals("A fresh tap should select, not type", 1, keyboard.output.size());
        tap(keyboard.view, 320, 191);
        assertEquals("c", keyboard.output.get(1));
    }
    @Test public void centerTapCancellationAndDraggingToFooterCannotTypeAccidentally() {
        Keyboard keyboard = new Keyboard();
        tap(keyboard.view, 120, 150); tap(keyboard.view, 205, 191);
        assertTrue(keyboard.output.isEmpty());
        send(keyboard.view, MotionEvent.ACTION_CANCEL, 205, 191);
        swipe(keyboard.view, 120, 150, 120, 550);
        assertTrue(keyboard.output.isEmpty());
        tap(keyboard.view, 120, 150); tap(keyboard.view, 90, 191);
        assertEquals("a", keyboard.output.get(0));
    }
    @Test public void footerAndEmptyNumericSlotsRemainUsableWithAnExpandedCell() {
        Keyboard keyboard = new Keyboard();
        tap(keyboard.view, 120, 150); tap(keyboard.view, 60, 535);
        assertEquals(1, keyboard.numbers);
        tap(keyboard.view, 120, 150); tap(keyboard.view, 90, 191);
        assertTrue("An empty numeric slot must not emit text", keyboard.output.isEmpty());
        tap(keyboard.view, 320, 191); assertEquals("1", keyboard.output.get(0));
        tap(keyboard.view, 660, 535); assertEquals(1, keyboard.picker);
    }
    @Test public void controllerLayoutRetainsFaceButtonsAndTouchModeReturnsOnDisconnect() {
        Keyboard keyboard = new Keyboard(); keyboard.view.setControllerConnected(true);
        tap(keyboard.view, 120, 150); assertTrue(keyboard.output.isEmpty());
        tap(keyboard.view, 555, 276); assertEquals("a", keyboard.output.get(0));
        tap(keyboard.view, 660, 276); assertEquals("c", keyboard.output.get(1));
        keyboard.view.setControllerConnected(false);
        swipe(keyboard.view, 120, 150, 60, 150);
        assertEquals("a", keyboard.output.get(2));
    }
    @Test public void extraSymbolsSupportSwipeAndTwoTapAndKeepThePage() throws Exception {
        Keyboard keyboard = new Keyboard(); keyboard.layer = KeyboardLayout.EXTRA_SYMBOLS;
        keyboard.view.updateState(keyboard.layer, 1, 1);
        save(bitmap(keyboard.view), "keyboard-extra-symbols.png");
        swipe(keyboard.view, 120, 150, 120, 100);
        tap(keyboard.view, 120, 150); tap(keyboard.view, 90, 191);
        assertArrayEquals(new String[]{"@", "#"}, keyboard.output.toArray(new String[0]));
        assertEquals(KeyboardLayout.EXTRA_SYMBOLS, keyboard.layer);
        assertEquals(1, keyboard.row); assertEquals(1, keyboard.col);
    }
    @Test public void hapticsAreOptInAndCommitsHaveVisibleFeedback() {
        Keyboard keyboard = new Keyboard();
        int previousFeedback = org.robolectric.Shadows.shadowOf(keyboard.view).lastHapticFeedbackPerformed();
        int previousPixel = bitmap(keyboard.view).getPixel(300, 220);
        keyboard.view.notifyCommitted();
        assertEquals(previousFeedback, org.robolectric.Shadows.shadowOf(keyboard.view).lastHapticFeedbackPerformed());
        assertNotEquals(previousPixel, bitmap(keyboard.view).getPixel(300, 220));
        RuntimeEnvironment.getApplication().getSharedPreferences("danzeff_preferences", 0).edit().putBoolean("haptics", true).commit();
        keyboard.view.notifyCommitted();
        assertEquals(android.view.HapticFeedbackConstants.KEYBOARD_TAP,
                org.robolectric.Shadows.shadowOf(keyboard.view).lastHapticFeedbackPerformed());
    }
    private static Bitmap bitmap(View view) {
        Bitmap bitmap = Bitmap.createBitmap(720, 592, Bitmap.Config.ARGB_8888); view.draw(new Canvas(bitmap)); return bitmap;
    }
    private static void save(Bitmap bitmap, String name) throws Exception {
        File file = new File("build/reports/" + name); file.getParentFile().mkdirs();
        try (FileOutputStream stream = new FileOutputStream(file)) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)); }
    }
    private static void tap(View view, float x, float y) { send(view, MotionEvent.ACTION_DOWN, x, y); send(view, MotionEvent.ACTION_UP, x, y); }
    private static void swipe(View view, float x, float y, float endX, float endY) {
        send(view, MotionEvent.ACTION_DOWN, x, y); send(view, MotionEvent.ACTION_MOVE, endX, endY); send(view, MotionEvent.ACTION_UP, endX, endY);
    }
    private static void send(View view, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(1, 2, action, x, y, 0); view.onTouchEvent(event); event.recycle();
    }
}
