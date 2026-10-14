package dev.danzeff.android;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class KeyboardSymbolsTest {
    @Test public void extraPageMatchesEveryFlipperSlot() {
        String[][][] expected = {
            {{"@", "#", "$", "%"}, {"^", "&", "*", "+"}, {"-", "_", "=", "~"}},
            {{"<", "[", "]", ">"}, {"\b", "0", " ", "."}, {"\\", "/", "|", ":"}},
            {{"!", "?", ",", ";"}, {"'", "\"", "`", "("}, {")", "{", "}", "_"}}
        };
        for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++)
            for (int slot = 0; slot < 4; slot++)
                assertEquals(expected[row][col][slot], KeyboardLayout.at(KeyboardLayout.EXTRA_SYMBOLS, row, col, slot));
    }
    @Test public void footerCyclesDigitsSymbolsExtraAndLettersAndDirectActionsWork() {
        var controller = Robolectric.buildService(DanzeffImeService.class).create();
        DanzeffImeService ime = controller.get();
        assertEquals(KeyboardLayout.LOWER, ime.currentLayer());
        ime.onNumbers(); assertEquals(KeyboardLayout.DIGITS, ime.currentLayer());
        ime.onShift(); assertEquals(KeyboardLayout.SYMBOLS, ime.currentLayer());
        ime.onShift(); assertEquals(KeyboardLayout.EXTRA_SYMBOLS, ime.currentLayer());
        ime.onChooseSlot(1); assertEquals("Typing must leave EX+ open", KeyboardLayout.EXTRA_SYMBOLS, ime.currentLayer());
        ime.onShift(); assertEquals(KeyboardLayout.DIGITS, ime.currentLayer());
        ime.onExtraSymbols(); assertEquals(KeyboardLayout.EXTRA_SYMBOLS, ime.currentLayer());
        ime.onNumbers(); assertEquals(KeyboardLayout.LOWER, ime.currentLayer());
        ime.onSymbols(); assertEquals(KeyboardLayout.SYMBOLS, ime.currentLayer());
        ime.onNumbers(); ime.onShift(); assertEquals(KeyboardLayout.UPPER, ime.currentLayer());
        ime.onChooseSlot(1); assertEquals(KeyboardLayout.LOWER, ime.currentLayer());
        assertTrue(ControllerProfile.validAction("symbols"));
        assertTrue(ControllerProfile.validAction("extra_symbols"));
        controller.destroy();
    }
}