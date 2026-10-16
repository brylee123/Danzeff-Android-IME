package dev.danzeff.android;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

/** Editor-requested behavior, independent of rendering. No private text is retained. */
final class FieldPolicy {
    enum Kind {
        TEXT,
        EMAIL,
        URL,
        NUMBER,
        PHONE,
        PASSWORD,
        LITERAL
    }

    final Kind kind;
    final int inputType;
    final int options;
    final int action;
    final int actionId;

    final String enterLabel;
    final boolean suggestions;
    final boolean prose;
    final boolean numeric;
    final boolean signed;
    final boolean decimal;

    FieldPolicy(final EditorInfo info) {
        inputType = info == null ? InputType.TYPE_CLASS_TEXT : info.inputType;
        options = info == null ? 0 : info.imeOptions;
        actionId =
                info == null
                                || info.actionLabel == null
                                || (options & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                        ? 0
                        : info.actionId;
        final int cls = inputType & InputType.TYPE_MASK_CLASS;
        final int variation = inputType & InputType.TYPE_MASK_VARIATION;

        final boolean password =
                cls == InputType.TYPE_CLASS_TEXT
                                && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                                        || variation
                                                == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                                        || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
                        || cls == InputType.TYPE_CLASS_NUMBER
                                && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
        if (password) {
            kind = Kind.PASSWORD;
        } else if (cls == InputType.TYPE_CLASS_NUMBER) {
            kind = Kind.NUMBER;
        } else if (cls == InputType.TYPE_CLASS_PHONE) {
            kind = Kind.PHONE;
        } else if (cls == InputType.TYPE_CLASS_TEXT
                && (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)) {
            kind = Kind.EMAIL;
        } else if (cls == InputType.TYPE_CLASS_TEXT
                && variation == InputType.TYPE_TEXT_VARIATION_URI) {
            kind = Kind.URL;
        } else if (cls != InputType.TYPE_CLASS_TEXT
                || (inputType & InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
                || variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
            kind = Kind.LITERAL;
        } else {
            kind = Kind.TEXT;
        }
        numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE;
        signed =
                cls == InputType.TYPE_CLASS_NUMBER
                        && (inputType & InputType.TYPE_NUMBER_FLAG_SIGNED) != 0;
        decimal =
                cls == InputType.TYPE_CLASS_NUMBER
                        && (inputType & InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0;
        prose = kind == Kind.TEXT;
        suggestions =
                prose
                        && (inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) == 0
                        && (options & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0;
        final int requested = options & EditorInfo.IME_MASK_ACTION;
        action =
                (options & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
                        ? EditorInfo.IME_ACTION_NONE
                        : requested;
        if (info != null
                && info.actionLabel != null
                && (actionId != 0 || action != EditorInfo.IME_ACTION_NONE)) {
            enterLabel = info.actionLabel.toString();
        } else {
            switch (action) {
                case EditorInfo.IME_ACTION_SEARCH:
                    enterLabel = "Search";
                    break;
                case EditorInfo.IME_ACTION_SEND:
                    enterLabel = "Send";
                    break;
                case EditorInfo.IME_ACTION_NEXT:
                    enterLabel = "Next";
                    break;
                case EditorInfo.IME_ACTION_PREVIOUS:
                    enterLabel = "Prev";
                    break;
                case EditorInfo.IME_ACTION_GO:
                    enterLabel = "Go";
                    break;
                case EditorInfo.IME_ACTION_DONE:
                    enterLabel = "Done";
                    break;
                default:
                    enterLabel =
                            numeric && (options & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0
                                    ? "Done"
                                    : "↵";
            }
        }
    }

    String character(final int layer, final int row, final int col, final int slot) {
        if (numeric && layer == KeyboardLayout.DIGITS) {
            if (row == 1 && col == 1 && slot == KeyboardLayout.LEFT) {
                return "0";
            }
            if (row == 0 && col == 0 && slot == KeyboardLayout.TOP && signed) {
                return "-";
            }
            if (row == 0 && col == 1 && slot == KeyboardLayout.TOP && decimal) {
                return ".";
            }
            if (kind == Kind.PHONE && slot == KeyboardLayout.TOP && row == 0) {
                return new String[] {"+", "*", "#"}[col];
            }
        }
        return KeyboardLayout.at(layer, row, col, slot);
    }

    String[] shortcuts() {
        switch (kind) {
            case EMAIL:
                return new String[] {"@", ".", "_", "-"};
            case URL:
                return new String[] {"/", ":", ".", "-"};
            case PHONE:
                return new String[] {"+", "*", "#", "0"};
            case NUMBER:
                return signed && decimal
                        ? new String[] {"0", "-", "."}
                        : signed
                                ? new String[] {"0", "-"}
                                : decimal ? new String[] {"0", "."} : new String[] {"0"};
            case PASSWORD:
                return numeric ? new String[] {"0"} : new String[] {"0", "@", "#", "!"};
            default:
                return new String[0];
        }
    }
}
