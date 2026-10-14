# Danzeff Android IME — proof of concept v0.3

An **actual Android system keyboard** (InputMethodService), not a mockup or a text-editor-only control. Original Danzeff-style 3x3 groups and four face-button positions, written from scratch in Java with native Android APIs. No UI library or third-party runtime dependencies.

## What works

- **System-wide** IME for Android 8.0+ (API 26+, 64-bit/32-bit Android compatible).
- **One-handed touch keyboard** when no controller is connected: touch a cell to expand it immediately, keep your finger down and swipe up/left/down/right, then release to type. The original cell stays locked even if the swipe crosses other cells.
- **Tap selection alongside swiping**: tap a cell, lift your finger, then tap a character in its enlarged popup. Both gestures are always available; no mode toggle is needed. The popup closes and the grid resets after each character. An empty numeric slot does not type; a canceled gesture or a swipe into the footer does not type.
- With a controller connected, the original grid and four face buttons remain available, including its two-step/direct-touch header toggle. Saved profiles still handle physical input independently.
- PSP-style **lowercase, uppercase, digits, and symbols** pages (ASCII; layout from DanzeffOSLib's `modeChar`). One-shot shift for letters.
- Backspace, space, context-aware enter/IME action, keyboard picker, and switch-to-next-keyboard.
- **Bluetooth/USB controller and keypad profiles**: assign buttons, stick positions, D-pad hats and triggers to Danzeff actions, save copies, rename profiles and select an active profile independently for each input device. Actual Android controller mapping varies; needs hardware testing.
- Original-style solid gray cells, white lettering, black dividers and a solid amber active cell with dark lettering. Footer controls sit above a dedicated system-navigation strip.
- An **in-app typing test**: prompted sentence, elapsed time, approximate completed-text WPM, text-vs-prompt-prefix accuracy, deletion count, manual reset. No test logs are saved.
- No network permission, analytics, logging of typed content, clipboard reads, dictionaries, or cloud features.

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

Danzeff slots are **TOP / LEFT / BOTTOM / RIGHT**, in source order `TRIANGLE / SQUARE / CROSS / CIRCLE`. Without a controller, all nine cells use the keyboard's full width. A selected cell expands to 165% of its usual dimensions, constrained to the grid so edge cells remain visible. Swipe direction is measured from the initial finger position; a 14dp minimum movement (or the device's touch slop, whichever is greater) distinguishes a swipe from a tap. Character selection commits on finger release. A second tap chooses a compass sector in the expanded cell; tapping its blank center does not insert text. To type **a**, touch the top-left cell and swipe left, or tap that cell and tap **a** in the enlarged popup. After insertion, the popup closes and the selected group returns to center. The center cell's top character is delete, bottom is space, left is M/m and right N/n.

Connecting a gamepad or joystick restores the right-side face buttons; disconnecting restores the one-handed touch layout. A configured external keypad also uses the controller layout. Virtual/injected input devices do not change the touch layout. Changing device/layout or canceling a touch discards any incomplete gesture. Numbers, shift, delete, space, enter and the monochrome globe stay in the footer, above Android's navigation controls.

On the original numeric page, the number is usually on the **RIGHT** (circle); **0** is the bottom character of the bottom-right group. Shift while on the numeric page reveals symbols. Enter, space, delete have always-accessible dedicated footer buttons.

**Test fairness:** WPM shown in the app is `current committed characters / 5 / elapsed minutes`, not true gross keystroke WPM. Text accuracy is edit-distance similarity to an equal-length target prefix, not keystroke error rate. It does not record the complete event stream or keystrokes, and the timer cannot continue updating when the text is unchanged. For comparisons, complete the same sentence for both keyboard modes, and use a stopwatch or screen recording if necessary.

## Safety and current limitations

This is a research proof of concept, **not a polished daily-driver**. Touch target sizes, TalkBack accessibility, emoji, non-English languages, cursor arrows, long-press key repeat, autocorrection, accessibility scaling, autocapitalization, and word suggestions require further work. Gamepad support is implemented but **not yet validated on hardware**. Some fields/apps may not accept actions identically, and keyboard-switching behavior can vary by OEM.

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
