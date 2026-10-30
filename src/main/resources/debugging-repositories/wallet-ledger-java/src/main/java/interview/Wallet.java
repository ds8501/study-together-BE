package interview;

import java.math.BigDecimal;

public final class Wallet {
    private final String id;
    private BigDecimal balance;

    public Wallet(String id, BigDecimal openingBalance) {
        this.id = id;
        this.balance = openingBalance;
    }

    public String getId() { return id; }
    public BigDecimal getBalance() { return balance; }
    void debit(BigDecimal amount) { balance = balance.subtract(amount); }
    void credit(BigDecimal amount) { balance = balance.add(amount); }
}
