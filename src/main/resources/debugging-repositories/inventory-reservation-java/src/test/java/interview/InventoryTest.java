package interview;

public final class InventoryTest {
    private static int failures = 0;
    private static void check(String name, boolean condition) {
        if (!condition) { failures++; System.out.println("FAIL: " + name); }
        else System.out.println("PASS: " + name);
    }

    public static void main(String[] args) {
        Inventory inventory = new Inventory();
        inventory.addStock("book", 10);
        check("initial stock is 10", inventory.available("book") == 10);

        inventory.addStock("book", -3);
        check("negative stock addition rejected", inventory.available("book") == 10);
        check("negative reservation rejected", !inventory.reserve("r-neg", "book", -2));
        check("valid reservation succeeds", inventory.reserve("r1", "book", 4));
        check("available stock decreases", inventory.available("book") == 6);
        check("duplicate reservation ID rejected", !inventory.reserve("r1", "book", 1));
        check("first release succeeds", inventory.release("r1"));
        check("second release rejected", !inventory.release("r1"));
        check("release restores stock once", inventory.available("book") == 10);
        check("unknown SKU rejected", !inventory.reserve("r-missing", "missing", 1));
        check("unknown SKU lookup does not create SKU", !inventory.containsSku("missing"));

        System.out.println(failures == 0 ? "ALL TESTS PASSED" : failures + " TEST(S) FAILED");
        if (failures > 0) System.exit(1);
    }
}
