package dev.danzeff.android;

import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.os.Build;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Converts buttons, stick positions, D-pad hats and triggers into stable inputs. */
public final class ControllerInput {
    public static final class Change {
        public final String token; public final boolean pressed;
        Change(String token, boolean pressed) { this.token = token; this.pressed = pressed; }
    }
    private final Map<Integer, Set<String>> previous = new HashMap<>();
    public void reset() { previous.clear(); }
    public void forget(int deviceId) { previous.remove(deviceId); }
    public static boolean controller(KeyEvent event) {
        return event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_DPAD)
            || event.isFromSource(InputDevice.SOURCE_JOYSTICK);
    }
    public static boolean allowedKey(int code) {
        return code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_HOME
            && code != KeyEvent.KEYCODE_POWER && code != KeyEvent.KEYCODE_VOLUME_UP
            && code != KeyEvent.KEYCODE_VOLUME_DOWN && code != KeyEvent.KEYCODE_VOLUME_MUTE
            && code != KeyEvent.KEYCODE_UNKNOWN;
    }
    public static boolean eligible(KeyEvent event) {
        InputDevice device = event.getDevice();
        return allowedKey(event.getKeyCode()) && (controller(event)
            || (external(device) && event.isFromSource(InputDevice.SOURCE_KEYBOARD)));
    }
    public static boolean external(InputDevice device) {
        return device != null && (Build.VERSION.SDK_INT >= 29 ? device.isExternal() : !device.isVirtual());
    }
    public List<Change> motion(MotionEvent event) {
        List<Change> changes = new ArrayList<>();
        if (!event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return changes;
        Set<String> old = previous.getOrDefault(event.getDeviceId(), new HashSet<>());
        Set<String> now = new HashSet<>();
        InputDevice device = event.getDevice();
        boolean stick = device != null && device.getMotionRange(MotionEvent.AXIS_X, event.getSource()) != null
            && device.getMotionRange(MotionEvent.AXIS_Y, event.getSource()) != null;
        if (stick) {
            int col = direction(event.getAxisValue(MotionEvent.AXIS_X), old, "x");
            int row = direction(event.getAxisValue(MotionEvent.AXIS_Y), old, "y");
            if (col != 0) now.add("x" + col);
            if (row != 0) now.add("y" + row);
            String token = "stick:" + ((row + 1) * 3 + col + 1);
            // Ignore initial centered packets (e.g. a trigger packet after keypad selection).
            if (!token.equals("stick:4") || old.stream().anyMatch(t -> t.startsWith("stick:"))) now.add(token);
        }
        int[] axes = {MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_HAT_X,
            MotionEvent.AXIS_HAT_Y, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_GAS, MotionEvent.AXIS_BRAKE};
        for (int axis : axes) {
            float value = event.getAxisValue(axis);
            String negative = "axis:" + axis + ":-", positive = "axis:" + axis + ":+";
            if (value < -(old.contains(negative) ? .30f : .60f)) now.add(negative);
            if (value > (old.contains(positive) ? .30f : .60f)) now.add(positive);
        }
        for (String token : old) if (!now.contains(token) && !token.startsWith("x") && !token.startsWith("y")) changes.add(new Change(token, false));
        for (String token : now) if (!old.contains(token) && !token.startsWith("x") && !token.startsWith("y")) changes.add(new Change(token, true));
        previous.put(event.getDeviceId(), now);
        return changes;
    }
    private int direction(float value, Set<String> old, String axis) {
        if (value < -(old.contains(axis + "-1") ? .25f : .45f)) return -1;
        if (value > (old.contains(axis + "1") ? .25f : .45f)) return 1;
        return 0;
    }
    public static String describe(String token) {
        if (token.startsWith("key:")) return KeyEvent.keyCodeToString(Integer.parseInt(token.substring(4))).replace("KEYCODE_", "");
        if (token.startsWith("stick:")) {
            String[] positions = {"up-left", "up", "up-right", "left", "center", "right", "down-left", "down", "down-right"};
            return "Left stick " + positions[Integer.parseInt(token.substring(6))];
        }
        String[] parts = token.split(":");
        return MotionEvent.axisToString(Integer.parseInt(parts[1])).replace("AXIS_", "") + " " + parts[2];
    }
}
