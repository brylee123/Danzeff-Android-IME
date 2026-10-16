package dev.danzeff.android;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import com.google.android.material.button.MaterialButton;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** System IME: no network, background processing, typed-text collection or logging. */
public class DanzeffImeService extends InputMethodService implements DanzeffKeyboardView.Listener {
    private DanzeffKeyboardView keyboard;
    private FrameLayout keyboardContainer;
    private LinearLayout toolbar;
    private LinearLayout suggestionBar;

    private final EditingEngine editing = new EditingEngine(this::getCurrentInputConnection);
    private boolean editingTools;
    private boolean autoCapsSuppressed;
    private boolean capsLock;

    private final Handler repeatHandler = new Handler(Looper.getMainLooper());
    private String repeatInput;
    private final Runnable deleteRepeat =
            new Runnable() {
                @Override
                public void run() {
                    if (repeatInput == null || !isInputViewShown()) {
                        stopDeleteRepeat();
                        return;
                    }
                    onBackspace();
                    repeatHandler.postDelayed(this, 80);
                }
            };

    private void stopDeleteRepeat() {
        repeatInput = null;
        repeatHandler.removeCallbacks(deleteRepeat);
    }

    private boolean numbers;
    private boolean extraSymbols;
    private boolean shift;
    private ControllerProfiles profiles;
    private final ControllerInput controllerInput = new ControllerInput();
    private final Set<String> heldShift = new HashSet<>();
    private final Set<String> consumedKeys = new HashSet<>();
    private final Map<String, String> lastInput = new HashMap<>();
    private final Map<String, Long> lastActionTime = new HashMap<>();
    private InputManager inputManager;

    private final InputManager.InputDeviceListener deviceListener =
            new InputManager.InputDeviceListener() {
                @Override
                public void onInputDeviceAdded(final int id) {
                    if (keyboard != null) {
                        keyboard.resetTouchSelection();
                    }
                    syncView();
                }

                @Override
                public void onInputDeviceChanged(final int id) {
                    onInputDeviceRemoved(id);
                }

                @Override
                public void onInputDeviceRemoved(final int id) {
                    final String prefix = id + ":";
                    heldShift.removeIf((final String token) -> token.startsWith(prefix));
                    consumedKeys.removeIf((final String token) -> token.startsWith(prefix));
                    if (repeatInput != null && repeatInput.startsWith(prefix)) {
                        stopDeleteRepeat();
                    }
                    controllerInput.forget(id);
                    if (keyboard != null) {
                        keyboard.resetTouchSelection();
                    }
                    syncView();
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        inputManager = (InputManager) getSystemService(Context.INPUT_SERVICE);
        if (inputManager != null) {
            inputManager.registerInputDeviceListener(deviceListener, null);
        }
    }

    @Override
    public void onDestroy() {
        stopDeleteRepeat();
        editing.reset();
        clearTransientUi();
        if (keyboard != null) {
            keyboard.resetTouchSelection();
        }
        if (inputManager != null) {
            inputManager.unregisterInputDeviceListener(deviceListener);
        }
        super.onDestroy();
    }

    private int row = 1;
    private int col = 1;

    @Override
    public View onCreateInputView() {
        profiles = new ControllerProfiles(this);
        keyboard = new DanzeffKeyboardView(this, this);
        syncView();
        final FrameLayout container = new FrameLayout(this);
        keyboardContainer = container;
        container.setBackgroundColor(KeyboardAppearance.background(this));
        final LinearLayout stack = new LinearLayout(this);
        stack.setOrientation(LinearLayout.VERTICAL);
        suggestionBar = new LinearLayout(this);
        suggestionBar.setOrientation(LinearLayout.HORIZONTAL);
        stack.addView(suggestionBar);
        final HorizontalScrollView toolsScroll = new HorizontalScrollView(this);
        toolsScroll.setHorizontalScrollBarEnabled(false);
        toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolsScroll.addView(toolbar);
        stack.addView(toolsScroll);
        stack.addView(keyboard);
        container.addView(
                stack,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT));
        container.setOnApplyWindowInsetsListener(
                (final View view, final WindowInsets insets) -> {
                    final int bottom =
                            Build.VERSION.SDK_INT >= 30
                                    ? insets.getInsets(WindowInsets.Type.navigationBars()).bottom
                                    : insets.getSystemWindowInsetBottom();
                    // Reserve a separate strip for Android's dismiss/picker buttons as well as its
                    // gesture bar.
                    final int shortcuts =
                            Math.round(48 * getResources().getDisplayMetrics().density);
                    view.setPadding(0, 0, 0, Math.max(bottom, shortcuts));
                    return insets;
                });
        refreshAppearance();
        syncView();
        return container;
    }

    @Override
    public void onStartInput(final EditorInfo info, final boolean restarting) {
        super.onStartInput(info, restarting);
        editing.start(info);
        loadTypingSettings();
        stopDeleteRepeat();
        numbers = editing.policy.numeric;
        shift = extraSymbols = autoCapsSuppressed = capsLock = false;
        heldShift.clear();
        consumedKeys.clear();
        controllerInput.reset();
        row = col = 1;
        if (keyboard != null) {
            keyboard.resetTouchSelection();
        }
        syncView();
    }

    @Override
    public void onStartInputView(final EditorInfo info, final boolean restarting) {
        super.onStartInputView(info, restarting);
        refreshAppearance();
        editing.start(info);
        loadTypingSettings();
        stopDeleteRepeat();
        editingTools = false;
        autoCapsSuppressed = capsLock = false;
        numbers = editing.policy.numeric;
        extraSymbols = false;
        shift = false;
        row = 1;
        col = 1;
        controllerInput.reset();
        heldShift.clear();
        consumedKeys.clear();
        lastInput.clear();
        lastActionTime.clear();
        if (keyboard != null) {
            keyboard.resetTouchSelection();
        }
        syncView();
    }

    private void refreshAppearance() {
        if (keyboard != null) {
            keyboard.refreshAppearance();
        }
        final int background = KeyboardAppearance.background(this);
        if (keyboardContainer != null) {
            keyboardContainer.setBackgroundColor(background);
        }
        if (getWindow() != null && getWindow().getWindow() != null) {
            final Window window = getWindow().getWindow();
            window.setNavigationBarColor(background);
            final int flags = window.getDecorView().getSystemUiVisibility();
            window.getDecorView()
                    .setSystemUiVisibility(
                            KeyboardAppearance.dark(this)
                                    ? flags & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                                    : flags | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    @Override
    public void onConfigurationChanged(final Configuration configuration) {
        super.onConfigurationChanged(configuration);
        stopDeleteRepeat();
        if (keyboard != null) {
            keyboard.resetTouchSelection();
        }
        refreshAppearance();
        syncView();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        // Keeps the field visible on most landscape screens, including foldables.
        return false;
    }

    int currentLayer() {
        return extraSymbols
                ? KeyboardLayout.EXTRA_SYMBOLS
                : KeyboardLayout.layer(
                        numbers,
                        shift
                                || !heldShift.isEmpty()
                                || !numbers && capsLock
                                || !numbers && !autoCapsSuppressed && editing.capsMode() != 0);
    }

    private void syncView() {
        loadTypingSettings();
        if (keyboard != null) {
            keyboard.setEditorPolicy(editing.policy);
            keyboard.updateState(currentLayer(), row, col);
        }
        updateToolbar();
    }

    @Override
    public void onChooseGroup(final int newRow, final int newCol) {
        row = Math.max(0, Math.min(2, newRow));
        col = Math.max(0, Math.min(2, newCol));
        syncView();
    }

    @Override
    public void onChooseSlot(final int slot) {
        final String text = editing.policy.character(currentLayer(), row, col, slot);
        if (text.isEmpty()) {
            return;
        }
        if ("\b".equals(text)) {
            deleteCharacter();
        } else {
            commit(text);
        }
        autoCapsSuppressed = false;
        syncView();
        // Touch shift is one-shot; mapped hold-shift inputs remain active until released.
        if (shift && !numbers) {
            shift = false;
            syncView();
        }
    }

    @Override
    public void onBackspace() {
        if (editing.delete()) {
            feedback();
        }
        autoCapsSuppressed = false;
        syncView();
    }

    @Override
    public void onSpace() {
        if (editing.space(SystemClock.uptimeMillis())) {
            feedback();
        }
        autoCapsSuppressed = false;
        syncView();
    }

    @Override
    public void onEnter() {
        final boolean accepted = editing.enter();
        if (accepted) {
            feedback();
        } else if (editing.policy.numeric
                && editing.policy.actionId == 0
                && (editing.policy.action == EditorInfo.IME_ACTION_NONE
                        || editing.policy.action == EditorInfo.IME_ACTION_UNSPECIFIED)
                && (editing.policy.options & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            requestHideSelf(0);
        }
        autoCapsSuppressed = false;
        syncView();
    }

    @Override
    public void onMoveCursor(final int direction) {
        editing.move(direction);
        autoCapsSuppressed = false;
        syncView();
    }

    @Override
    public void onShift() {
        if (numbers) {
            if (extraSymbols) {
                extraSymbols = false;
                shift = false;
            } else if (shift) {
                extraSymbols = true;
                shift = false;
            } else {
                shift = true;
            }
        } else {
            if (capsLock) {
                capsLock = false;
                shift = false;
                autoCapsSuppressed = true;
            } else if (!shift
                    && heldShift.isEmpty()
                    && !autoCapsSuppressed
                    && editing.capsMode() != 0) {
                autoCapsSuppressed = true;
            } else {
                shift = !shift;
                autoCapsSuppressed = false;
            }
        }
        if (keyboard != null) {
            keyboard.resetTouchSelection();
        }
        syncView();
    }

    public void onCapsLock() {
        capsLock = !capsLock;
        shift = false;
        autoCapsSuppressed = false;
        syncView();
    }

    public void onSymbols() {
        numbers = true;
        shift = true;
        extraSymbols = false;
        resetPage();
    }

    public void onExtraSymbols() {
        numbers = true;
        shift = false;
        extraSymbols = true;
        resetPage();
    }

    private void resetPage() {
        row = col = 1;
        if (keyboard != null) {
            keyboard.resetTouchSelection();
        }
        syncView();
    }

    @Override
    public void onNumbers() {
        numbers = !numbers;
        extraSymbols = false;
        shift = false;
        resetPage();
    }

    @Override
    public void onSwitchKeyboard() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || !switchToNextInputMethod(false)) {
            final InputMethodManager imm =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
            }
        }
    }

    private void commit(final String text) {
        if (editing.type(text)) {
            feedback();
        }
        syncView();
    }

    private void deleteCharacter() {
        onBackspace();
    }

    private void feedback() {
        if (keyboard != null) {
            keyboard.notifyCommitted();
        }
    }

    private void clearTransientUi() {
        if (suggestionBar != null) {
            suggestionBar.removeAllViews();
            suggestionBar.setVisibility(View.GONE);
        }
        if (toolbar != null) {
            toolbar.removeAllViews();
        }
    }

    private void loadTypingSettings() {
        final SharedPreferences prefs = getSharedPreferences("danzeff_preferences", 0);
        editing.suggestionsEnabled = prefs.getBoolean("suggestions", true);
        editing.autocorrectEnabled = prefs.getBoolean("autocorrect", false);
        editing.capitalizationEnabled = prefs.getBoolean("capitalization", true);
        editing.punctuationEnabled = prefs.getBoolean("punctuation", true);
    }

    @Override
    public void onUpdateSelection(
            final int oldStart,
            final int oldEnd,
            final int start,
            final int end,
            final int candidatesStart,
            final int candidatesEnd) {
        super.onUpdateSelection(oldStart, oldEnd, start, end, candidatesStart, candidatesEnd);
        editing.selectionChanged(start, end);
        syncView();
    }

    @Override
    public void onFinishInputView(final boolean finishingInput) {
        stopDeleteRepeat();
        editing.reset();
        clearTransientUi();
        if (keyboard != null) {
            keyboard.resetTouchSelection();
        }
        editingTools = false;
        super.onFinishInputView(finishingInput);
    }

    private Button tool(final String label, final Runnable action) {
        final MaterialButton button = AppUi.button(this, label);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(13);
        button.setTypeface(getResources().getFont(R.font.google_sans_medium));
        button.setTextColor(KeyboardAppearance.dark(this) ? 0xFFE8EAED : 0xFF202124);
        final int padding = Math.round(8 * getResources().getDisplayMetrics().density);
        button.setPadding(padding, 0, padding, 0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(Math.round(48 * getResources().getDisplayMetrics().density));
        button.setMinimumWidth(0);
        button.setOnClickListener(
                (final View v) -> {
                    action.run();
                    syncView();
                });
        button.setHeight(Math.round(40 * getResources().getDisplayMetrics().density));
        final LinearLayout.LayoutParams margins =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        Math.round(40 * getResources().getDisplayMetrics().density));
        final int gap = Math.round(2 * getResources().getDisplayMetrics().density);
        margins.setMargins(gap, gap, gap, gap);
        button.setLayoutParams(margins);
        return button;
    }

    private void updateToolbar() {
        if (toolbar == null || suggestionBar == null) {
            return;
        }
        toolbar.removeAllViews();
        suggestionBar.removeAllViews();
        toolbar.addView(tool(editingTools ? "Back" : "Edit", () -> editingTools = !editingTools));
        if (editingTools) {
            toolbar.addView(tool("←", () -> onMoveCursor(-1)));
            toolbar.addView(tool("→", () -> onMoveCursor(1)));
            toolbar.addView(
                    tool(editing.selecting() ? "Select ON" : "Select", editing::toggleSelection));
            toolbar.addView(
                    tool("Select all", () -> editing.contextAction(android.R.id.selectAll)));

        } else {
            if (!numbers) {
                toolbar.addView(tool(capsLock ? "Caps ON" : "Caps", this::onCapsLock));
            }
            for (final String shortcut : editing.policy.shortcuts()) {
                toolbar.addView(tool(shortcut, () -> commit(shortcut)));
            }
            toolbar.addView(
                    tool(
                            "Settings",
                            () -> {
                                final Intent intent =
                                        new Intent(this, TypingSettingsActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }));
        }
        if (editingTools) {
            for (final int id :
                    new int[] {android.R.id.copy, android.R.id.cut, android.R.id.paste}) {
                final String label =
                        id == android.R.id.copy ? "Copy" : id == android.R.id.cut ? "Cut" : "Paste";
                final Button button = tool(label, () -> editing.contextAction(id));
                addBarButton(button);
            }
            suggestionBar.setVisibility(View.VISIBLE);
            return;
        }
        final String word = editing.currentWord();
        final List<String> suggestions = editing.suggestions();
        for (final String suggestion : suggestions) {
            final Button button =
                    tool(
                            suggestion,
                            () -> {
                                if (editing.choose(word, suggestion)) {
                                    feedback();
                                }
                            });
            addBarButton(button);
        }
        suggestionBar.setVisibility(suggestions.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void addBarButton(final Button button) {
        final LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0, Math.round(40 * getResources().getDisplayMetrics().density), 1);
        final int gap = Math.round(2 * getResources().getDisplayMetrics().density);
        params.setMargins(gap, gap, gap, gap);
        suggestionBar.addView(button, params);
    }

    @Override
    public boolean onEvaluateInputViewShown() {
        if (super.onEvaluateInputViewShown()) {
            return true;
        }
        // This IME needs its character grid even when a physical keypad is attached.
        return true;
    }

    @Override
    public void onFinishInput() {
        stopDeleteRepeat();
        editing.reset();
        clearTransientUi();
        if (keyboard != null) {
            keyboard.resetTouchSelection();
        }
        heldShift.clear();
        consumedKeys.clear();
        controllerInput.reset();
        super.onFinishInput();
    }

    private ControllerProfile profile(final InputDevice device) {
        if (profiles == null) {
            profiles = new ControllerProfiles(this);
        }
        return profiles.active(
                ControllerProfiles.descriptor(device), ControllerProfiles.deviceName(device));
    }

    private void action(final String action, final String inputId, final boolean pressed) {
        if ("shift_hold".equals(action)) {
            if (pressed) {
                heldShift.add(inputId);
            } else {
                heldShift.remove(inputId);
            }
            syncView();
            return;
        }
        if (!pressed) {
            if (inputId.equals(repeatInput)) {
                stopDeleteRepeat();
            }
            return;
        }
        if (action == null) {
            return;
        }
        // Some controllers report the same D-pad press as both a key and a hat axis.
        final String device = inputId.substring(0, inputId.indexOf(':'));
        final String actionId = device + ":" + action;
        final String previous = lastInput.get(actionId);
        final boolean key = inputId.contains(":key:");
        final boolean previousKey = previous != null && previous.contains(":key:");

        final long now = SystemClock.uptimeMillis();
        if (previous != null
                && key != previousKey
                && now - lastActionTime.getOrDefault(actionId, 0L) < 50) {
            return;
        }
        lastInput.put(actionId, inputId);
        lastActionTime.put(actionId, now);
        if (action.startsWith("slot")) {
            onChooseSlot(Integer.parseInt(action.substring(4)));
            return;
        }
        if (action.startsWith("group")) {
            final int group = Integer.parseInt(action.substring(5));
            onChooseGroup(group / 3, group % 3);
            return;
        }
        switch (action) {
            case "up":
                onChooseGroup(row - 1, col);
                break;
            case "down":
                onChooseGroup(row + 1, col);
                break;
            case "left":
                onChooseGroup(row, col - 1);
                break;
            case "right":
                onChooseGroup(row, col + 1);
                break;
            case "shift":
                onShift();
                break;
            case "caps_lock":
                onCapsLock();
                break;
            case "numbers":
                onNumbers();
                break;
            case "symbols":
                onSymbols();
                break;
            case "extra_symbols":
                onExtraSymbols();
                break;
            case "space":
                onSpace();
                break;
            case "delete":
                onBackspace();
                stopDeleteRepeat();
                repeatInput = inputId;
                repeatHandler.postDelayed(deleteRepeat, 450);
                break;
            case "enter":
                onEnter();
                break;
            case "newline":
                commit("\n");
                break;
            case "picker":
                onSwitchKeyboard();
                break;
            case "hide":
                requestHideSelf(0);
                break;
            case "cursor_left":
                cursor(KeyEvent.KEYCODE_DPAD_LEFT);
                break;
            case "cursor_right":
                cursor(KeyEvent.KEYCODE_DPAD_RIGHT);
                break;
        }
    }

    private void cursor(final int code) {
        onMoveCursor(code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        if (!isInputViewShown() || !ControllerInput.eligible(event)) {
            return super.onKeyDown(keyCode, event);
        }
        final String token = "key:" + keyCode;
        final String inputId = event.getDeviceId() + ":" + token;

        final String mapped = profile(event.getDevice()).bindings.get(token);
        if (mapped == null) {
            return super.onKeyDown(keyCode, event);
        }
        consumedKeys.add(inputId);
        if (event.getRepeatCount() == 0) {
            action(mapped, inputId, true);
        }
        return true;
    }

    @Override
    public boolean onKeyUp(final int keyCode, final KeyEvent event) {
        final String inputId = event.getDeviceId() + ":key:" + keyCode;
        if (consumedKeys.remove(inputId)) {
            if (inputId.equals(repeatInput)) {
                stopDeleteRepeat();
            }
            heldShift.remove(inputId);
            syncView();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(final MotionEvent event) {
        if (!isInputViewShown()
                || !event.isFromSource(InputDevice.SOURCE_JOYSTICK)
                || event.getAction() != MotionEvent.ACTION_MOVE) {
            return super.onGenericMotionEvent(event);
        }
        final ControllerProfile p = profile(event.getDevice());
        boolean handled = false;
        for (final ControllerInput.Change change : controllerInput.motion(event)) {
            final String inputId = event.getDeviceId() + ":" + change.token;
            if (!change.pressed) {
                heldShift.remove(inputId);
            }
            final String mapped = p.bindings.get(change.token);
            if (mapped != null) {
                action(mapped, inputId, change.pressed);
                handled = true;
            }
        }
        syncView();
        return handled || super.onGenericMotionEvent(event);
    }
}
