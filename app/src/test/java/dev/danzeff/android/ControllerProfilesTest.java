package dev.danzeff.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ControllerProfilesTest {
    private Context context;

    @Before
    public void clearTestPreferences() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences("controller_profiles", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
    }

    @Test
    public void profilesPersistIndependentlyPerDeviceAndCopiesDoNotOverwriteOriginals() {
        final ControllerProfiles store = new ControllerProfiles(context);
        final ControllerProfile a = store.active("usb-a", "Controller A");
        store.save(a);
        final ControllerProfile b = store.active("usb-b", "Controller B");
        store.save(b);
        final ControllerProfile custom = a.copy("custom-a", "Custom A");
        custom.bind("key:96", "space");
        store.save(custom);
        final ControllerProfiles reloaded = new ControllerProfiles(context);
        assertEquals("custom-a", reloaded.active("usb-a", "Controller A").id);
        assertEquals("space", reloaded.active("usb-a", "Controller A").bindings.get("key:96"));
        assertEquals("slot2", reloaded.active("usb-b", "Controller B").bindings.get("key:96"));
        assertEquals(
                "slot2",
                reloaded.all().stream()
                        .filter((final ControllerProfile p) -> p.id.equals(a.id))
                        .findFirst()
                        .get()
                        .bindings
                        .get("key:96"));
        custom.name = "Renamed A";
        store.save(custom);
        assertEquals("Renamed A", reloaded.active("usb-a", "Controller A").name);
        store.save(a);
        assertEquals("slot2", reloaded.active("usb-a", "Controller A").bindings.get("key:96"));
    }

    @Test
    public void captureCancelRenameCopyAndRestoreWorkThroughTheActualEditor() {
        final ControllerSettingsActivity activity =
                Robolectric.buildActivity(ControllerSettingsActivity.class).setup().get();
        click(activity.getWindow().getDecorView(), "Detect device from input");
        press(latestDialog(), KeyEvent.KEYCODE_BUTTON_A);
        shadowOf(Looper.getMainLooper()).idle();
        final ControllerProfiles store = new ControllerProfiles(context);
        final ControllerProfile defaults = store.active("virtual", "Virtual input");
        assertEquals("slot2", defaults.bindings.get("key:96"));
        click(activity.getWindow().getDecorView(), "Triangle / top character");
        final AlertDialog capture = latestDialog();
        assertTrue(capture.isShowing());
        capture.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("slot2", store.active("virtual", "Virtual input").bindings.get("key:96"));
        click(activity.getWindow().getDecorView(), "Triangle / top character");
        press(latestDialog(), KeyEvent.KEYCODE_BUTTON_A);
        assertEquals("slot0", store.active("virtual", "Virtual input").bindings.get("key:96"));
        click(activity.getWindow().getDecorView(), "Rename profile");
        final AlertDialog rename = latestDialog();
        find(rename.getWindow().getDecorView(), EditText.class).setText("My controller");
        rename.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertEquals("My controller", store.active("virtual", "Virtual input").name);
        click(activity.getWindow().getDecorView(), "Save as new profile");
        final AlertDialog copy = latestDialog();
        find(copy.getWindow().getDecorView(), EditText.class).setText("Alternate layout");
        copy.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertEquals(2, store.all().size());
        click(activity.getWindow().getDecorView(), "Restore PSP defaults");
        latestDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("slot2", store.active("virtual", "Virtual input").bindings.get("key:96"));
        final ControllerProfile original =
                store.all().stream()
                        .filter((final ControllerProfile p) -> p.name.equals("My controller"))
                        .findFirst()
                        .get();
        assertEquals("slot0", original.bindings.get("key:96"));
        activity.finish();
    }

    private static AlertDialog latestDialog() {
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }

    private static void press(final AlertDialog dialog, final int keyCode) {
        dialog.dispatchKeyEvent(
                new KeyEvent(
                        1,
                        1,
                        KeyEvent.ACTION_DOWN,
                        keyCode,
                        0,
                        0,
                        -1,
                        0,
                        0,
                        InputDevice.SOURCE_GAMEPAD));
        shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void aDelayedCancelCannotDisableNewCaptureAndTriggersCanBeLearned() {
        final ControllerSettingsActivity activity =
                Robolectric.buildActivity(ControllerSettingsActivity.class).setup().get();
        click(activity.getWindow().getDecorView(), "Detect device from input");
        press(latestDialog(), KeyEvent.KEYCODE_BUTTON_A);
        click(activity.getWindow().getDecorView(), "Triangle / top character");
        latestDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        // Open the replacement before the old dialog's dismissal callback runs.
        button(activity.getWindow().getDecorView(), "Triangle / top character").performClick();
        shadowOf(Looper.getMainLooper()).idle();
        press(latestDialog(), KeyEvent.KEYCODE_BUTTON_B);
        final ControllerProfiles store = new ControllerProfiles(context);
        assertEquals("slot0", store.active("virtual", "Virtual input").bindings.get("key:97"));
        click(activity.getWindow().getDecorView(), "Hold shift");
        final MotionEvent trigger = trigger(.8f);
        latestDialog().dispatchGenericMotionEvent(trigger);
        trigger.recycle();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(
                "shift_hold", store.active("virtual", "Virtual input").bindings.get("axis:17:+"));
        activity.finish();
    }

    @Test
    public void triggerHysteresisAvoidsRepeatedInputsAndReleasesHoldShift() {
        final ControllerInput decoder = new ControllerInput();
        MotionEvent event = trigger(.8f);
        assertTrue(decoder.motion(event).get(0).pressed);
        event.recycle();
        event = trigger(.8f);
        assertTrue(decoder.motion(event).isEmpty());
        event.recycle();
        event = trigger(.4f);
        assertTrue(decoder.motion(event).isEmpty());
        event.recycle();
        event = trigger(.2f);
        assertFalse(decoder.motion(event).get(0).pressed);
        event.recycle();
        event = trigger(.55f);
        assertTrue(decoder.motion(event).isEmpty());
        event.recycle();
        event = trigger(.8f);
        assertTrue(decoder.motion(event).get(0).pressed);
        event.recycle();
    }

    private static MotionEvent trigger(final float value) {
        final MotionEvent.PointerProperties pointer = new MotionEvent.PointerProperties();
        pointer.id = 0;
        final MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords();
        coords.setAxisValue(MotionEvent.AXIS_LTRIGGER, value);
        return MotionEvent.obtain(
                1,
                1,
                MotionEvent.ACTION_MOVE,
                1,
                new MotionEvent.PointerProperties[] {pointer},
                new MotionEvent.PointerCoords[] {coords},
                0,
                0,
                1,
                1,
                -1,
                0,
                InputDevice.SOURCE_JOYSTICK,
                0);
    }

    private static void click(final View view, final String prefix) {
        final Button button = button(view, prefix);
        assertNotNull("Button not found: " + prefix, button);
        button.performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static Button button(final View view, final String prefix) {
        if (view instanceof Button && ((Button) view).getText().toString().startsWith(prefix)) {
            return (Button) view;
        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                final Button result = button(((ViewGroup) view).getChildAt(i), prefix);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static <T> T find(final View view, final Class<T> type) {
        if (type.isInstance(view)) {
            return type.cast(view);
        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                final T result = find(((ViewGroup) view).getChildAt(i), type);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
