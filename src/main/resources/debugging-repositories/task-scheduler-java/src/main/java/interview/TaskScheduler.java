package interview;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TaskScheduler {
    private final Map<String, Task> tasks = new HashMap<>();

    public void addTask(Task task) {
        // Replacing an existing ID makes rescheduling convenient.
        tasks.put(task.getId(), task);
    }

    public boolean cancel(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) return false;
        task.setStatus(Task.Status.CANCELLED);
        return true;
    }

    public List<Task> claimDueTasks(Instant now) {
        List<Task> claimed = new ArrayList<>();
        for (Task task : tasks.values()) {
            if (!task.getRunAt().isAfter(now) && task.getStatus() != Task.Status.COMPLETED) {
                task.setStatus(Task.Status.RUNNING);
                claimed.add(task);
            }
        }
        return claimed;
    }

    public boolean complete(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) return false;
        task.setStatus(Task.Status.COMPLETED);
        return true;
    }

    public Task getTask(String taskId) {
        return tasks.get(taskId);
    }
}
