package dev.danzeff.android;

import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 28, 35})
public class EditingEngineTest {
    static final class Editor extends BaseInputConnection {
        final SpannableStringBuilder text = new SpannableStringBuilder();
        int action = -1, menu = -1, key = -1, meta, batches;
        boolean context = true, extraction = true, acceptsAction = true, acceptsDelete = true;
        Editor() { super(new View(RuntimeEnvironment.getApplication()), true); Selection.setSelection(text, 0); }
        @Override public Editable getEditable() { return text; }
        void set(String value) { text.replace(0, text.length(), value); Selection.setSelection(text, text.length()); }
        @Override public CharSequence getTextBeforeCursor(int n, int flags) {
            return context ? super.getTextBeforeCursor(n, flags) : null;
        }
        @Override public boolean deleteSurroundingText(int before, int after) {
            return acceptsDelete && super.deleteSurroundingText(before, after);
        }
        @Override public ExtractedText getExtractedText(ExtractedTextRequest request, int flags) {
            if (!extraction) return null;
            ExtractedText result = new ExtractedText();
            result.text = text.toString(); result.startOffset = 0;
            result.partialStartOffset = result.partialEndOffset = -1;
            result.selectionStart = Selection.getSelectionStart(text); result.selectionEnd = Selection.getSelectionEnd(text);
            return result;
        }
        @Override public boolean beginBatchEdit() { batches++; return true; }
        @Override public boolean endBatchEdit() { batches--; return true; }
        @Override public boolean performEditorAction(int id) { action = id; return acceptsAction; }
        @Override public boolean performContextMenuAction(int id) {
            menu = id;
            if (id == android.R.id.selectAll) Selection.setSelection(text, 0, text.length());
            return true;
        }
        @Override public boolean sendKeyEvent(KeyEvent event) {
            key = event.getKeyCode(); meta = event.getMetaState(); return true;
        }
    }
    private Editor editor;
    private EditingEngine engine;
    private EditorInfo info;
    @Before public void setup() {
        editor = new Editor(); engine = new EditingEngine(() -> editor);
        info = new EditorInfo(); info.inputType = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        engine.start(info);
    }
    @Test public void fieldPoliciesCoverNumbersPhoneEmailUrlAndEveryPasswordVariant() {
        info.inputType = InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        engine.start(info);
        assertTrue(engine.policy.numeric); assertFalse(engine.policy.suggestions);
        assertEquals("-", engine.policy.character(KeyboardLayout.DIGITS, 0, 0, 0));
        assertEquals(".", engine.policy.character(KeyboardLayout.DIGITS, 0, 1, 0));
        assertEquals("0", engine.policy.character(KeyboardLayout.DIGITS, 1, 1, 1));
        info.inputType = InputType.TYPE_CLASS_PHONE; engine.start(info);
        assertEquals("+", engine.policy.character(KeyboardLayout.DIGITS, 0, 0, 0));
        for (int variation : new int[]{InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI}) {
            info.inputType = InputType.TYPE_CLASS_TEXT | variation; engine.start(info);
            editor.set("teh"); engine.autocorrectEnabled = true;
            assertTrue(engine.suggestions().isEmpty()); assertEquals(0, engine.capsMode());
            engine.space(100); engine.space(200); assertEquals("teh  ", editor.text.toString());
        }
        for (int type : new int[]{InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD}) {
            info.inputType = type; engine.start(info); editor.set("teh");
            assertEquals(FieldPolicy.Kind.PASSWORD, engine.policy.kind);
            assertTrue(engine.suggestions().isEmpty()); assertEquals(0, engine.capsMode());
            engine.space(100); engine.space(200); assertEquals("teh  ", editor.text.toString());
        }
    }
    @Test public void actionsRespectSearchSendNextDoneAndNoEnterAction() {
        for (int action : new int[]{EditorInfo.IME_ACTION_SEARCH, EditorInfo.IME_ACTION_SEND,
                EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_DONE, EditorInfo.IME_ACTION_GO}) {
            info.imeOptions = action; engine.start(info); assertTrue(engine.enter()); assertEquals(action, editor.action);
        }
        info.actionLabel = "Submit"; info.actionId = 42; info.imeOptions = EditorInfo.IME_ACTION_UNSPECIFIED; engine.start(info);
        assertEquals("Submit", engine.policy.enterLabel); engine.enter(); assertEquals(42, editor.action);
        info.actionLabel = null; info.actionId = 0;
        info.imeOptions = EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        engine.start(info); editor.action = -1; editor.set("Hello"); engine.enter();
        assertEquals(-1, editor.action); assertEquals("Hello\n", editor.text.toString());
    }
    @Test public void deletionRemovesWholeEmojiAccentsFlagsAndCrLf() {
        for (String grapheme : new String[]{"😀", "e\u0301", "👨‍👩‍👧‍👦", "👍🏽", "🇺🇸", "1️⃣", "\r\n", "👩🏽‍💻"}) {
            editor.set("A" + grapheme); engine.reset(); assertTrue(engine.delete());
            assertEquals("Broken grapheme: " + grapheme, "A", editor.text.toString());
        }
        editor.set("A🇺🇸🇨🇦"); engine.delete(); assertEquals("A🇺🇸", editor.text.toString());
        Selection.setSelection(editor.text, 0, editor.text.length()); engine.delete(); assertEquals("", editor.text.toString());
    }
    @Test public void cursorAndSelectionNeverSplitGraphemes() {
        editor.set("A👨‍👩‍👧‍👦B"); engine.move(-1); assertEquals(editor.text.length() - 1, Selection.getSelectionEnd(editor.text));
        engine.move(-1); assertEquals(1, Selection.getSelectionEnd(editor.text));
        engine.move(1); assertEquals(editor.text.length() - 1, Selection.getSelectionEnd(editor.text));
        assertTrue(engine.toggleSelection()); engine.move(-1);
        assertEquals("👨‍👩‍👧‍👦", editor.getSelectedText(0).toString());
        engine.delete(); assertEquals("AB", editor.text.toString());
        engine.contextAction(android.R.id.selectAll); assertEquals(android.R.id.selectAll, editor.menu);
        engine.contextAction(android.R.id.copy); assertEquals(android.R.id.copy, editor.menu);
        engine.contextAction(android.R.id.cut); assertEquals(android.R.id.cut, editor.menu);
        engine.contextAction(android.R.id.paste); assertEquals(android.R.id.paste, editor.menu);
    }
    @Test public void autocorrectionIsOptionalConservativeAndBackspaceRestoresOriginal() {
        editor.set("teh"); engine.space(100); assertEquals("teh ", editor.text.toString());
        editor.set("teh"); engine.autocorrectEnabled = true; engine.reset(); engine.space(100);
        assertEquals("the ", editor.text.toString()); engine.delete(); assertEquals("teh", editor.text.toString());
        engine.delete(); assertEquals("te", editor.text.toString()); assertEquals(0, editor.batches);
        editor.set("Zyxw"); engine.reset(); engine.space(100); assertEquals("Zyxw ", editor.text.toString());
    }
    @Test public void correctionDoesNotUndoAfterOtherInputOrExternalCursorMovement() {
        engine.autocorrectEnabled = true; editor.set("teh"); engine.space(100);
        engine.type("x"); engine.delete(); assertEquals("the ", editor.text.toString());
        editor.set("teh"); engine.reset(); engine.space(100);
        engine.selectionChanged(4, 4); // An unchanged callback preserves a valid correction.
        engine.delete(); assertEquals("teh", editor.text.toString());
        editor.set("teh"); engine.reset(); engine.space(100);
        engine.selectionChanged(0, 0); engine.delete(); assertEquals("the", editor.text.toString());
        engine.start(info); assertFalse(engine.selecting());
    }
    @Test public void suggestionsAreLocalAndRejectStaleCandidateTaps() {
        editor.set("hel"); assertEquals("hello", engine.suggestions().get(1));
        assertTrue(engine.choose("hel", "hello")); assertEquals("hello ", editor.text.toString());
        editor.set("other"); assertFalse(engine.choose("hel", "hello")); assertEquals("other", editor.text.toString());
        info.imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING; engine.start(info); assertTrue(engine.suggestions().isEmpty());
        info.imeOptions = 0; info.inputType |= InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        engine.start(info); editor.set("teh"); engine.autocorrectEnabled = true; engine.space(100);
        assertTrue(engine.suggestions().isEmpty()); assertEquals("teh ", editor.text.toString());
    }
    @Test public void capsAndPunctuationFollowContextAndCanBeDisabled() {
        editor.set(""); assertTrue(engine.capsMode() != 0);
        editor.set("hello"); assertEquals(0, engine.capsMode());
        engine.space(100); engine.space(200); assertEquals("hello. ", editor.text.toString());
        assertTrue(engine.capsMode() != 0); engine.delete(); assertEquals("hello  ", editor.text.toString());
        engine.punctuationEnabled = false; editor.set("hello"); engine.reset();
        engine.space(100); engine.space(200); assertEquals("hello  ", editor.text.toString());
        engine.capitalizationEnabled = false; editor.set(""); assertEquals(0, engine.capsMode());
    }
    @Test public void limitedEditorsFallBackWithoutLosingSpacesOrSelectionIntent() {
        editor.extraction = false; engine.toggleSelection(); engine.move(-1);
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, editor.key); assertTrue((editor.meta & KeyEvent.META_SHIFT_ON) != 0);
        editor.context = false; engine.delete(); assertEquals(KeyEvent.KEYCODE_DEL, editor.key);
        editor.context = editor.extraction = true; editor.acceptsDelete = false;
        editor.set("teh"); engine.reset(); engine.autocorrectEnabled = true; engine.space(100);
        assertEquals("teh ", editor.text.toString()); assertEquals(0, editor.batches);
        editor.acceptsDelete = true; editor.set("x" + "a".repeat(2048)); engine.delete(); assertEquals(2048, editor.text.length());
    }
    @Test public void doubleSpaceDoesNotTransformTextAfterExternalCursorMovement() {
        editor.set("hello"); engine.space(100);
        engine.selectionChanged(0, 0);
        engine.space(200);
        assertEquals("hello  ", editor.text.toString());
    }
    @Test public void refusedPunctuationTransformationStillInsertsLiteralSpace() {
        editor.set("hello"); engine.space(100); editor.acceptsDelete = false;
        engine.space(200); assertEquals("hello  ", editor.text.toString());
        assertEquals(0, editor.batches);
    }
    @Test public void numericActionsHonorNoEnterActionAndNeverSubstituteDeclinedSearchWithDone() {
        info.inputType = InputType.TYPE_CLASS_NUMBER; engine.start(info);
        assertTrue(engine.enter()); assertEquals(EditorInfo.IME_ACTION_DONE, editor.action);
        info.imeOptions = EditorInfo.IME_ACTION_SEND | EditorInfo.IME_FLAG_NO_ENTER_ACTION;
        engine.start(info); editor.action = -1; editor.set("123");
        assertEquals("↵", engine.policy.enterLabel); engine.enter();
        assertEquals(-1, editor.action); assertEquals("123\n", editor.text.toString());
        info.imeOptions = EditorInfo.IME_ACTION_SEARCH; engine.start(info); editor.acceptsAction = false;
        assertFalse(engine.enter()); assertEquals(EditorInfo.IME_ACTION_SEARCH, editor.action);
    }
    @Test public void detachedEditorAndFieldChangesClearTransientState() {
        EditingEngine detached = new EditingEngine(() -> null);
        assertFalse(detached.type("x")); assertFalse(detached.delete()); assertFalse(detached.space(1));
        assertFalse(detached.enter()); assertFalse(detached.move(1)); assertTrue(detached.suggestions().isEmpty());
        engine.autocorrectEnabled = true; editor.set("teh"); engine.space(100);
        engine.start(info); engine.delete(); assertEquals("the", editor.text.toString());
    }
}
