# Danzeff Android: roadmap to a daily keyboard

The next goal should be dependable everyday editing before prediction or large feature menus. Proposed features below are not implemented unless explicitly marked as current.

Current foundation: Android IME, touch swipes and two-tap character entry, controller/keypad profiles, Light/Dark/System appearance, Google Sans, original symbols and Flipper EX+ punctuation. No network permission or typed-text logging.

| Priority | Proposed feature | User benefit | Acceptance criteria |
|---|---|---|---|
| P0 | Field-aware layouts and actions | Number, phone, email, URL and password fields open with useful keys; Enter says Search/Send/Next/Done when appropriate. | Respect EditorInfo inputType, input flags and imeOptions across native editors, browsers and WebViews; keep a clear manual override. Password fields never train dictionaries or save clipboard history. |
| P0 | Cursor and selection tools | Fix mistakes without repeatedly tapping the text field. | Spacebar drag moves cursor; optional arrows, select-all, copy/cut/paste and undo where editors support them; never silently delete a selection on navigation. |
| P0 | Delete repeat and Unicode-safe editing | Quickly erase mistakes, including emoji sequences. | Hold delete repeats after a delay, optional delete-word gesture; combining marks and joined emoji are handled as visible characters when editor context permits. Releasing or canceling stops repeat. |
| P0 | Adjustable ergonomics | Fit different hands, phones, foldables and accessibility needs. | Keyboard height, left/right docking, landscape layout, text scaling and adjustable swipe distance; footer never overlaps navigation in gesture or three-button mode. |
| P0 | Accessible key navigation | Make the custom canvas usable with TalkBack and external switches. | Expose cells/characters as semantic virtual nodes, meaningful action labels, focus order and touch exploration; don't announce entered passwords. |
| P0 | Compatibility and reliability matrix | Make it safe to leave Danzeff selected all day. | Verify rotation, dark-mode changes, split screen, app switches, controller reconnects and process restarts; exercise messaging, browser forms and document editors on physical devices; no duplicate commits or lost input. |
| P0 | Stable release signing and reproducible updates | Install updates without uninstalling or losing settings. | Separate test/debug builds from release builds; user-controlled protected release key; consistent signing certificate and automated build/test pipeline. |
| P1 | Smart capitalization and punctuation | Reduce mode switching while composing ordinary sentences. | Respect requested caps flags; one-shot Shift and explicit Caps Lock; optional double-space period; disable transformations for passwords and literal/code entry. |
| P1 | Local suggestions and reversible autocorrect | Improve speed without sending typed text away. | Offline dictionary, optional suggestions, personal dictionary controls, tap-to-accept; immediately undo correction with Backspace. Honor no-suggestions/private-learning flags. |
| P1 | Accents, Unicode symbols and languages | Write names and messages beyond ASCII. | Long-press accent choices, € £ ¥ © ° ± × ÷ ≤ ≥ arrows and typographic quotes; language-specific layouts and Android input-method subtypes. Test font fallback and combining characters. |
| P1 | Emoji picker and search | Handle common messages without switching keyboards. | Searchable categories, skin tones and recent selections stored locally; insert complete sequences; clear-recents option. Rich stickers/GIFs can be considered later through supported editor APIs. |
| P1 | Optional haptics and visible feedback | Feel whether a cell, swipe or character was accepted. | Separate selection/commit feedback, system vibration preferences respected, intensity controls; no character committed just because a hover highlight changed. |
| P1 | Discoverable onboarding and customization | Reduce the learning curve and personalize common inputs. | Short guided typing exercise, diagram of four-direction entry, symbol shortcuts, optional hints; named layout presets and import/export of device profiles. |
| P2 | Privacy-first clipboard and text snippets | Paste frequent addresses or phrases quickly. | Explicit opt-in history, expiration and clear-all controls, sensitive-clip exclusion; snippet storage and backup are user-controlled. |
| P2 | Voice entry integration | Offer a hands-free alternative when typing is inconvenient. | Clear microphone activation and permission flow; identify whether recognition is local or uses a service; permission-denied fallback and cancellation. |
| P2 | Optional QWERTY fallback | Let a new user keep Danzeff as default while learning. | One clear toggle inside the same IME; use only properly licensed implementation/assets and preserve privacy/settings across layouts. |

Suggested sequence:
1. Daily-use foundation: field handling, editing, delete repeat, accessibility, ergonomics and physical-device compatibility.
2. Faster writing: smart caps, offline suggestions, accents/languages and emoji.
3. Convenience: snippets/clipboard, voice and optional fallback layout.

Readiness should be judged by real tasks: composing and correcting a message, entering an email/URL/phone number, searching, filling a form, selecting and replacing text, switching languages and recovering from controller disconnects. Measure task completion and error correction as well as typing speed. Keep all typing studies explicitly opt-in and avoid collecting private text.

Android implementation references:
- EditorInfo: https://developer.android.com/reference/android/view/inputmethod/EditorInfo
- InputType: https://developer.android.com/reference/android/text/InputType
- InputMethodSubtype: https://developer.android.com/reference/android/view/inputmethod/InputMethodSubtype

This is a proposed product plan, not a claim that the prototype already satisfies these criteria.