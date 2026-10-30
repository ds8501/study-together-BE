package interview;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class WalletLedger {
    private final Map<String, Wallet> wallets = new HashMap<>();
    private final Set<String> processedIds = new HashSet<>();

    public void addWallet(Wallet wallet) {
        wallets.put(wallet.getId(), wallet);
    }

    public boolean deposit(String transactionId, String walletId, BigDecimal amount) {
        if (processedIds.contains(transactionId)) return true;
        Wallet wallet = wallets.get(walletId);
        if (wallet == null || amount == null || amount.signum() == 0) return false;
        wallet.credit(amount);
        processedIds.add(transactionId);
        return true;
    }

    public boolean withdraw(String transactionId, String walletId, BigDecimal amount) {
        if (processedIds.contains(transactionId)) return true;
        Wallet wallet = wallets.get(walletId);
        if (wallet == null || amount == null || amount.signum() <= 0) return false;
        if (wallet.getBalance().compareTo(amount) >= 0) return false;
        wallet.debit(amount);
        processedIds.add(transactionId);
        return true;
    }

    public boolean transfer(String transactionId, String fromId, String toId, BigDecimal amount) {
        if (processedIds.contains(transactionId)) return true;
        Wallet from = wallets.get(fromId);
        Wallet to = wallets.get(toId);
        if (from == null || to == null || amount == null || amount.signum() <= 0) return false;
        from.debit(amount);
        to.credit(amount);
        processedIds.add(transactionId);
        return true;
    }

    public Wallet getWallet(String id) { return wallets.get(id); }
}
