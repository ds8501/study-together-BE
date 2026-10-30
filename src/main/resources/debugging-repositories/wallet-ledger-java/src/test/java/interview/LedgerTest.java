package interview;

import java.math.BigDecimal;

public final class LedgerTest {
    private static int failures = 0;
    private static BigDecimal bd(String value) { return new BigDecimal(value); }
    private static void check(String name, boolean condition) {
        if (!condition) { failures++; System.out.println("FAIL: " + name); }
        else System.out.println("PASS: " + name);
    }

    public static void main(String[] args) {
        WalletLedger ledger = new WalletLedger();
        Wallet alice = new Wallet("alice", bd("100.00"));
        Wallet bob = new Wallet("bob", bd("20.00"));
        ledger.addWallet(alice); ledger.addWallet(bob);

        check("exact balance withdrawal succeeds", ledger.withdraw("w1", "alice", bd("100.00")));
        check("withdrawal leaves zero balance", alice.getBalance().compareTo(bd("0.00")) == 0);
        check("negative deposit rejected", !ledger.deposit("d-neg", "alice", bd("-5.00")));
        check("insufficient transfer rejected", !ledger.transfer("t-over", "alice", "bob", bd("1.00")));
        check("failed transfer leaves recipient unchanged", bob.getBalance().compareTo(bd("20.00")) == 0);

        check("valid transfer succeeds", ledger.transfer("t1", "bob", "alice", bd("5.00")));
        check("same transfer replayed only once",
                ledger.transfer("t1", "bob", "alice", bd("5.00"))
                && bob.getBalance().compareTo(bd("15.00")) == 0
                && alice.getBalance().compareTo(bd("5.00")) == 0);
        check("transaction ID cannot be reused with different payload",
                !ledger.deposit("t1", "alice", bd("10.00")));

        System.out.println(failures == 0 ? "ALL TESTS PASSED" : failures + " TEST(S) FAILED");
        if (failures > 0) System.exit(1);
    }
}
