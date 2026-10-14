package dev.danzeff.android;

import android.icu.text.BreakIterator;
import java.util.Locale;

/** ICU grapheme boundaries plus emoji joins for older Android ICU releases. */
final class Graphemes {
    private Graphemes() {}
    static int previous(String text, int end) {
        end = Math.max(0, Math.min(end, text.length()));
        if (end == 0) return 0;
        String prefix = text.substring(0, end);
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(prefix);
        int boundary = iterator.preceding(end);
        int start = baseStart(prefix, end);
        int last = prefix.codePointBefore(end);
        if (regional(last)) {
            int count = 0, p = end;
            while (p > 0 && regional(prefix.codePointBefore(p))) {
                p -= Character.charCount(prefix.codePointBefore(p)); count++;
            }
            start = end - (count % 2 == 0 ? 4 : 2);
        }
        while (start > 0 && prefix.codePointBefore(start) == 0x200D) {
            start -= 1;
            if (start > 0) start = baseStart(prefix, start);
        }
        if (last == '\n' && start > 0 && prefix.charAt(start - 1) == '\r') start--;
        return Math.min(boundary < 0 ? start : boundary, start);
    }
    private static int baseStart(String text, int end) {
        int p = end;
        while (p > 0) {
            int cp = text.codePointBefore(p); p -= Character.charCount(cp);
            int type = Character.getType(cp);
            boolean extension = type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK || cp >= 0x1F3FB && cp <= 0x1F3FF
                    || cp >= 0xE0020 && cp <= 0xE007F;
            if (!extension) break;
        }
        return p;
    }
    private static boolean regional(int cp) { return cp >= 0x1F1E6 && cp <= 0x1F1FF; }
    static int next(String text, int start) {
        if (start >= text.length()) return text.length();
        BreakIterator iterator = BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text);
        int p = iterator.following(start);
        if (p < 0) return text.length();
        // Older ICU may split a joined emoji; don't stop inside its full cluster.
        while (p < text.length()) {
            if (text.codePointAt(p) == 0x200D) {
                p++;
                if (p < text.length()) p += Character.charCount(text.codePointAt(p));
                continue;
            }
            int end = p + Character.charCount(text.codePointAt(p));
            if (previous(text, end) >= p) break;
            p = end;
        }
        return p;
    }
}
