package hu.icemine.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TextTest {

    @Test
    void colorCodesAreTranslated() {
        assertEquals("§bIce§fMine", Text.color("&bIce&fMine"));
        assertEquals("§l§nX", Text.color("&L&NX"));
        assertEquals("100&", Text.color("100&"));
        assertEquals("a & b", Text.color("a & b"));
        assertEquals("", Text.color((String) null));
    }

    @Test
    void hexColorsAreTranslated() {
        assertEquals("§x§3§d§a§9§f§cX", Text.color("&#3DA9FCX"));
    }

    @Test
    void stripRemovesBothStyles() {
        assertEquals("IceMine", Text.strip("§bIce&fMine"));
        assertEquals("a & b", Text.strip("a & b"));
    }

    @Test
    void numbersUseSpaceSeparator() {
        assertEquals("0", Text.number(0));
        assertEquals("999", Text.number(999));
        assertEquals("1 000", Text.number(1000));
        assertEquals("1 234 567", Text.number(1_234_567));
        assertEquals("-12 500", Text.number(-12_500));
    }

    @Test
    void timerFormat() {
        assertEquals("00:00.000", Text.timer(-5));
        assertEquals("01:05.123", Text.timer(65_123));
        assertEquals("61:00.000", Text.timer(3_660_000));
    }

    @Test
    void durationFormat() {
        assertEquals("0 perc", Text.duration(30_000));
        assertEquals("2 óra 5 perc", Text.duration((125L * 60_000)));
        assertEquals("1 nap 3 óra", Text.duration(27L * 3_600_000));
    }

    @Test
    void ratioHandlesZeroDeaths() {
        assertEquals("5.00", Text.ratio(5, 0));
        assertEquals("0.33", Text.ratio(1, 3));
    }

    @Test
    void barIsClamped() {
        assertEquals("##--", Text.bar(0.5, 4, "#", "-"));
        assertEquals("####", Text.bar(3.0, 4, "#", "-"));
        assertEquals("----", Text.bar(-1.0, 4, "#", "-"));
    }

    @Test
    void replaceWorksInPairs() {
        assertEquals("Szia Noel, 5 pont", Text.replace("Szia {player}, {points} pont", "{player}", "Noel", "{points}", "5"));
        assertEquals("x", Text.replace("x{a}", "{a}", null));
        assertEquals("", Text.replace(null, "a", "b"));
    }

    @Test
    void decimalsForMultipliers() {
        assertEquals("1", Text.decimal(1.0));
        assertEquals("1,5", Text.decimal(1.5));
        assertEquals("1,75", Text.decimal(1.75));
    }
}
