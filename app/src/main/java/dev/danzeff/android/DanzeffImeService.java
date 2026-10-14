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
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.view.WindowInsets;
import android.widget.FrameLayout;

/** System IME: no network, background processing, typed-text collection or logging. */
public class DanzeffImeService extends InputMethodService implements DanzeffKeyboardView.Listener {
    private DanzeffKeyboardView keyboard;
    private boolean numbers;
    private boolean shift;
    private ControllerProfiles profiles;
    private final ControllerInput controllerInput = new ControllerInput();
    private final Set<String> heldShift = new HashSet<>();
    private final Set<String> consumedKeys = new HashSet<>();
    private final Map<String, String> lastInput = new HashMap<>();
    private final Map<String, Long> lastActionTime = new HashMap<>();
    private InputManager inputManager;
    private boolean controllerLayout;
    private final InputManager.InputDeviceListener deviceListener = new InputManager.InputDeviceListener() {
        @Override public void onInputDeviceAdded(int id) { refreshControllerLayout(); }
        @Override public void onInputDeviceChanged(int id) { onInputDeviceRemoved(id); }
        @Override public void onInputDeviceRemoved(int id) {
            String prefix = id + ":";
            heldShift.removeIf(token -> token.startsWith(prefix));
            consumedKeys.removeIf(token -> token.startsWith(prefix));
            controllerInput.forget(id); syncView(); refreshControllerLayout();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        if (inputManager != null) inputManager.registerInputDeviceListener(deviceListener, null);
    }
    @Override public void onDestroy() {
        if (inputManager != null) inputManager.unregisterInputDeviceListener(deviceListener);
        super.onDestroy();
    }
    private int row = 1;
    private int col = 1;

    @Override public View onCreateInputView() {
        profiles = new ControllerProfiles(this);
        keyboard = new DanzeffKeyboardView(this, this);
        syncView();
        refreshControllerLayout();
        FrameLayout container = new FrameLayout(this);
        container.setBackgroundColor(0xFF181818);
        container.addView(keyboard, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        container.setOnApplyWindowInsetsListener((view, insets) -> {
            int bottom = Build.VERSION.SDK_INT >= 30
                ? insets.getInsets(WindowInsets.Type.navigationBars()).bottom : insets.getSystemWindowInsetBottom();
            // Reserve a separate strip for Android's dismiss/picker buttons as well as its gesture bar.
            int shortcuts = Math.round(48 * getResources().getDisplayMetrics().density);
            view.setPadding(0, 0, 0, Math.max(bottom, shortcuts));
            return insets;
        });
        return container;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        numbers = false;
        shift = false;
        row = 1;
        col = 1;
        controllerInput.reset();
        heldShift.clear();
        consumedKeys.clear();
        lastInput.clear(); lastActionTime.clear();
        if (keyboard != null) keyboard.resetTouchSelection();
        refreshControllerLayout();
        syncView();
    }

    @Override public boolean onEvaluateFullscreenMode() {
        // Keeps the field visible on most landscape screens, including foldables.
        return false;
    }

    private void syncView() {
        if (keyboard != null) keyboard.updateState(KeyboardLayout.layer(numbers, shift || !heldShift.isEmpty()), row, col);
    }

    private void refreshControllerLayout() {
        if (keyboard == null) return;
        boolean connected = false;
        Set<String> configuredDevices = new HashSet<>();
        if (profiles != null) for (ControllerProfile saved : profiles.all()) configuredDevices.add(saved.device);
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && !device.isVirtual() && (device.supportsSource(InputDevice.SOURCE_GAMEPAD)
                    || device.supportsSource(InputDevice.SOURCE_JOYSTICK)
                    || (ControllerInput.external(device) && configuredDevices.contains(device.getDescriptor())))) {
                connected = true; break;
            }
        }
        if (controllerLayout != connected) { row = col = 1; controllerLayout = connected; }
        keyboard.setControllerConnected(connected); syncView();
    }

    @Override public void onChooseGroup(int newRow, int newCol) {
        row = Math.max(0, Math.min(2, newRow));
        col = Math.max(0, Math.min(2, newCol));
        syncView();
    }

    @Override public void onChooseSlot(int slot) {
        String text = KeyboardLayout.at(KeyboardLayout.layer(numbers, shift || !heldShift.isEmpty()), row, col, slot);
        if (text.isEmpty()) return;
        if ("\b".equals(text)) {
            deleteCharacter();
        } else {
            commit(text);
        }
        // Touch shift is one-shot; mapped hold-shift inputs remain active until released.
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

    @Override public boolean onEvaluateInputViewShown() {
        if (super.onEvaluateInputViewShown()) return true;
        // This IME needs its character grid even when a physical keypad is attached.
        return true;
    }

    @Override public void onFinishInput() {
        heldShift.clear(); consumedKeys.clear(); controllerInput.reset();
        super.onFinishInput();
    }

    private ControllerProfile profile(InputDevice device) {
        if (profiles == null) profiles = new ControllerProfiles(this);
        return profiles.active(ControllerProfiles.descriptor(device), ControllerProfiles.deviceName(device));
    }

    private void action(String action, String inputId, boolean pressed) {
        if ("shift_hold".equals(action)) {
            if (pressed) heldShift.add(inputId); else heldShift.remove(inputId);
            syncView(); return;
        }
        if (!pressed || action == null) return;
        // Some controllers report the same D-pad press as both a key and a hat axis.
        String device = inputId.substring(0, inputId.indexOf(':'));
        String actionId = device + ":" + action;
        String previous = lastInput.get(actionId);
        boolean key = inputId.contains(":key:"), previousKey = previous != null && previous.contains(":key:");
        long now = SystemClock.uptimeMillis();
        if (previous != null && key != previousKey
                && now - lastActionTime.getOrDefault(actionId, 0L) < 50) return;
        lastInput.put(actionId, inputId); lastActionTime.put(actionId, now);
        if (action.startsWith("slot")) { onChooseSlot(Integer.parseInt(action.substring(4))); return; }
        if (action.startsWith("group")) {
            int group = Integer.parseInt(action.substring(5)); onChooseGroup(group / 3, group % 3); return;
        }
        switch (action) {
            case "up": onChooseGroup(row - 1, col); break;
            case "down": onChooseGroup(row + 1, col); break;
            case "left": onChooseGroup(row, col - 1); break;
            case "right": onChooseGroup(row, col + 1); break;
            case "shift": onShift(); break;
            case "numbers": onNumbers(); break;
            case "space": onSpace(); break;
            case "delete": onBackspace(); break;
            case "enter": onEnter(); break;
            case "newline": commit("\n"); break;
            case "picker": onSwitchKeyboard(); break;
            case "hide": requestHideSelf(0); break;
            case "cursor_left": cursor(KeyEvent.KEYCODE_DPAD_LEFT); break;
            case "cursor_right": cursor(KeyEvent.KEYCODE_DPAD_RIGHT); break;
        }
    }
    private void cursor(int code) {
        InputConnection connection = getCurrentInputConnection();
        if (connection != null) {
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
            connection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        }
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isInputViewShown() || !ControllerInput.eligible(event)) return super.onKeyDown(keyCode, event);
        String token = "key:" + keyCode, inputId = event.getDeviceId() + ":" + token;
        String mapped = profile(event.getDevice()).bindings.get(token);
        if (mapped == null) return super.onKeyDown(keyCode, event);
        consumedKeys.add(inputId);
        if (event.getRepeatCount() == 0) action(mapped, inputId, true);
        return true;
    }
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        String inputId = event.getDeviceId() + ":key:" + keyCode;
        if (consumedKeys.remove(inputId)) {
            heldShift.remove(inputId); syncView(); return true;
        }
        return super.onKeyUp(keyCode, event);
    }
    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if (!isInputViewShown() || !event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                || event.getAction() != MotionEvent.ACTION_MOVE) return super.onGenericMotionEvent(event);
        ControllerProfile p = profile(event.getDevice());
        boolean handled = false;
        for (ControllerInput.Change change : controllerInput.motion(event)) {
            String inputId = event.getDeviceId() + ":" + change.token;
            if (!change.pressed) heldShift.remove(inputId);
            String mapped = p.bindings.get(change.token);
            if (mapped != null) { action(mapped, inputId, change.pressed); handled = true; }
        }
        syncView();
        return handled || super.onGenericMotionEvent(event);
    }
}
