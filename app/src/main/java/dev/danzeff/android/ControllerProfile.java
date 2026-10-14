package dev.danzeff.android;

import java.util.LinkedHashMap;
import java.util.Map;

/** Device-specific bindings. One input has one action; actions may have aliases. */
public final class ControllerProfile {
    public String id, name, device, deviceName;
    public final Map<String, String> bindings = new LinkedHashMap<>();
    public static final String[][] ACTIONS = {
        {"slot0", "Triangle / top character"}, {"slot1", "Square / left character"},
        {"slot2", "Cross / bottom character"}, {"slot3", "Circle / right character"},
        {"shift_hold", "Hold shift (PSP R trigger)"}, {"shift", "Toggle / one-shot shift"},
        {"numbers", "Switch letters / numbers (PSP L trigger)"},
        {"space", "Space"}, {"delete", "Delete / backspace"}, {"newline", "New line (PSP D-pad down)"},
        {"enter", "Start / Enter or editor action"},
        {"cursor_left", "Move cursor left"}, {"cursor_right", "Move cursor right"},
        {"hide", "Select / hide keyboard"}, {"picker", "Switch keyboard"},
        {"up", "Move group up"}, {"down", "Move group down"},
        {"left", "Move group left"}, {"right", "Move group right"},
        {"group0", "Select top-left group"}, {"group1", "Select top-middle group"},
        {"group2", "Select top-right group"}, {"group3", "Select middle-left group"},
        {"group4", "Select center group"}, {"group5", "Select middle-right group"},
        {"group6", "Select bottom-left group"}, {"group7", "Select bottom-middle group"},
        {"group8", "Select bottom-right group"}
    };
    public static boolean validAction(String action) {
        for (String[] item : ACTIONS) if (item[0].equals(action)) return true;
        return false;
    }
    public String bind(String input, String action) {
        if (!validAction(action)) throw new IllegalArgumentException("Unknown action");
        return bindings.put(input, action);
    }
    public void clear(String action) { bindings.values().removeIf(action::equals); }
    public ControllerProfile copy(String newId, String newName) {
        ControllerProfile p = new ControllerProfile();
        p.id = newId; p.name = newName; p.device = device; p.deviceName = deviceName;
        p.bindings.putAll(bindings);
        return p;
    }
    public static String label(String action) {
        for (String[] item : ACTIONS) if (item[0].equals(action)) return item[1];
        return action;
    }
}
