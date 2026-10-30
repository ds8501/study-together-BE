# Task Scheduler (Java 17+)

A small in-memory scheduler used by background workers.

## Candidate brief
The worker occasionally executes jobs more than once, executes cancelled jobs, or accepts invalid state transitions. Investigate the implementation and fix the defects without weakening the requirements.

## Requirements
- Task IDs must be unique.
- Only due `PENDING` tasks can be claimed.
- A task can be claimed by at most one worker.
- Only `RUNNING` tasks can be completed.
- Only `PENDING` tasks can be cancelled.
- All scheduler state transitions must be thread-safe.

## Run
```bash
mkdir -p out
javac -d out src/main/java/interview/*.java src/test/java/interview/*.java
java -cp out interview.SchedulerTest
```

Some tests intentionally fail. Do not modify tests to make them pass.
