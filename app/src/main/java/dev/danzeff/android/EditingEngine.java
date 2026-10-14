package dev.danzeff.android;

import android.text.InputType;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Bounded editor operations. Transient correction data is cleared on field/selection changes. */
final class EditingEngine {
    private final Supplier<InputConnection> connection;
    FieldPolicy policy = new FieldPolicy(null);
    boolean suggestionsEnabled = true, autocorrectEnabled, capitalizationEnabled = true, punctuationEnabled = true;
    private String undoOriginal, undoReplacement;
    private int undoEnd = -1;
    private long lastSpace = -1;
    private int spaceEnd = -1;
    private boolean selecting;
    private int selectionAnchor = -1, selectionEdge = -1, expectedStart = -1, expectedEnd = -1;
    EditingEngine(Supplier<InputConnection> connection) { this.connection = connection; }
    void start(EditorInfo info) { policy = new FieldPolicy(info); reset(); }
    void reset() { clearUndo(); lastSpace = -1; spaceEnd = -1; selecting = false; selectionAnchor = selectionEdge = expectedStart = expectedEnd = -1; }
    private void clearUndo() { undoOriginal = undoReplacement = null; undoEnd = -1; }
    void selectionChanged(int start, int end) {
        if (start != end || end != undoEnd) clearUndo();
        if (start != end || end != spaceEnd) lastSpace = -1;
        if (!selecting || start != expectedStart || end != expectedEnd) selectionAnchor = selectionEdge = -1;
    }
    private String before(InputConnection ic, int length) {
        CharSequence text = ic.getTextBeforeCursor(length, 0);
        return text == null ? "" : text.toString();
    }
    private boolean selected(InputConnection ic) {
        CharSequence text = ic.getSelectedText(0); return text != null && text.length() > 0;
    }
    private ExtractedText extracted(InputConnection ic) {
        ExtractedTextRequest request = new ExtractedTextRequest(); request.hintMaxChars = 4096; request.hintMaxLines = 32;
        return ic.getExtractedText(request, 0);
    }
    private int position(InputConnection ic) {
        ExtractedText text = extracted(ic);
        return text != null && text.selectionStart == text.selectionEnd ? text.startOffset + text.selectionEnd : -1;
    }
    int capsMode() {
        if (!capitalizationEnabled || !policy.prose) return 0;
        InputConnection ic = connection.get(); if (ic == null) return 0;
        int requested = policy.inputType & (InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        if (requested == 0) requested = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        return ic.getCursorCapsMode(requested);
    }
    boolean type(String text) {
        InputConnection ic = connection.get(); if (ic == null || text.isEmpty()) return false;
        clearUndo(); lastSpace = -1;
        return ic.commitText(text, 1);
    }
    List<String> suggestions() {
        InputConnection ic = connection.get();
        if (ic == null || !suggestionsEnabled || !policy.suggestions || selected(ic)) return Collections.emptyList();
        return LocalSuggestions.candidates(LocalSuggestions.wordBefore(before(ic, 64)));
    }
    boolean choose(String expectedWord, String candidate) {
        InputConnection ic = connection.get();
        if (ic == null || !suggestionsEnabled || !policy.suggestions || selected(ic)) return false;
        String word = LocalSuggestions.wordBefore(before(ic, 64));
        if (!word.equals(expectedWord) || word.isEmpty()) return false;
        clearUndo();
        return replaceWord(ic, word, candidate, " ");
    }
    String currentWord() {
        InputConnection ic = connection.get();
        return ic != null && suggestionsEnabled && policy.suggestions ? LocalSuggestions.wordBefore(before(ic, 64)) : "";
    }
    boolean space(long time) {
        InputConnection ic = connection.get(); if (ic == null) return false;
        clearUndo();
        if (selected(ic)) { lastSpace = -1; return ic.commitText(" ", 1); }
        String text = before(ic, 128);
        if (policy.prose && punctuationEnabled && lastSpace >= 0 && time - lastSpace < 700
                && spaceEnd >= 0 && position(ic) == spaceEnd && text.endsWith(" ") && text.length() > 1
                && Character.isLetterOrDigit(text.codePointBefore(text.length() - 1))) {
            ic.beginBatchEdit();
            try {
                if (!ic.deleteSurroundingText(1, 0)) { lastSpace = -1; return ic.commitText(" ", 1); }
                boolean ok = ic.commitText(". ", 1);
                if (ok) rememberUndo(ic, "  ", ". ");
                else ic.commitText("  ", 1);
                lastSpace = -1; return ok;
            } finally { ic.endBatchEdit(); }
        }
        String word = LocalSuggestions.wordBefore(text);
        if (suggestionsEnabled && autocorrectEnabled && policy.suggestions && !word.isEmpty() && position(ic) >= 0) {
            String corrected = LocalSuggestions.correction(word);
            if (!corrected.equals(word)) {
                boolean ok = replaceWord(ic, word, corrected, " "); lastSpace = time; boolean inserted = ok || ic.commitText(" ", 1); spaceEnd = position(ic); return inserted;
            }
        }
        lastSpace = time; boolean inserted = ic.commitText(" ", 1); spaceEnd = position(ic); return inserted;
    }
    private boolean replaceWord(InputConnection ic, String word, String replacement, String suffix) {
        ic.beginBatchEdit();
        try {
            if (!ic.deleteSurroundingText(word.length(), 0)) return false;
            boolean ok = ic.commitText(replacement + suffix, 1);
            if (!ok) { ic.commitText(word, 1); return false; }
            rememberUndo(ic, word, replacement + suffix);
            return true;
        } finally { ic.endBatchEdit(); }
    }
    private void rememberUndo(InputConnection ic, String original, String replacement) {
        int end = position(ic);
        if (end >= 0 && before(ic, replacement.length()).equals(replacement)) {
            undoOriginal = original; undoReplacement = replacement; undoEnd = end;
        }
    }
    boolean delete() {
        InputConnection ic = connection.get(); if (ic == null) return false;
        lastSpace = -1;
        if (selected(ic)) { clearUndo(); return ic.commitText("", 1); }
        if (undoReplacement != null && position(ic) == undoEnd
                && before(ic, undoReplacement.length()).equals(undoReplacement)) {
            String old = undoOriginal, replacement = undoReplacement; clearUndo();
            ic.beginBatchEdit();
            try { return ic.deleteSurroundingText(replacement.length(), 0) && ic.commitText(old, 1); }
            finally { ic.endBatchEdit(); }
        }
        clearUndo();
        CharSequence available = ic.getTextBeforeCursor(2048, 0);
        if (available == null || available.length() == 0) return key(ic, KeyEvent.KEYCODE_DEL, false);
        String text = available.toString(); int start = Graphemes.previous(text, text.length());
        // Never delete a possibly truncated cluster at the bounded context edge.
        if (start == 0 && (text.length() == 2048 || Character.isLowSurrogate(text.charAt(0)) || text.codePointAt(0) == 0x200D)) return key(ic, KeyEvent.KEYCODE_DEL, false);
        return ic.deleteSurroundingText(text.length() - start, 0) || key(ic, KeyEvent.KEYCODE_DEL, false);
    }
    boolean enter() {
        InputConnection ic = connection.get(); if (ic == null) return false;
        reset();
        if (policy.actionId != 0 || policy.action != EditorInfo.IME_ACTION_NONE && policy.action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            int id = policy.actionId != 0 ? policy.actionId : policy.action;
            if (ic.performEditorAction(id)) return true;
        }
        if (policy.numeric && (policy.options & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
            return (policy.action == EditorInfo.IME_ACTION_NONE || policy.action == EditorInfo.IME_ACTION_UNSPECIFIED)
                    && ic.performEditorAction(EditorInfo.IME_ACTION_DONE);
        }
        return ic.commitText("\n", 1) || key(ic, KeyEvent.KEYCODE_ENTER, false);
    }
    boolean move(int direction) {
        InputConnection ic = connection.get(); if (ic == null) return false;
        clearUndo(); lastSpace = -1;
        ExtractedText extracted = extracted(ic);
        if (extracted == null || extracted.text == null || extracted.partialStartOffset >= 0)
            return key(ic, direction < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT, selecting);
        String text = extracted.text.toString();
        int start = extracted.selectionStart, end = extracted.selectionEnd;
        if (start < 0 || end < 0 || start > text.length() || end > text.length()) return false;
        if (selecting) {
            if (selectionAnchor < 0) { selectionAnchor = start; selectionEdge = end; }
            selectionEdge = direction < 0 ? Graphemes.previous(text, selectionEdge) : Graphemes.next(text, selectionEdge);
            expectedStart = extracted.startOffset + selectionAnchor; expectedEnd = extracted.startOffset + selectionEdge;
            return ic.setSelection(expectedStart, expectedEnd);
        }
        int target = start != end ? (direction < 0 ? Math.min(start, end) : Math.max(start, end))
                : direction < 0 ? Graphemes.previous(text, end) : Graphemes.next(text, end);
        return ic.setSelection(extracted.startOffset + target, extracted.startOffset + target);
    }
    boolean toggleSelection() { selecting = !selecting; selectionAnchor = selectionEdge = -1; clearUndo(); return selecting; }
    boolean selecting() { return selecting; }
    boolean contextAction(int id) {
        InputConnection ic = connection.get(); reset();
        return ic != null && ic.performContextMenuAction(id);
    }
    private boolean key(InputConnection ic, int code, boolean shift) {
        int meta = shift ? KeyEvent.META_SHIFT_ON : 0;
        long time = android.os.SystemClock.uptimeMillis();
        boolean down = ic.sendKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_DOWN, code, 0, meta));
        boolean up = ic.sendKeyEvent(new KeyEvent(time, time, KeyEvent.ACTION_UP, code, 0, meta));
        return down || up;
    }
}
