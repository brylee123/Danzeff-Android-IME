package dev.danzeff.android;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Small original English starter lexicon. No learning, storage, downloads or telemetry. */
final class LocalSuggestions {
    private static final List<String> WORDS = Arrays.asList(
        ("the a an and are as at be been but by can could did do does for from get go good had has have he hello "
        + "her here him his how i if in into is it its just know like look love make me more my new no not now of "
        + "on one only or our out people please really right said say see she so some than thank thanks that their "
        + "them then there these they thing think this time to today too two up us use very want was way we well "
        + "were what when where which who why will with would yes you your about after again all also back because "
        + "before being best better both call come day down each even first friend help home last left let life "
        + "little long many may message most much need never next night other over phone place same send should "
        + "since something still take tell tomorrow under until wait work world write keyboard quick brown fox "
        + "jumps lazy dog android controller number symbol test typing").split(" "));
    static String wordBefore(String before) {
        int start = before.length();
        while (start > 0 && Character.isLetter(before.codePointBefore(start)))
            start -= Character.charCount(before.codePointBefore(start));
        return before.substring(start);
    }
    static List<String> candidates(String typed) {
        List<String> result = new ArrayList<>();
        if (typed.isEmpty() || typed.length() > 32) return result;
        String word = typed.toLowerCase(Locale.ROOT);
        result.add(typed);
        for (String candidate : WORDS) {
            if (candidate.startsWith(word) && !candidate.equals(word)) {
                String cased = casing(typed, candidate);
                if (!result.contains(cased)) result.add(cased);
                if (result.size() == 3) return result;
            }
        }
        if (word.length() >= 3 && result.size() < 3) {
            String best = null; int score = 3;
            for (String candidate : WORDS) {
                if (result.contains(casing(typed, candidate))) continue;
                int distance = distance(word, candidate);
                if (distance < score) { score = distance; best = candidate; }
            }
            if (best != null && score <= (word.length() >= 5 ? 2 : 1)) result.add(casing(typed, best));
        }
        return result;
    }
    static String correction(String typed) {
        String word = typed.toLowerCase(Locale.ROOT);
        if (word.length() < 3 || word.length() > 32 || WORDS.contains(word)) return typed;
        // Correct only a unique one-edit candidate. Don't replace uncertain names or completions.
        String match = null;
        for (String candidate : WORDS) if (distance(word, candidate) == 1) {
            if (match != null) return typed;
            match = candidate;
        }
        return match == null ? typed : casing(typed, match);
    }
    private static String casing(String typed, String word) {
        if (typed.equals(typed.toUpperCase(Locale.ROOT))) return word.toUpperCase(Locale.ROOT);
        return Character.isUpperCase(typed.codePointAt(0))
                ? word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1) : word;
    }
    private static int distance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) d[i][0] = i;
        for (int j = 0; j <= b.length(); j++) d[0][j] = j;
        for (int i = 1; i <= a.length(); i++) for (int j = 1; j <= b.length(); j++) {
            d[i][j] = Math.min(Math.min(d[i-1][j] + 1, d[i][j-1] + 1),
                    d[i-1][j-1] + (a.charAt(i-1) == b.charAt(j-1) ? 0 : 1));
            if (i > 1 && j > 1 && a.charAt(i-1) == b.charAt(j-2) && a.charAt(i-2) == b.charAt(j-1))
                d[i][j] = Math.min(d[i][j], d[i-2][j-2] + 1);
        }
        return d[a.length()][b.length()];
    }
}
