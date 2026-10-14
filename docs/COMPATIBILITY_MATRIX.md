# Compatibility and reliability matrix — v0.6

Automated tests use Robolectric and simulated InputConnections, not physical devices. Android API 26, 28 and 35 editor tests cover the minimum Android version and two newer platforms. Native rendering tests use API 28; controller-profile and IME integration tests use API 35.

| Area | Automated coverage | Live coverage / remaining work |
|---|---|---|
| Number/decimal/signed/phone/email/URL/password | Field policy, initial page, shortcut keys, transformation suppression | Verify real apps' EditorInfo declarations and OEM numeric filters |
| Search/Send/Next/Go/Done/custom actions | Action dispatch and label; no-enter-action newline | Browser/WebView, messaging and editor app checks needed |
| Cursor/selection/copy/cut/paste | Grapheme movement, selection extension, delegated action IDs, Space dragging | Verify actual clipboard behavior and partially extracted editors on devices |
| Unicode deletion | Accents, supplementary characters, family/occupation ZWJ emoji, skin tones, flags, keycaps and CRLF on APIs 26/28/35 | Android ICU version and app-provided context determine coverage for newer Unicode and complex scripts |
| Delete hold/repeat | Immediate delete, timed repeats, release/cancel/leave-key, disconnect, hide, resize/recreation cancellation | Physical controller key/axis streams and Bluetooth reconnect testing needed |
| Smart caps/punctuation | Caps context/disable, double-space timing, undo, stale cursor invalidation, literal/password/number/email/URL field exclusions | Check punctuation conventions and languages other than English |
| Local suggestions/autocorrect | Prefix/correction, conservative unique match, opt-in, Backspace reversal, stale candidates, selection and private/no-suggestion exclusions | Starter English lexicon only; vocabulary/real typing study still needed |
| Haptics/visible feedback | Opt-in request and rendered committed-cell feedback; existing palette/popup rendering | Physical vibration intensity/system preference behavior needs device testing |
| Restricted/missing editor APIs | Null context/connection, unavailable extraction, declined delete, action fallback, balanced batch edits | Terminal apps, WebViews, unusual custom editors still need real-app tests |
| IME lifecycle | Field switches, manual mode override, hide/restart/configuration cancellation, transient-state reset | Multi-window, foldables, process death, OEM navigation and repeated app changes pending |
| Controller profiles | Existing device/profile persistence, capture and input regression tests | No Bluetooth/USB hardware claim |

Run the full suite with:

```
gradlew.bat :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

Manual device/app checklist:
1. Exercise each field type in native Android controls, Chrome/WebView, messaging and a document editor. Check action labels and whether Send/Next performs the app's intended action once.
2. Type and correct a sentence. Move through emoji/accent text, extend and replace a selection, and use copy/cut/paste without losing the selection.
3. Hold delete, slide away, lift, cancel and switch apps while holding. No delete may continue after input ends.
4. Rotate, toggle System dark mode, use gesture and three-button navigation, enter split screen, and hide/show the keyboard. Check the toolbar/footer remain reachable.
5. Connect/disconnect/reconnect actual Bluetooth and USB devices. Test key-plus-hat duplicate reports, shift release and repeated delete termination.
6. Verify passwords and literal/no-suggestion fields have no candidates or automatic text changes. Verify private-learning requests disable suggestions and autocorrect. Confirm that no typed text or clipboard history is stored.
7. Revoke/disable system haptics and confirm the keyboard respects it.

Record device, Android build, app version, navigation mode and outcome for each case. Mark untested cases as pending; do not infer success from unit tests.
Live emulator status: API 37 was detected, but streamed and non-streamed installation and a restart command timed out or failed due to the existing debugging/emulator issue. No successful v0.6 installation or live app behavior is claimed. All physical-device and real-app rows remain pending.

Final verification: assembleDebug and lintDebug passed; 63 automated tests passed (0 failures/errors), plus portable character-map and controller-profile checks. This evidence covers simulated editor/API behavior and native view rendering, not physical devices or live app compatibility.
