package dev.danzeff.android;

/** Regression checks for conflicts and independence of saved profile copies. */
public final class ControllerProfileCheck {
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    public static void main(String[] args) {
        ControllerProfile first = new ControllerProfile();
        first.id = "first"; first.name = "Controller"; first.device = "usb-device"; first.deviceName = "USB pad";
        first.bind("key:96", "slot2"); first.bind("axis:17:+", "shift_hold");
        ControllerProfile copy = first.copy("copy", "Custom");
        check("slot2".equals(copy.bind("key:96", "space")), "A reassignment must report the displaced action");
        check("slot2".equals(first.bindings.get("key:96")), "Changing a copy must not change the original");
        check("space".equals(copy.bindings.get("key:96")), "One input must have exactly one action");
        copy.bind("key:97", "space"); copy.clear("space");
        check(!copy.bindings.containsValue("space"), "Clearing an action must remove all its inputs");
        check("shift_hold".equals(copy.bindings.get("axis:17:+")), "Clearing space must preserve other mappings");
        check(first.device.equals(copy.device) && !first.id.equals(copy.id), "Copies must retain device ownership with a new identity");
        try { copy.bind("key:99", "not-an-action"); throw new AssertionError("Invalid action accepted"); }
        catch (IllegalArgumentException expected) {}
        check(ControllerProfile.validAction("newline") && ControllerProfile.validAction("cursor_left")
            && ControllerProfile.validAction("hide"), "PSP actions must remain mappable");
        System.out.println("PASS: profile copies, conflicts, clearing, device ownership and PSP actions");
    }
}
