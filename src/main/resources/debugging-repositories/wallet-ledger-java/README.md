# Wallet Ledger (Java 17+)

A minimal wallet service supporting deposits, withdrawals, and transfers.

## Requirements
- Amounts must be strictly positive.
- A withdrawal must never overdraw a wallet.
- Transfers must preserve the total balance.
- A transaction ID is an idempotency key.
- Reusing an ID with a different operation or payload must be rejected.
- Failed operations must not mutate balances.

## Run
```bash
mkdir -p out
javac -d out src/main/java/interview/*.java src/test/java/interview/*.java
java -cp out interview.LedgerTest
```

Some tests intentionally fail. Do not modify tests to make them pass.
