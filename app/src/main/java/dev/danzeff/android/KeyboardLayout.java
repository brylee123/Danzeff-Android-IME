package dev.danzeff.android;

/**
 * Character data, with no Android dependency. Each 3x3 cell has slots in the
 * ORIGINAL Danzeff order: [triangle/top, square/left, cross/bottom, circle/right].
 * Backspace is represented as "\b"; empty slots cannot output a character.
 * Based on the publicly documented PSP layout, not on its original graphics.
 */
public final class KeyboardLayout {
    private KeyboardLayout() {}

    public static final int LOWER = 0;
    public static final int UPPER = 1;
    public static final int DIGITS = 2;
    public static final int SYMBOLS = 3;
    public static final int TOP = 0;
    public static final int LEFT = 1;
    public static final int BOTTOM = 2;
    public static final int RIGHT = 3;

    private static final String[][][][] KEYS = {
        { // Lowercase
            {{",","a","b","c"},{".","d","e","f"},{"!","g","h","i"}},
            {{"-","j","k","l"},{"\b","m"," ","n"},{"?","o","p","q"}},
            {{"(","r","s","t"},{":","u","v","w"},{")","x","y","z"}}
        },
        { // Uppercase, matched to the original PSP uppercase page
            {{"^","A","B","C"},{"@","D","E","F"},{"*","G","H","I"}},
            {{"_","J","K","L"},{"\b","M"," ","N"},{"\"","O","P","Q"}},
            {{"=","R","S","T"},{";","U","V","W"},{"/","X","Y","Z"}}
        },
        { // Digits; as in the original, most numbers use the right face button
            {{"","","","1"},{"","","","2"},{"","","","3"}},
            {{"","","","4"},{"\b",""," ","5"},{"","","","6"}},
            {{"","","","7"},{"","","","8"},{"","","0","9"}}
        },
        { // Symbols
            {{"'","(",".",")"},{"\"","<","'",">"},{"-","[","_","]"}},
            {{"!","{","?","}"},{"\b",""," ",""},{"+","\\","=","/"}},
            {{":","@",";","#"},{"~","$","`","%"},{"*","^","|","&"}}
        }
    };

    public static String at(int layer, int row, int col, int slot) {
        if (layer < 0 || layer >= KEYS.length || row < 0 || row > 2 ||
                col < 0 || col > 2 || slot < 0 || slot > 3) {
            return "";
        }
        return KEYS[layer][row][col][slot];
    }

    public static String display(String value) {
        if ("\b".equals(value)) return "⌫";
        if (" ".equals(value)) return "␣";
        if (value.isEmpty()) return "·";
        return value;
    }

    public static int layer(boolean numbers, boolean shifted) {
        return (numbers ? DIGITS : LOWER) + (shifted ? 1 : 0);
    }
}
