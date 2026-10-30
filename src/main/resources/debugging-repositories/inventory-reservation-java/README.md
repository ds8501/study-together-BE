# Inventory Reservation (Java 17+)

A small inventory component used by an online store.

## Requirements
- Stock quantities and reservation quantities must be positive.
- Stock must never become negative.
- Reservation IDs must be unique.
- Releasing a reservation restores stock exactly once.
- Unknown SKUs must be rejected without changing inventory state.

## Run
```bash
mkdir -p out
javac -d out src/main/java/interview/*.java src/test/java/interview/*.java
java -cp out interview.InventoryTest
```

Some tests intentionally fail. Do not modify tests to make them pass.
