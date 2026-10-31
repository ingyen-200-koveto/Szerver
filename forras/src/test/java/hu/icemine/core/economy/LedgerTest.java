package hu.icemine.core.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hu.icemine.core.economy.Ledger.Result;
import org.junit.jupiter.api.Test;

class LedgerTest {

    private static final long MAX = 1_000_000_000L;
    private static final long TX = 10_000_000L;

    @Test
    void depositAccepted() {
        assertEquals(Result.OK, Ledger.checkDeposit(100, 50, MAX, TX));
    }

    @Test
    void depositRejectsZeroAndNegative() {
        assertEquals(Result.INVALID_AMOUNT, Ledger.checkDeposit(100, 0, MAX, TX));
        assertEquals(Result.INVALID_AMOUNT, Ledger.checkDeposit(100, -5, MAX, TX));
    }

    @Test
    void depositRejectsOverTransactionLimit() {
        assertEquals(Result.INVALID_AMOUNT, Ledger.checkDeposit(0, TX + 1, MAX, TX));
    }

    @Test
    void depositStopsAtMaxBalance() {
        assertEquals(Result.OK, Ledger.checkDeposit(MAX - 10, 10, MAX, TX));
        assertEquals(Result.LIMIT, Ledger.checkDeposit(MAX - 10, 11, MAX, TX));
        assertEquals(Result.LIMIT, Ledger.checkDeposit(-1, 10, MAX, TX));
    }

    @Test
    void depositDoesNotOverflow() {
        assertEquals(Result.LIMIT, Ledger.checkDeposit(Long.MAX_VALUE - 1, 5, Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test
    void withdrawNeedsEnoughMoney() {
        assertEquals(Result.OK, Ledger.checkWithdraw(100, 100, TX));
        assertEquals(Result.INSUFFICIENT, Ledger.checkWithdraw(99, 100, TX));
        assertEquals(Result.INVALID_AMOUNT, Ledger.checkWithdraw(100, 0, TX));
    }

    @Test
    void transferChecksBothSides() {
        assertEquals(Result.OK, Ledger.checkTransfer(500, 0, 200, MAX, TX));
        assertEquals(Result.INSUFFICIENT, Ledger.checkTransfer(100, 0, 200, MAX, TX));
        assertEquals(Result.LIMIT, Ledger.checkTransfer(500, MAX, 200, MAX, TX));
    }

    @Test
    void clampSetStaysInRange() {
        assertEquals(0, Ledger.clampSet(-50, MAX));
        assertEquals(MAX, Ledger.clampSet(MAX + 1, MAX));
        assertEquals(1234, Ledger.clampSet(1234, MAX));
    }

    @Test
    void parseAmountPlainAndSuffixed() {
        assertEquals(100, Ledger.parseAmount("100"));
        assertEquals(2_000, Ledger.parseAmount("2k"));
        assertEquals(3_000_000, Ledger.parseAmount("3M"));
        assertEquals(1_000, Ledger.parseAmount(" 1 000 "));
        assertEquals(5_000, Ledger.parseAmount("5_000"));
    }

    @Test
    void parseAmountRejectsGarbage() {
        assertEquals(-1, Ledger.parseAmount(null));
        assertEquals(-1, Ledger.parseAmount(""));
        assertEquals(-1, Ledger.parseAmount("k"));
        assertEquals(-1, Ledger.parseAmount("abc"));
        assertEquals(-1, Ledger.parseAmount("-5"));
        assertEquals(-1, Ledger.parseAmount("1.5k"));
        assertEquals(-1, Ledger.parseAmount("1234567890123456"));
    }

    @Test
    void parseAmountRejectsOverflow() {
        assertEquals(9_223_372_036_854_000_000L, Ledger.parseAmount("9223372036854m"));
        assertEquals(-1, Ledger.parseAmount("9223372036855m"));
    }
}
