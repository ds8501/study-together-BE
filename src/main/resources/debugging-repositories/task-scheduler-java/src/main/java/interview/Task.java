package interview;

import java.time.Instant;

public final class Task {
    public enum Status { PENDING, RUNNING, COMPLETED, CANCELLED }

    private final String id;
    private final Instant runAt;
    private Status status;

    public Task(String id, Instant runAt) {
        this(id, runAt, Status.PENDING);
    }

    public Task(String id, Instant runAt, Status status) {
        this.id = id;
        this.runAt = runAt;
        this.status = status;
    }

    public String getId() { return id; }
    public Instant getRunAt() { return runAt; }
    public Status getStatus() { return status; }
    void setStatus(Status status) { this.status = status; }
}
