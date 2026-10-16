# Danzeff Android IME — proof of concept v0.7

An **actual Android system keyboard** (InputMethodService), not a mockup or a text-editor-only control. Original Danzeff-style 3x3 groups with top/left/bottom/right character slots, written in Java with Material 3 controls and native Markdown rendering.

## What works

- **System-wide** IME for Android 8.0+ (API 26+, 64-bit/32-bit Android compatible).
- **One-handed touch keyboard**, with or without a controller: touch a cell to expand it immediately, keep your finger down and swipe up/left/down/right, then release to type. The original cell stays locked even if the swipe crosses other cells.
- **Tap selection alongside swiping**: tap a cell, lift your finger, then tap a character in its enlarged popup. Both gestures are always available; no mode toggle is needed. The popup closes and the grid resets after each character. An empty numeric slot does not type; a canceled gesture or a swipe into the footer does not type.
- The full-width swipe/tap grid remains the same with a controller or keypad connected. Hardware profiles select groups and characters independently; there is no separate face-button panel or touch-mode toggle.
- PSP-style **lowercase, uppercase, digits, and symbols** pages (ASCII; layout from DanzeffOSLib's `modeChar`). One-shot shift for letters.
- Backspace, space, context-aware enter/IME action, keyboard picker, and switch-to-next-keyboard.
- **Bluetooth/USB controller and keypad profiles**: assign buttons, stick positions, D-pad hats and triggers to Danzeff actions, save copies, rename profiles and select an active profile independently for each input device. Actual Android controller mapping varies; needs hardware testing.
- Rounded Gboard-inspired keys and controls, bundled Google Sans typography, and Light/Dark/System appearance with a solid amber active cell. Footer controls sit above a dedicated system-navigation strip.
- An **in-app typing test**: prompted sentence, elapsed time, approximate completed-text WPM, text-vs-prompt-prefix accuracy, deletion count, manual reset. No test logs are saved.
- No network permission, analytics, typed-text storage or dictionary learning. Suggestions use a small bundled English starter lexicon; copy/cut/paste delegates to the focused editor.

## Build and install

1. Install **Android Studio** with the Android SDK **35** and JDK **17+**. Open the project folder. Android Studio may offer to provision Gradle if it is not installed.
2. The project specifies **Android Gradle Plugin 8.9.2** with a **Gradle 8.11.1 wrapper**. In Android Studio's Gradle settings, choose a JDK 17–23 (JDK 21 was used for the verified build); newer Studio installations may bundle an incompatible JDK. The wrapper verifies its distribution checksum.
3. Allow Android Studio's Gradle sync; press **Run** with your Android phone connected by USB debugging, or choose **Build > Build APK(s)**. A successful debug build produces `app/build/outputs/apk/debug/app-debug.apk`.
4. Launch **Danzeff Keyboard** on the phone. Tap **Enable keyboard in Android Settings** and enable **Danzeff Keyboard**. Android shows a standard warning for all third-party keyboards. Tap **Choose Danzeff as the current keyboard** and select it.
5. Try the built-in typing experiment or open any ordinary text field. Use the keyboard's **🌐** button to switch back to your usual keyboard.

**Optional CLI:** Set `JAVA_HOME` to your compatible JDK, then run `gradlew.bat :app:assembleDebug` on Windows or `./gradlew :app:assembleDebug` on macOS/Linux. Debug builds are locally signed for testing. Generated APKs, build output, SDK paths and signing keys are excluded from Git.

## Controller and keypad mapping

Open **Controller & keypad profiles** from the app. Choose a connected device or tap **Detect device from input**. Tap an action to listen, then press a button, move a stick or squeeze a trigger. Back, Home, Power and volume controls are reserved for Android. Cancel closes the listener without changing any mapping. Long-press an action to clear its bindings.

Changes save automatically. **Save as new profile** creates an independent copy; **Rename profile** changes its name; **Choose saved profile** activates another profile for the same device. **Restore PSP defaults** resets only the current profile. Profiles are stored locally and selected by Android's input-device descriptor, not its transient connection ID. Android may identify one physical controller differently over Bluetooth and USB; those connections then have separate profiles. Disconnected devices remain editable. There is no typed-text storage or upload.

The defaults follow the [PSP Danzeff input implementation](https://github.com/PSP-Archive/DanzeffOSLib/blob/main/danzeff.c): left stick selects a group; Y/X/A/B correspond to triangle/square/cross/circle; L1 changes letters/numbers; hold R1 for uppercase/symbols. D-pad up deletes, down inserts a newline, and left/right move the cursor. Start and Select are application-defined outputs in the PSP library; on Android they default to editor Enter and hiding the IME. Additional mappable actions include space, keyboard switching, one-shot shift, relative group movement, and each of the nine group positions.

Keypad defaults: 7/8/9 selects the top row, 4/5/6 the middle row and 1/2/3 the bottom row. Divide/multiply/minus/plus select top/left/bottom/right characters. Keypad 0 inserts space, decimal deletes and keypad Enter submits the editor action. Controllers that emulate a physical keyboard can map their keys too. Unmapped physical keys pass through normally.

Stick directions are captured as the nine left-stick positions; other axes capture positive/negative activation. Inputs use a deadzone and hysteresis, and holding a button does not repeatedly insert characters. Hold-shift stays active until release and resets when its device disconnects. Controller firmware that reports both a key and a D-pad axis is debounced to avoid duplicate actions. Some applications do not send generic joystick events to the IME; physical-controller behavior still needs testing on the target Android device.

## Input specifics

Danzeff slots are **TOP / LEFT / BOTTOM / RIGHT**, in source order `TRIANGLE / SQUARE / CROSS / CIRCLE`. All nine cells always use the keyboard's full width, regardless of connected input devices. A selected cell expands to 165% of its usual dimensions, constrained to the grid so edge cells remain visible. Swipe direction is measured from the initial finger position; a 14dp minimum movement (or the device's touch slop, whichever is greater) distinguishes a swipe from a tap. Character selection commits on finger release. A second tap chooses a compass sector in the expanded cell; tapping its blank center does not insert text. To type **a**, touch the top-left cell and swipe left, or tap that cell and tap **a** in the enlarged popup. After insertion, the popup closes and the selected group returns to center. The center cell's top character is delete, bottom is space, left is M/m and right N/n.

Connecting or disconnecting a gamepad or keypad never changes the grid layout. Input-device changes and canceled touches discard incomplete gestures. Numbers, shift, delete, space, enter and the monochrome globe stay in the footer, above Android's navigation controls.

On the original numeric page, the number is usually on the **RIGHT** (circle); **0** is the bottom character of the bottom-right group. Shift while on the numeric page reveals symbols. Enter, space, delete have always-accessible dedicated footer buttons.

**Test fairness:** WPM shown in the app is `current committed characters / 5 / elapsed minutes`, not true gross keystroke WPM. Text accuracy is edit-distance similarity to an equal-length target prefix, not keystroke error rate. It does not record the complete event stream or keystrokes, and the timer cannot continue updating when the text is unchanged. For comparisons, complete the same sentence for both keyboard modes, and use a stopwatch or screen recording if necessary.

## Safety and current limitations

This is a research proof of concept, **not a polished daily-driver**. Touch target sizes, full TalkBack support, accessibility scaling, broader dictionaries, emoji entry and multilingual input require further work. Cursor editing, delete repeat, smart caps and starter offline suggestions are now available, with compatibility limits documented below. Gamepad support is implemented but **not yet validated on hardware**. Some fields/apps may not accept actions identically, and keyboard-switching behavior can vary by OEM.

All typed text is sent only to the currently focused Android text field using `InputConnection`; there is **no INTERNET permission**, no background service other than the Android IME service, and no text persistence. Android warns when enabling any third-party keyboard because such an IME has access to user input; do not enter credentials with an unreviewed build.

## Checks

Run `gradlew.bat :app:testDebugUnitTest :app:lintDebug` (or `./gradlew` on macOS/Linux) for Android profile-editor, persistence, trigger-release, capture-cancellation and keyboard-rendering checks. Robolectric and JUnit are test-only dependencies. The rendering test generates `app/build/reports/keyboard-preview.png`.

Run the dependency-free character-map test:

```bash
mkdir -p /tmp/danzeff-check
javac -d /tmp/danzeff-check app/src/main/java/dev/danzeff/android/KeyboardLayout.java tools/KeyboardLayoutCheck.java
java -cp /tmp/danzeff-check dev.danzeff.android.KeyboardLayoutCheck
```

## Credits and licensing

Thank you to **danzel** and **Jeff Chen**, the original Danzeff keyboard authors, **geecko (Clément G)** for Danzeff-G, and **Sakya** for the OSlib port.

Following [Danzeff Flipper Lab](https://github.com/brylee123/Danzeff-Flipper-Lab), new Android project code is licensed under [BSD 2-Clause](LICENSE), while derived Danzeff layout data retains the full [BSD 3-Clause notice](docs/licenses/DANZEFF-G-LICENSE.txt). See [third-party credits and layout provenance](docs/THIRD_PARTY.md). Attribution does not imply endorsement. Original PSP artwork and binaries are not bundled.

The APK includes the credits and full BSD notices, accessible through **Credits & licenses** in the app. Gradle wrapper licenses are preserved under `docs/licenses/`.

Android platform IME reference: https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method

## Roadmap

- v0.2: device testing on foldables, landscape, and mainstream controllers; touch feedback, rotation/resizing, custom mappings, larger touch targets
- v0.3: instrumented **opt-in** usability sessions for WPM, corrected/uncorrected errors and per-mode learning curves, without recording sensitive user text
- Later: accessibility support, smart punctuation, physical controller deadzone tuning, optional predictions, more layouts

## Keyboard appearance

Open the launcher app and choose **Keyboard appearance**: **Light**, **Dark**, or **System (auto)** (the default). The keyboard, expanded cells, controller toggle and footer use solid rounded Gboard-inspired shapes with the Danzeff amber accent. Google Sans Regular and Medium are bundled from Google's official v14.000 Android font release under SIL OFL 1.1; the full font notice is included in the app credits. System mode follows Android night mode, including changes while the keyboard is open.

## Symbols and extra punctuation

Tap **?123** for digits, then **SYM** for the original symbols page, **EX+** for the Flipper convenience symbols page, and **123** to return to digits. **ABC** returns to letters from any numeric/symbol page. Characters use the same swipe or two-tap gestures and the symbol page stays open after typing. EX+ includes @ # $ % ^ & * + - _ = ~ < > [ ] { } \ / | : ; ! ? , . quotes, backtick, parentheses, zero, delete and space. It uses the exact Danzeff Flipper Lab extra-page positions. This page adds convenient access to existing ASCII punctuation; Unicode currency/math and emoji are future work.

Controller profiles now offer **Open symbols page** and **Open extra symbols (Flipper EX+)** actions. Existing mappings and PSP hold-shift behavior remain available. The toggle-shift action cycles numeric pages; on letters it remains one-shot shift.

See [the daily-keyboard roadmap](docs/DAILY_KEYBOARD_ROADMAP.md) for prioritized proposed features.
## Daily-use features (v0.6)

- **Field-aware entry:** numeric/phone fields start on digits, including zero in the center-left slot. Signed/decimal fields expose minus/decimal point; phone, email, URL and password fields have relevant shortcut buttons. Manual page selection remains available. The Enter key uses Search/Send/Next/Go/Done or the editor's custom action; multiline/no-enter-action fields retain newline behavior.
- **Edit toolbar:** tap **Edit**, use left/right arrows, toggle **Select** to extend a selection, or choose Select all/Copy/Cut/Paste. The toolbar scrolls horizontally. Drag **Space** left/right to move the cursor without inserting a space. Editor context-menu actions handle clipboard access; the keyboard does not retain clipboard history.
- **Delete repeat:** hold the footer delete key or a controller input mapped to Delete. One deletion occurs immediately, repeat starts after 450 ms, and repeats every 80 ms. Release, leaving the key, cancel, device disconnect, view hide/detach or resize stops repeat.
- **Unicode editing:** ICU grapheme boundaries, with emoji joins for older Android, preserve combining accents, flags, skin tones and joined emoji. Context queries are bounded; unsupported or truncated context falls back to editor key handling. This depends on the host editor and Android's Unicode version.
- **Smart caps/punctuation:** honor capitalization context/flags, allow a manual lowercase override and explicit **Caps** lock, and optionally turn two spaces within 700 ms into a period plus space. Backspace can undo that transformation immediately. Literal/no-suggestions, password, numeric, phone, email and URL fields do not receive automatic text transformations.
- **Local English suggestions:** a small original bundled starter lexicon offers prefix/near-match suggestions. Tap a candidate to replace the current word and append space; stale candidate taps are rejected. No learning or typed-word persistence. Suggestion flags and private-learning requests are respected.
- **Optional reversible autocorrect:** off by default; when enabled, Space corrects only a unique one-edit dictionary match. Immediate Backspace restores the original word. New typing, field change or external cursor/selection changes invalidate undo. Editors without sufficient selection context do not receive automatic correction.
- **Feedback:** optional haptics (off by default, respecting Android's system setting), visible pressed footer states, swipe-sector highlights and a brief committed-cell outline. Configure under **Typing & feedback**, from the launcher or keyboard Settings button.

See [compatibility and reliability coverage](docs/COMPATIBILITY_MATRIX.md). These are prototype implementations, not a claim of comprehensive physical-device or app compatibility.

### Consistent Material appearance

All app screens, profile dialogs, typing switches and keyboard toolbar controls share the Light, Dark or System appearance preference, solid tonal surfaces and Google Sans typography. Credits and bundled help render Markdown natively, including headings, links and tables, without network permission.
