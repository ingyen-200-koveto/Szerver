package hu.icemine.core.economy;

public final class Ledger {

    public enum Result {
        OK,
        INVALID_AMOUNT,
        INSUFFICIENT,
        LIMIT,
        UNKNOWN_CURRENCY,
        NOT_LOADED,
        SAME_PLAYER,
        COOLDOWN,
        DISABLED
    }

    private Ledger() {
    }

    public static Result checkDeposit(long balance, long amount, long maxBalance, long maxTransaction) {
        if (amount <= 0 || amount > maxTransaction) {
            return Result.INVALID_AMOUNT;
        }
        if (balance < 0 || balance > maxBalance - amount) {
            return Result.LIMIT;
        }
        return Result.OK;
    }

    public static Result checkWithdraw(long balance, long amount, long maxTransaction) {
        if (amount <= 0 || amount > maxTransaction) {
            return Result.INVALID_AMOUNT;
        }
        if (balance < amount) {
            return Result.INSUFFICIENT;
        }
        return Result.OK;
    }

    public static Result checkTransfer(long fromBalance, long toBalance, long amount, long maxBalance, long maxTransaction) {
        Result withdraw = checkWithdraw(fromBalance, amount, maxTransaction);
        if (withdraw != Result.OK) {
            return withdraw;
        }
        return checkDeposit(toBalance, amount, maxBalance, maxTransaction);
    }

    public static long clampSet(long amount, long maxBalance) {
        return Math.max(0, Math.min(maxBalance, amount));
    }

    public static long parseAmount(String input) {
        if (input == null) {
            return -1;
        }
        String clean = input.trim().replace(" ", "").replace("_", "").toLowerCase();
        long multiplier = 1;
        if (clean.endsWith("k")) {
            multiplier = 1_000;
            clean = clean.substring(0, clean.length() - 1);
        } else if (clean.endsWith("m")) {
            multiplier = 1_000_000;
            clean = clean.substring(0, clean.length() - 1);
        }
        if (clean.isEmpty() || clean.length() > 15 || !clean.matches("\\d+")) {
            return -1;
        }
        long value = Long.parseLong(clean);
        if (value > Long.MAX_VALUE / multiplier) {
            return -1;
        }
        return value * multiplier;
    }
}
