package interview;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class SchedulerTest {
    private static int failures = 0;
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private static void check(String name, boolean condition) {
        if (!condition) { failures++; System.out.println("FAIL: " + name); }
        else System.out.println("PASS: " + name);
    }

    public static void main(String[] args) throws Exception {
        TaskScheduler scheduler = new TaskScheduler();
        scheduler.addTask(new Task("due", NOW.minusSeconds(1)));
        scheduler.addTask(new Task("future", NOW.plusSeconds(60)));
        scheduler.addTask(new Task("cancelled", NOW.minusSeconds(1), Task.Status.CANCELLED));
        List<Task> first = scheduler.claimDueTasks(NOW);
        check("only pending due task is claimed", first.size() == 1 && first.get(0).getId().equals("due"));

        TaskScheduler duplicate = new TaskScheduler();
        duplicate.addTask(new Task("same", NOW));
        boolean duplicateRejected = false;
        try { duplicate.addTask(new Task("same", NOW.plusSeconds(5))); }
        catch (IllegalArgumentException expected) { duplicateRejected = true; }
        check("duplicate task ID rejected", duplicateRejected);

        TaskScheduler idempotent = new TaskScheduler();
        idempotent.addTask(new Task("once", NOW.minusSeconds(1)));
        check("first claim returns task", idempotent.claimDueTasks(NOW).size() == 1);
        check("second claim does not return task again", idempotent.claimDueTasks(NOW).isEmpty());

        TaskScheduler transitions = new TaskScheduler();
        transitions.addTask(new Task("pending", NOW));
        check("pending task cannot be completed", !transitions.complete("pending"));
        check("pending task remains pending", transitions.getTask("pending").getStatus() == Task.Status.PENDING);
        check("pending task can be cancelled", transitions.cancel("pending"));
        check("cancelled task cannot be completed", !transitions.complete("pending"));

        TaskScheduler concurrent = new TaskScheduler();
        concurrent.addTask(new Task("one", NOW.minusSeconds(1)));
        int workers = 8;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger total = new AtomicInteger();
        Thread[] threads = new Thread[workers];
        for (int i = 0; i < workers; i++) {
            threads[i] = new Thread(() -> {
                ready.countDown();
                try { start.await(); total.addAndGet(concurrent.claimDueTasks(NOW).size()); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            threads[i].start();
        }
        ready.await(); start.countDown();
        for (Thread thread : threads) thread.join();
        check("concurrent workers claim task only once", total.get() == 1);

        System.out.println(failures == 0 ? "ALL TESTS PASSED" : failures + " TEST(S) FAILED");
        if (failures > 0) System.exit(1);
    }
}
