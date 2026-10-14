package dev.danzeff.android;

import android.content.Context;
import android.os.Build;
import android.inputmethodservice.InputMethodService;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

/** System IME: no network, background processing, typed-text collection or logging. */
public class DanzeffImeService extends InputMethodService implements DanzeffKeyboardView.Listener {
    private DanzeffKeyboardView keyboard;
    private boolean numbers;
    private boolean shift;
    private int row = 1;
    private int col = 1;

    @Override public View onCreateInputView() {
        keyboard = new DanzeffKeyboardView(this, this);
        syncView();
        return keyboard;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        numbers = false;
        shift = false;
        row = 1;
        col = 1;
        syncView();
    }

    @Override public boolean onEvaluateFullscreenMode() {
        // Keeps the field visible on most landscape screens, including foldables.
        return false;
    }

    private void syncView() {
        if (keyboard != null) keyboard.updateState(KeyboardLayout.layer(numbers, shift), row, col);
    }

    @Override public void onChooseGroup(int newRow, int newCol) {
        row = Math.max(0, Math.min(2, newRow));
        col = Math.max(0, Math.min(2, newCol));
        syncView();
    }

    @Override public void onChooseSlot(int slot) {
        String text = KeyboardLayout.at(KeyboardLayout.layer(numbers, shift), row, col, slot);
        if (text.isEmpty()) return;
        if ("\b".equals(text)) {
            deleteCharacter();
        } else {
            commit(text);
        }
        // Touch SHIFT is one-shot. On a physical gamepad holding R1 re-enables it below.
        if (shift && !numbers) {
            shift = false;
            syncView();
        }
    }

    @Override public void onBackspace() { deleteCharacter(); }
    @Override public void onSpace() { commit(" "); }
    @Override public void onEnter() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        EditorInfo info = getCurrentInputEditorInfo();
        int action = info == null ? EditorInfo.IME_ACTION_NONE :
            (info.imeOptions & EditorInfo.IME_MASK_ACTION);
        if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            if (connection.performEditorAction(action)) return;
        }
        commit("\n");
    }
    @Override public void onShift() { shift = !shift; syncView(); }
    @Override public void onNumbers() { numbers = !numbers; shift = false; syncView(); }
    @Override public void onSwitchKeyboard() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !switchToNextInputMethod(false)) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        }
    }

    private void commit(String text) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) connection.commitText(text, 1);
    }

    private void deleteCharacter() {
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return;
        CharSequence selected = connection.getSelectedText(0);
        if (selected != null && selected.length() > 0) {
            connection.commitText("", 1);
            return;
        }
        CharSequence before = connection.getTextBeforeCursor(2, 0);
        if (before != null && before.length() > 0) {
            // Surrogate-safe deletion, e.g. an emoji immediately before the cursor.
            int utf16 = Character.isLowSurrogate(before.charAt(before.length() - 1)) &&
                before.length() > 1 && Character.isHighSurrogate(before.charAt(before.length() - 2)) ? 2 : 1;
            connection.deleteSurroundingText(utf16, 0);
        } else {
            // Some editors do not support getTextBeforeCursor; fall back to KEYCODE_DEL.
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD &&
            (event.getSource() & InputDevice.SOURCE_DPAD) != InputDevice.SOURCE_DPAD &&
            (event.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {
            return super.onKeyDown(keyCode, event);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: onChooseGroup(row - 1, col); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: onChooseGroup(row + 1, col); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: onChooseGroup(row, col - 1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: onChooseGroup(row, col + 1); return true;
            case KeyEvent.KEYCODE_BUTTON_Y: if (event.getRepeatCount() == 0) onChooseSlot(0); return true;
            case KeyEvent.KEYCODE_BUTTON_X: if (event.getRepeatCount() == 0) onChooseSlot(1); return true;
            case KeyEvent.KEYCODE_BUTTON_A: if (event.getRepeatCount() == 0) onChooseSlot(2); return true;
            case KeyEvent.KEYCODE_BUTTON_B: if (event.getRepeatCount() == 0) onChooseSlot(3); return true;
            case KeyEvent.KEYCODE_BUTTON_L1: if (event.getRepeatCount() == 0) onNumbers(); return true;
            case KeyEvent.KEYCODE_BUTTON_R1: if (event.getRepeatCount() == 0) onShift(); return true;
            case KeyEvent.KEYCODE_BUTTON_START: if (event.getRepeatCount() == 0) onEnter(); return true;
            case KeyEvent.KEYCODE_BUTTON_SELECT: if (event.getRepeatCount() == 0) onBackspace(); return true;
            default: return super.onKeyDown(keyCode, event);
        }
    }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_BUTTON_X:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_BUTTON_B:
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                if ((event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                    (event.getSource() & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD) return true;
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
            event.getAction() == MotionEvent.ACTION_MOVE) {
            float x = event.getAxisValue(MotionEvent.AXIS_X);
            float y = event.getAxisValue(MotionEvent.AXIS_Y);
            // Center of analog stick maps to center cell, exactly as on PSP.
            int newCol = x < -0.45f ? 0 : x > 0.45f ? 2 : 1;
            int newRow = y < -0.45f ? 0 : y > 0.45f ? 2 : 1;
            if (row != newRow || col != newCol) onChooseGroup(newRow, newCol);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }
}
