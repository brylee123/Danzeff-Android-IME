# Danzeff Android IME — proof of concept v0.1

An **actual Android system keyboard** (InputMethodService), not a mockup or a text-editor-only control. Original Danzeff-style 3x3 groups and four face-button positions, written from scratch in Java with native Android APIs. No UI library or third-party runtime dependencies.

## What works

- **System-wide** IME for Android 8.0+ (API 26+, 64-bit/32-bit Android compatible).
- **Classic 2-step touch**: tap/hold a group in the 3x3 grid, then tap a face button for top (triangle), left (square), bottom (cross), right (circle). Multitouch works; the grid selection persists between taps.
- **Direct-touch mode**: tap one quadrant of any 3x3 cell to output its character; toggle with `TWO-STEP ⇄` in the header. The mode choice persists locally.
- PSP-style **lowercase, uppercase, digits, and symbols** pages (ASCII; layout from DanzeffOSLib's `modeChar`). One-shot shift for letters.
- Backspace, space, context-aware enter/IME action, keyboard picker, and switch-to-next-keyboard.
- Basic **Bluetooth/USB gamepad** support: D-pad and left analog stick choose a group; Y/X/A/B enter top/left/bottom/right; L1 changes letters/digits, R1 shifts, Start=Enter, Select=Backspace. Actual Android controller mapping varies; needs device testing.
- An **in-app typing test**: prompted sentence, elapsed time, approximate completed-text WPM, text-vs-prompt-prefix accuracy, deletion count, manual reset. No test logs are saved.
- No network permission, analytics, logging of typed content, clipboard reads, dictionaries, or cloud features.

## Build and install

1. Install **Android Studio** with the Android SDK **35** and JDK **17+**. Open the project folder. Android Studio may offer to provision Gradle if it is not installed.
2. The project specifies **Android Gradle Plugin 8.9.2**. It is a standard Gradle/Android project. Use **Gradle 8.11.1** for command-line builds (`gradle :app:assembleDebug`). A wrapper JAR is not bundled because the build environment here has no Gradle/Android SDK or ability to download those binaries.
3. Allow Android Studio's Gradle sync; press **Run** with your Android phone connected by USB debugging, or choose **Build > Build APK(s)**. A successful debug build produces `app/build/outputs/apk/debug/app-debug.apk`.
4. Launch **Danzeff Keyboard** on the phone. Tap **Enable keyboard in Android Settings** and enable **Danzeff Keyboard**. Android shows a standard warning for all third-party keyboards. Tap **Choose Danzeff as the current keyboard** and select it.
5. Try the built-in typing experiment or open any ordinary text field. Use the keyboard's **🌐** button to switch back to your usual keyboard.

**Optional CLI:** If you have Gradle 8.11.1 installed, run `gradle :app:assembleDebug`. The source archive intentionally does not contain a built APK; no working Android build toolchain was available to compile and sign it here.

## Input specifics

Danzeff slots are **TOP / LEFT / BOTTOM / RIGHT**, in source order `TRIANGLE / SQUARE / CROSS / CIRCLE`. In 2-step mode, the active green-outlined group sets which characters the four right-side buttons insert. In direct mode, the closest compass quadrant within a cell chooses one character; one touch suffices. The center cell's top character is delete, bottom is space, left is M/m and right N/n.

On the original numeric page, the number is usually on the **RIGHT** (circle); **0** is the bottom character of the bottom-right group. Shift while on the numeric page reveals symbols. Enter, space, delete have always-accessible dedicated footer buttons.

**Test fairness:** WPM shown in the app is `current committed characters / 5 / elapsed minutes`, not true gross keystroke WPM. Text accuracy is edit-distance similarity to an equal-length target prefix, not keystroke error rate. It does not record the complete event stream or keystrokes, and the timer cannot continue updating when the text is unchanged. For comparisons, complete the same sentence for both keyboard modes, and use a stopwatch or screen recording if necessary.

## Safety and current limitations

This is a research proof of concept, **not a polished daily-driver**. Touch target sizes, TalkBack accessibility, emoji, non-English languages, cursor arrows, long-press key repeat, autocorrection, accessibility scaling, autocapitalization, and word suggestions require further work. Gamepad support is implemented but **not yet validated on hardware**. Some fields/apps may not accept actions identically, and keyboard-switching behavior can vary by OEM.

All typed text is sent only to the currently focused Android text field using `InputConnection`; there is **no INTERNET permission**, no background service other than the Android IME service, and no text persistence. Android warns when enabling any third-party keyboard because such an IME has access to user input; do not enter credentials with an unreviewed build.

## Checks

Run the dependency-free character-map test:

```bash
mkdir -p /tmp/danzeff-check
javac -d /tmp/danzeff-check app/src/main/java/dev/danzeff/android/KeyboardLayout.java tools/KeyboardLayoutCheck.java
java -cp /tmp/danzeff-check dev.danzeff.android.KeyboardLayoutCheck
```

## Attribution and licensing note

Inspired by the PSP **Danzeff** keyboard and the publicly accessible [PSP-Archive/DanzeffOSLib](https://github.com/PSP-Archive/DanzeffOSLib) source (notably its `modeChar` lookup array). No original PSP source code, image assets, sound assets, or app binaries are copied into this project. The Android input service, rendering and gesture handling are independently implemented. Check rights for the historical character-layout data and any future reuse of original assets before publishing a release.

Android platform IME reference: https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method

## Roadmap

- v0.2: device testing on foldables, landscape, and mainstream controllers; touch feedback, rotation/resizing, custom mappings, larger touch targets
- v0.3: instrumented **opt-in** usability sessions for WPM, corrected/uncorrected errors and per-mode learning curves, without recording sensitive user text
- Later: accessibility support, smart punctuation, physical controller deadzone tuning, optional predictions, more layouts
