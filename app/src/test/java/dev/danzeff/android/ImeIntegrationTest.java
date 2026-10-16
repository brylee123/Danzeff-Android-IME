package dev.danzeff.android;

import android.os.Looper;
import android.text.InputType;
import android.text.Selection;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.android.controller.ServiceController;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "xhdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class ImeIntegrationTest {
    public static class TestIme extends DanzeffImeService {
        final EditingEngineTest.Editor editor = new EditingEngineTest.Editor();
        @Override public InputConnection getCurrentInputConnection() { return editor; }
    }
    private ServiceController<TestIme> controller;
    private TestIme ime;
    private View root;
    private DanzeffKeyboardView keyboard;
    private EditorInfo info;
    @Before public void setup() {
        RuntimeEnvironment.getApplication().getSharedPreferences("danzeff_preferences", 0).edit().clear().commit();
        controller = Robolectric.buildService(TestIme.class).create(); ime = controller.get();
        root = ime.onCreateInputView();
        var activity = Robolectric.buildActivity(android.app.Activity.class).setup().visible().get();
        activity.setContentView(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.AT_MOST));
        root.layout(0, 0, 720, root.getMeasuredHeight());
        keyboard = findKeyboard(root);
        info = new EditorInfo(); info.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        ime.onStartInputView(info, false);
    }
    @Test public void fieldChangesResetModesAndPreserveManualOverrides() {
        assertEquals(KeyboardLayout.UPPER, ime.currentLayer());
        ime.onShift(); assertEquals(KeyboardLayout.LOWER, ime.currentLayer());
        ime.onChooseGroup(0, 0); ime.onChooseSlot(KeyboardLayout.LEFT); assertEquals("a", ime.editor.text.toString());
        info.inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        ime.onStartInputView(info, false); assertEquals(KeyboardLayout.DIGITS, ime.currentLayer());
        ime.onChooseGroup(0, 1); ime.onChooseSlot(KeyboardLayout.TOP); assertEquals("a.", ime.editor.text.toString());
        assertNotNull(button(root, "0")); assertNotNull(button(root, "."));
        info.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
        ime.onStartInputView(info, false); assertEquals(KeyboardLayout.LOWER, ime.currentLayer());
        assertNotNull(button(root, "@"));
        ime.onFinishInputView(true); controller.destroy();
    }
    @Test public void editToolbarAndSpaceDragMoveAndSelectWithoutTypingSpace() {
        ime.editor.set("abc");
        button(root, "Edit").performClick();
        button(root, "Select").performClick();
        button(root, "←").performClick();
        assertEquals("c", ime.editor.getSelectedText(0).toString());
        button(root, "Copy").performClick(); assertEquals(android.R.id.copy, ime.editor.menu);
        Selection.setSelection(ime.editor.text, 3);
        float y = keyboard.getHeight() - 30;
        touch(MotionEvent.ACTION_DOWN, 420, y);
        touch(MotionEvent.ACTION_MOVE, 378, y);
        touch(MotionEvent.ACTION_UP, 378, y);
        assertEquals("abc", ime.editor.text.toString()); assertEquals(2, Selection.getSelectionEnd(ime.editor.text));
        controller.destroy();
    }
    @Test public void heldDeleteStopsOnReleaseCancelLeavingKeyAndDisconnect() {
        float y = keyboard.getHeight() - 30;
        ime.editor.set("abcdefghij");
        ime.onUpdateSelection(0, 0, 10, 10, -1, -1);
        shadowOf(Looper.getMainLooper()).idle();
        int beforeWidth = keyboard.getWidth(); y = keyboard.getHeight() - 30;
        touch(MotionEvent.ACTION_DOWN, 300, y); assertEquals(9, ime.editor.text.length());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(540));
        assertTrue("count=" + ime.editor.text.length() + " width=" + beforeWidth + "/" + keyboard.getWidth() + " attached=" + keyboard.isAttachedToWindow(), ime.editor.text.length() <= 7);
        touch(MotionEvent.ACTION_UP, 300, y);
        int count = ime.editor.text.length(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(count, ime.editor.text.length());
        touch(MotionEvent.ACTION_DOWN, 300, y); touch(MotionEvent.ACTION_CANCEL, 300, y);
        count = ime.editor.text.length(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); assertEquals(count, ime.editor.text.length());
        touch(MotionEvent.ACTION_DOWN, 300, y); touch(MotionEvent.ACTION_MOVE, 60, y);
        count = ime.editor.text.length(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); assertEquals(count, ime.editor.text.length());
        touch(MotionEvent.ACTION_UP, 60, y);

        touch(MotionEvent.ACTION_DOWN, 300, y);
        java.lang.reflect.Field listenerField;
        try {
            listenerField = DanzeffImeService.class.getDeclaredField("deviceListener"); listenerField.setAccessible(true);
            ((android.hardware.input.InputManager.InputDeviceListener)listenerField.get(ime)).onInputDeviceRemoved(99);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        count = ime.editor.text.length(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); assertEquals(count, ime.editor.text.length());
        controller.destroy();
    }
    @Test public void hidingOrRecreatingKeyboardCancelsRepeatAndCorrectionState() {
        ime.editor.set("abcdef");
        float y = keyboard.getHeight() - 30;
        touch(MotionEvent.ACTION_DOWN, 300, y);
        ime.onFinishInputView(false);
        assertNull(button(root, "abcdef"));
        int count = ime.editor.text.length(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals(count, ime.editor.text.length());
        ime.onStartInputView(info, true);
        keyboard.onConfigurationChanged(ime.getResources().getConfiguration());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1)); assertEquals(count, ime.editor.text.length());
        controller.destroy();
    }
    @Test public void explicitCapsLockPersistsThroughTypingAndClearsOnFieldChange() {
        button(root, "Caps").performClick(); assertEquals(KeyboardLayout.UPPER, ime.currentLayer());
        ime.onChooseGroup(0, 0); ime.onChooseSlot(KeyboardLayout.LEFT);
        ime.onChooseSlot(KeyboardLayout.LEFT); assertEquals("AA", ime.editor.text.toString());
        ime.onShift(); assertEquals(KeyboardLayout.LOWER, ime.currentLayer());
        ime.onStartInputView(info, false); assertNotNull(button(root, "Caps"));
        controller.destroy();
    }
    @Test public void suggestionButtonsAreWiredAndDisappearInPasswordFields() {
        ime.editor.set("hel"); ime.onUpdateSelection(0, 0, 3, 3, -1, -1);
        button(root, "hello").performClick(); assertEquals("hello ", ime.editor.text.toString());
        info.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        ime.onStartInputView(info, false);
        ime.editor.set("hel"); ime.onUpdateSelection(0, 0, 3, 3, -1, -1);
        assertNull(button(root, "hello"));
        controller.destroy();
    }
    private void touch(int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(1, 2, action, x, y, 0);
        keyboard.onTouchEvent(event); event.recycle();
    }
    static Button button(View view, String text) {
        if (view instanceof Button && ((Button) view).getText().toString().equals(text)) return (Button) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            Button result = button(((ViewGroup) view).getChildAt(i), text); if (result != null) return result;
        }
        return null;
    }
    private static DanzeffKeyboardView findKeyboard(View view) {
        if (view instanceof DanzeffKeyboardView) return (DanzeffKeyboardView) view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            DanzeffKeyboardView result = findKeyboard(((ViewGroup) view).getChildAt(i)); if (result != null) return result;
        }
        return null;
    }
}
