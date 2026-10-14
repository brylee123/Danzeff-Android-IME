package dev.danzeff.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.InputDevice;
import android.view.KeyEvent;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Preferences contain configuration only, never typed text. */
public final class ControllerProfiles {
    private final SharedPreferences preferences;
    public ControllerProfiles(Context context) {
        preferences = context.getSharedPreferences("controller_profiles", Context.MODE_PRIVATE);
    }
    public List<ControllerProfile> all() {
        List<ControllerProfile> result = new ArrayList<>();
        try {
            JSONArray list = new JSONArray(preferences.getString("profiles", "[]"));
            for (int i = 0; i < list.length(); i++) {
                JSONObject json = list.getJSONObject(i);
                ControllerProfile p = new ControllerProfile();
                p.id = json.getString("id"); p.name = json.getString("name");
                p.device = json.getString("device"); p.deviceName = json.getString("deviceName");
                JSONObject bindings = json.getJSONObject("bindings");
                Iterator<String> keys = bindings.keys();
                while (keys.hasNext()) {
                    String token = keys.next(), action = bindings.getString(token);
                    if (ControllerProfile.validAction(action)) p.bindings.put(token, action);
                }
                result.add(p);
            }
        } catch (JSONException ignored) { /* Invalid configuration falls back to defaults. */ }
        return result;
    }
    public ControllerProfile active(String device, String name) {
        String id = preferences.getString("active:" + device, "");
        for (ControllerProfile p : all()) if (device.equals(p.device) && id.equals(p.id)) return p;
        return defaults(device, name);
    }
    public void save(ControllerProfile profile) {
        List<ControllerProfile> list = all();
        list.removeIf(p -> p.id.equals(profile.id));
        list.add(profile);
        JSONArray json = new JSONArray();
        try {
            for (ControllerProfile p : list) {
                JSONObject item = new JSONObject();
                item.put("id", p.id); item.put("name", p.name); item.put("device", p.device);
                item.put("deviceName", p.deviceName); item.put("bindings", new JSONObject(p.bindings));
                json.put(item);
            }
        } catch (JSONException e) { throw new IllegalStateException(e); }
        preferences.edit().putString("profiles", json.toString())
            .putString("active:" + profile.device, profile.id).apply();
    }
    public static ControllerProfile defaults(String descriptor, String name) {
        ControllerProfile p = new ControllerProfile();
        p.id = UUID.randomUUID().toString(); p.name = "PSP defaults";
        p.device = descriptor; p.deviceName = name;
        int[] buttons = {KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B};
        for (int i = 0; i < 4; i++) p.bind("key:" + buttons[i], "slot" + i);
        p.bind("key:" + KeyEvent.KEYCODE_BUTTON_L1, "numbers");
        p.bind("key:" + KeyEvent.KEYCODE_BUTTON_R1, "shift_hold");
        p.bind("key:" + KeyEvent.KEYCODE_BUTTON_START, "enter");
        p.bind("key:" + KeyEvent.KEYCODE_BUTTON_SELECT, "hide");
        p.bind("key:" + KeyEvent.KEYCODE_DPAD_UP, "delete");
        p.bind("key:" + KeyEvent.KEYCODE_DPAD_DOWN, "newline");
        p.bind("key:" + KeyEvent.KEYCODE_DPAD_LEFT, "cursor_left");
        p.bind("key:" + KeyEvent.KEYCODE_DPAD_RIGHT, "cursor_right");
        p.bind("axis:15:-", "cursor_left"); p.bind("axis:15:+", "cursor_right");
        p.bind("axis:16:-", "delete"); p.bind("axis:16:+", "newline");
        // A numeric keypad selects the corresponding physical position in the grid.
        int[] keypad = {7,8,9,4,5,6,1,2,3};
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
    public static String descriptor(InputDevice device) {
        return device == null ? "virtual" : device.getDescriptor();
    }
    public static String deviceName(InputDevice device) {
        return device == null ? "Virtual input" : device.getName();
    }
}
