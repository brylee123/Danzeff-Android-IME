package dev.danzeff.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Preferences contain configuration only, never typed text. */
public final class ControllerProfiles {
    private final SharedPreferences preferences;

    public ControllerProfiles(final Context context) {
        preferences = context.getSharedPreferences("controller_profiles", Context.MODE_PRIVATE);
    }

    public List<ControllerProfile> all() {
        final List<ControllerProfile> result = new ArrayList<>();
        try {
            final JSONArray list = new JSONArray(preferences.getString("profiles", "[]"));
            for (int i = 0; i < list.length(); i++) {
                final JSONObject json = list.getJSONObject(i);
                final ControllerProfile p =
                        new ControllerProfile(
                                json.getString("id"),
                                json.getString("name"),
                                json.getString("device"),
                                json.getString("deviceName"));
                final JSONObject bindings = json.getJSONObject("bindings");
                final Iterator<String> keys = bindings.keys();
                while (keys.hasNext()) {
                    final String token = keys.next();
                    final String action = bindings.getString(token);

                    if (ControllerProfile.validAction(action)) {
                        p.bindings.put(token, action);
                    }
                }
                result.add(p);
            }
        } catch (final JSONException ignored) {
            /* Invalid configuration falls back to defaults. */
        }
        return result;
    }

    public ControllerProfile active(final String device, final String name) {
        final String id = preferences.getString("active:" + device, "");
        for (final ControllerProfile p : all()) {
            if (device.equals(p.device) && id.equals(p.id)) {
                return p;
            }
        }
        return defaults(device, name);
    }

    public void save(final ControllerProfile profile) {
        final List<ControllerProfile> list = all();
        list.removeIf((final ControllerProfile p) -> p.id.equals(profile.id));
        list.add(profile);
        final JSONArray json = new JSONArray();
        try {
            for (final ControllerProfile p : list) {
                final JSONObject item = new JSONObject();
                item.put("id", p.id);
                item.put("name", p.name);
                item.put("device", p.device);
                item.put("deviceName", p.deviceName);
                item.put("bindings", new JSONObject(p.bindings));
                json.put(item);
            }
        } catch (final JSONException e) {
            throw new IllegalStateException(e);
        }
        preferences
                .edit()
                .putString("profiles", json.toString())
                .putString("active:" + profile.device, profile.id)
                .apply();
    }

    public static ControllerProfile defaults(final String descriptor, final String name) {
        final ControllerProfile p =
                new ControllerProfile(
                        UUID.randomUUID().toString(), "PSP defaults", descriptor, name);
        final int[] buttons = {
            KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_B
        };
        for (int i = 0; i < 4; i++) {
            p.bind("key:" + buttons[i], "slot" + i);
        }
        p.bind("key:" + KeyEvent.KEYCODE_BUTTON_L1, "numbers");
        p.bind("key:" + KeyEvent.KEYCODE_BUTTON_R1, "shift_hold");
        p.bind("key:" + KeyEvent.KEYCODE_BUTTON_START, "enter");
        p.bind("key:" + KeyEvent.KEYCODE_BUTTON_SELECT, "hide");
        p.bind("key:" + KeyEvent.KEYCODE_DPAD_UP, "delete");
        p.bind("key:" + KeyEvent.KEYCODE_DPAD_DOWN, "newline");
        p.bind("key:" + KeyEvent.KEYCODE_DPAD_LEFT, "cursor_left");
        p.bind("key:" + KeyEvent.KEYCODE_DPAD_RIGHT, "cursor_right");
        p.bind("axis:15:-", "cursor_left");
        p.bind("axis:15:+", "cursor_right");
        p.bind("axis:16:-", "delete");
        p.bind("axis:16:+", "newline");
        // A numeric keypad selects the corresponding physical position in the grid.
        final int[] keypad = {7, 8, 9, 4, 5, 6, 1, 2, 3};
        for (int i = 0; i < 9; i++) {
            p.bind("stick:" + i, "group" + i);
            p.bind("key:" + (KeyEvent.KEYCODE_NUMPAD_0 + keypad[i]), "group" + i);
        }
        p.bind("key:" + KeyEvent.KEYCODE_NUMPAD_DIVIDE, "slot0");
        p.bind("key:" + KeyEvent.KEYCODE_NUMPAD_MULTIPLY, "slot1");
        p.bind("key:" + KeyEvent.KEYCODE_NUMPAD_SUBTRACT, "slot2");
        p.bind("key:" + KeyEvent.KEYCODE_NUMPAD_ADD, "slot3");
        p.bind("key:" + KeyEvent.KEYCODE_NUMPAD_ENTER, "enter");
        p.bind("key:" + KeyEvent.KEYCODE_NUMPAD_DOT, "delete");
        p.bind("key:" + KeyEvent.KEYCODE_NUMPAD_0, "space");
        return p;
    }

    public static String descriptor(final InputDevice device) {
        return device == null ? "virtual" : device.getDescriptor();
    }

    public static String deviceName(final InputDevice device) {
        return device == null ? "Virtual input" : device.getName();
    }
}
