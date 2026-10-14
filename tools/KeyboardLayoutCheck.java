package dev.danzeff.android;

/** Dependency-free checks for the original character map (run with javac/java). */
public final class KeyboardLayoutCheck {
    private static void check(boolean predicate, String message) {
        if (!predicate) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        StringBuilder all = new StringBuilder();
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++)
            for (int k = 0; k < 4; k++) all.append(KeyboardLayout.at(0,r,c,k));
        for (char letter = 'a'; letter <= 'z'; letter++)
            check(all.indexOf(String.valueOf(letter)) >= 0, "Missing " + letter);
        check("\b".equals(KeyboardLayout.at(0,1,1,0)), "Center/top should delete");
        check(" ".equals(KeyboardLayout.at(0,1,1,2)), "Center/bottom should space");
        check("a".equals(KeyboardLayout.at(0,0,0,1)), "Triangle/square order changed");
        check("C".equals(KeyboardLayout.at(1,0,0,3)), "Uppercase mapping");
        check("0".equals(KeyboardLayout.at(2,2,2,2)), "Zero digit missing");
        check("\\".equals(KeyboardLayout.at(3,1,2,1)), "Symbols mapping");
        check("".equals(KeyboardLayout.at(2,0,0,0)), "Empty digit slot should be blank");
        check("".equals(KeyboardLayout.at(-1,4,4,4)), "Out-of-range should be safe");
        System.out.println("PASS: keyboard map, alphabet, digits, symbols, bounds");
    }
}
