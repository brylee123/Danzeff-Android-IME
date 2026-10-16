package dev.danzeff.android;

import java.util.LinkedHashMap;
import java.util.Map;

/** Device-specific bindings. One input has one action; actions may have aliases. */
public final class ControllerProfile {
    public final String id;
    public String name;
    public final String device;
    public final String deviceName;

    public final Map<String, String> bindings = new LinkedHashMap<>();
    public static final String[][] ACTIONS = {
        {"slot0", "Triangle / top character"},
        {"slot1", "Square / left character"},
        {"slot2", "Cross / bottom character"},
        {"slot3", "Circle / right character"},
        {"shift_hold", "Hold shift (PSP R trigger)"},
        {"shift", "Toggle / one-shot shift"},
        {"caps_lock", "Toggle caps lock"},
        {"numbers", "Switch letters / numbers (PSP L trigger)"},
        {"symbols", "Open symbols page"},
        {"extra_symbols", "Open extra symbols (Flipper EX+)"},
        {"space", "Space"},
        {"delete", "Delete / backspace"},
        {"newline", "New line (PSP D-pad down)"},
        {"enter", "Start / Enter or editor action"},
        {"cursor_left", "Move cursor left"},
        {"cursor_right", "Move cursor right"},
        {"hide", "Select / hide keyboard"},
        {"picker", "Switch keyboard"},
        {"up", "Move group up"},
        {"down", "Move group down"},
        {"left", "Move group left"},
        {"right", "Move group right"},
        {"group0", "Select top-left group"},
        {"group1", "Select top-middle group"},
        {"group2", "Select top-right group"},
        {"group3", "Select middle-left group"},
        {"group4", "Select center group"},
        {"group5", "Select middle-right group"},
        {"group6", "Select bottom-left group"},
        {"group7", "Select bottom-middle group"},
        {"group8", "Select bottom-right group"}
    };

    public ControllerProfile(
            final String id, final String name, final String device, final String deviceName) {
        this.id = id;
        this.name = name;
        this.device = device;
        this.deviceName = deviceName;
    }

    public static boolean validAction(final String action) {
        for (final String[] item : ACTIONS) {
            if (item[0].equals(action)) {
                return true;
            }
        }
        return false;
    }

    public String bind(final String input, final String action) {
        if (!validAction(action)) {
            throw new IllegalArgumentException("Unknown action");
        }
        return bindings.put(input, action);
    }

    public void clear(final String action) {
        bindings.values().removeIf(action::equals);
    }

    public ControllerProfile copy(final String newId, final String newName) {
        final ControllerProfile p = new ControllerProfile(newId, newName, device, deviceName);
        p.bindings.putAll(bindings);
        return p;
    }

    public static String label(final String action) {
        for (final String[] item : ACTIONS) {
            if (item[0].equals(action)) {
                return item[1];
            }
        }
        return action;
    }
}
