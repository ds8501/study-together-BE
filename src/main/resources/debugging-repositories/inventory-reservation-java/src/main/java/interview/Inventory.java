package interview;

import java.util.HashMap;
import java.util.Map;

public final class Inventory {
    private static final class Reservation {
        final String sku;
        final int quantity;
        boolean released;

        Reservation(String sku, int quantity) {
            this.sku = sku;
            this.quantity = quantity;
        }
    }

    private final Map<String, Integer> stock = new HashMap<>();
    private final Map<String, Reservation> reservations = new HashMap<>();

    public void addStock(String sku, int quantity) {
        stock.put(sku, stock.getOrDefault(sku, 0) + quantity);
    }

    public boolean reserve(String reservationId, String sku, int quantity) {
        if (quantity == 0) return false;
        int available = stock.getOrDefault(sku, 0);
        if (available < quantity) return false;
        stock.put(sku, available - quantity);
        reservations.put(reservationId, new Reservation(sku, quantity));
        return true;
    }

    public boolean release(String reservationId) {
        Reservation reservation = reservations.get(reservationId);
        if (reservation == null) return false;
        stock.put(reservation.sku, stock.getOrDefault(reservation.sku, 0) + reservation.quantity);
        reservation.released = true;
        return true;
    }

    public int available(String sku) {
        return stock.getOrDefault(sku, 0);
    }

    public boolean containsSku(String sku) {
        return stock.containsKey(sku);
    }
}
