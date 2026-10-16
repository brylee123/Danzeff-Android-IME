package dev.danzeff.android;

import android.content.DialogInterface;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Local profile editor. Capture is limited to the explicit listening dialog. */
public final class ControllerSettingsActivity extends ThemedActivity {
    private ControllerProfiles store;
    private ControllerProfile profile;
    private LinearLayout content;
    private AlertDialog listening;
    private String pendingAction;
    private final ControllerInput inputs = new ControllerInput();
    private int ignoredKey = -1;

    @Override
    public void onCreate(final Bundle state) {
        super.onCreate(state);
        store = new ControllerProfiles(this);
        if (state != null) {
            final String descriptor = state.getString("device");
            if (descriptor != null) {
                profile = store.active(descriptor, state.getString("deviceName", "Input device"));
            }
        }
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        render();
    }

    @Override
    protected void onSaveInstanceState(final Bundle state) {
        super.onSaveInstanceState(state);
        if (profile != null) {
            state.putString("device", profile.device);
            state.putString("deviceName", profile.deviceName);
        }
    }

    private int dp(final int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void text(final String value, final int size) {
        final TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTypeface(getResources().getFont(R.font.google_sans_regular));
        view.setTextColor(AppUi.ink(this));
        view.setPadding(0, dp(10), 0, dp(10));
        content.addView(view);
    }

    private void button(final String label, final Runnable action) {
        final Button view = AppUi.button(this, label);
        view.setOnClickListener((final View v) -> action.run());
        content.addView(view);
    }

    private void render() {
        final int scrollPosition =
                content != null && content.getParent() instanceof ScrollView
                        ? ((ScrollView) content.getParent()).getScrollY()
                        : 0;
        final ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(KeyboardAppearance.background(this));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(28));
        scroll.addView(content);
        setContentView(scroll);
        scroll.post(() -> scroll.scrollTo(0, scrollPosition));

        text(
                "Connect a Bluetooth or USB device. Choose it below, or detect it by pressing a"
                        + " button. Profiles and changes are saved automatically on this device.",
                14);
        button(
                profile == null ? "Choose input device" : "Device: " + profile.deviceName,
                this::chooseDevice);
        button("Detect device from input", () -> listen(null));
        if (profile == null) {
            text("Choose a device to edit its mappings.", 16);
            return;
        }
        text("Active profile: " + profile.name, 20);
        button("Choose saved profile", this::chooseProfile);
        button("Save as new profile", () -> nameDialog(false));
        button("Rename profile", () -> nameDialog(true));
        button(
                "Restore PSP defaults",
                () ->
                        new MaterialAlertDialogBuilder(this)
                                .setMessage("Replace this profile's mappings with the defaults?")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton(
                                        "Restore",
                                        (final DialogInterface dialog, final int which) -> {
                                            profile.bindings.clear();
                                            profile.bindings.putAll(
                                                    ControllerProfiles.defaults(
                                                                    profile.device,
                                                                    profile.deviceName)
                                                            .bindings);
                                            store.save(profile);
                                            render();
                                        })
                                .show());
        text(
                "Tap an action, then press a button, move a stick, or squeeze a trigger. Long-press"
                        + " an action to clear its bindings. Several inputs can perform the same"
                        + " action; assigning an input to another action moves that input.",
                14);
        for (final String[] action : ControllerProfile.ACTIONS) {
            final List<String> bindings = new ArrayList<>();
            profile.bindings.forEach(
                    (final String token, final String mapped) -> {
                        if (action[0].equals(mapped)) {
                            bindings.add(ControllerInput.describe(token));
                        }
                    });
            final Button view = AppUi.button(this, "");
            view.setText(
                    action[1]
                            + "\n"
                            + (bindings.isEmpty() ? "Not mapped" : String.join(" • ", bindings)));
            view.setOnClickListener((final View v) -> listen(action[0]));
            view.setOnLongClickListener(
                    (final View v) -> {
                        profile.clear(action[0]);
                        store.save(profile);
                        render();
                        return true;
                    });
            content.addView(view);
        }
    }

    private void selectDevice(final String descriptor, final String name) {
        profile = store.active(descriptor, name);
        store.save(profile);
        render();
    }

    private void chooseDevice() {
        final List<String> descriptors = new ArrayList<>();
        final List<String> names = new ArrayList<>();

        for (final int id : InputDevice.getDeviceIds()) {
            final InputDevice device = InputDevice.getDevice(id);
            if (device != null
                    && !device.isVirtual()
                    && (ControllerInput.external(device)
                            || device.supportsSource(InputDevice.SOURCE_GAMEPAD)
                            || device.supportsSource(InputDevice.SOURCE_JOYSTICK))) {
                descriptors.add(device.getDescriptor());
                names.add(device.getName());
            }
        }
        for (final ControllerProfile saved : store.all()) {
            if (!descriptors.contains(saved.device)) {
                descriptors.add(saved.device);
                names.add(saved.deviceName + " (disconnected)");
            }
        }
        if (names.isEmpty()) {
            listen(null);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle("Input device")
                .setItems(
                        names.toArray(new String[0]),
                        (final DialogInterface dialog, final int which) ->
                                selectDevice(
                                        descriptors.get(which),
                                        names.get(which).replace(" (disconnected)", "")))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void chooseProfile() {
        final List<ControllerProfile> profiles = new ArrayList<>();
        for (final ControllerProfile saved : store.all()) {
            if (profile.device.equals(saved.device)) {
                profiles.add(saved);
            }
        }
        final String[] names =
                profiles.stream().map((final ControllerProfile p) -> p.name).toArray(String[]::new);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Profiles for " + profile.deviceName)
                .setItems(
                        names,
                        (final DialogInterface dialog, final int which) -> {
                            profile = profiles.get(which);
                            store.save(profile);
                            render();
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void nameDialog(final boolean rename) {
        final EditText name = new TextInputEditText(this);
        name.setSingleLine(true);
        final TextInputLayout nameBox = AppUi.input(this, "Profile name");
        nameBox.addView(name);
        name.setText(rename ? profile.name : profile.name + " copy");
        name.selectAll();
        final AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setTitle(rename ? "Rename profile" : "Save new profile")
                        .setView(nameBox)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Save", null)
                        .create();
        dialog.setOnShowListener(
                (final DialogInterface d) ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        (final View v) -> {
                                            final String value = name.getText().toString().trim();
                                            if (value.isEmpty()) {
                                                name.setError("Enter a profile name");
                                                return;
                                            }
                                            if (value.length() > 60) {
                                                name.setError("Use 60 characters or fewer");
                                                return;
                                            }
                                            for (final ControllerProfile saved : store.all()) {
                                                if (saved.device.equals(profile.device)
                                                        && saved.name.equalsIgnoreCase(value)
                                                        && !(rename
                                                                && saved.id.equals(profile.id))) {
                                                    name.setError(
                                                            "A profile with this name already"
                                                                    + " exists");
                                                    return;
                                                }
                                            }
                                            if (rename) {
                                                profile.name = value;
                                            } else {
                                                profile =
                                                        profile.copy(
                                                                UUID.randomUUID().toString(),
                                                                value);
                                            }
                                            store.save(profile);
                                            dialog.dismiss();
                                            render();
                                        }));
        dialog.show();
    }

    private void listen(final String action) {
        pendingAction = action;
        inputs.reset();
        listening =
                new AlertDialog(
                        this,
                        com.google.android.material.R.style
                                .ThemeOverlay_Material3_MaterialAlertDialog) {
                    @Override
                    public boolean dispatchGenericMotionEvent(final MotionEvent event) {
                        return captureMotion(event) || super.dispatchGenericMotionEvent(event);
                    }
                };
        listening.setTitle(
                action == null ? "Detect input device" : ControllerProfile.label(action));
        listening.setMessage(
                "Listening… Press a controller/keypad button, move a stick, or squeeze a trigger. "
                        + (action == null ? "" : "Use " + profile.deviceName + "."));
        listening.setButton(
                AlertDialog.BUTTON_NEGATIVE,
                "Cancel",
                (final DialogInterface dialog, final int which) -> dialog.dismiss());
        listening.setOnKeyListener(
                (final DialogInterface dialog, final int code, final KeyEvent event) ->
                        captureKey(event));
        listening.setOnDismissListener(
                (final DialogInterface dialog) -> {
                    // A delayed dismissal from an older dialog must not cancel a new capture.
                    if (listening == dialog) {
                        listening = null;
                        pendingAction = null;
                        inputs.reset();
                    }
                });
        listening.getWindow().addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        listening.show();
        listening
                .getWindow()
                .setBackgroundDrawable(
                        new InsetDrawable(
                                new MaterialAlertDialogBuilder(this).getBackground(), dp(24)));
    }

    private boolean captureKey(final KeyEvent event) {
        if (listening == null || !ControllerInput.eligible(event)) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (capture("key:" + event.getKeyCode(), event.getDevice())) {
                ignoredKey = event.getKeyCode();
            }
        }
        return true;
    }

    private boolean capture(final String token, final InputDevice device) {
        if (pendingAction == null) {
            selectDevice(
                    ControllerProfiles.descriptor(device), ControllerProfiles.deviceName(device));
        } else {
            if (!profile.device.equals(ControllerProfiles.descriptor(device))) {
                Toast.makeText(
                                this,
                                "Use the selected device, or cancel and detect another device.",
                                Toast.LENGTH_SHORT)
                        .show();
                return false;
            }
            final String previous = profile.bind(token, pendingAction);
            store.save(profile);
            Toast.makeText(
                            this,
                            previous != null && !previous.equals(pendingAction)
                                    ? "Moved input from " + ControllerProfile.label(previous)
                                    : "Mapping saved",
                            Toast.LENGTH_SHORT)
                    .show();
        }
        listening.dismiss();
        render();
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        if (event.getKeyCode() == ignoredKey) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                ignoredKey = -1;
            }
            return true;
        }
        if (captureKey(event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean captureMotion(final MotionEvent event) {
        if (listening != null && event.isFromSource(InputDevice.SOURCE_JOYSTICK)) {
            for (final ControllerInput.Change change : inputs.motion(event)) {
                if (change.pressed && !change.token.equals("stick:4")) {
                    capture(change.token, event.getDevice());
                    break;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean dispatchGenericMotionEvent(final MotionEvent event) {
        return captureMotion(event) || super.dispatchGenericMotionEvent(event);
    }

    @Override
    protected void onPause() {
        if (listening != null) {
            listening.dismiss();
        }
        ignoredKey = -1;
        super.onPause();
    }
}
