package foldersync;

import static org.junit.jupiter.api.Assertions.*;

import io.temporal.activity.Activity;
import io.temporal.activity.ActivityInfo;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FolderSyncWorkflowImplTest {

    private static final String TASK_QUEUE = "test-queue";

    private TestWorkflowEnvironment testEnv;
    private WorkflowClient client;
    private FakeActivities fake;

    // ---------- fake activities (behave like real folders) ----------

    public static class FakeActivities implements FolderSyncActivities {
        // Controls (set by each test)
        final Set<String> folder1 = ConcurrentHashMap.newKeySet();
        final AtomicInteger scanFailuresRemaining = new AtomicInteger(0);
        final Set<String> filesThatFailToCopy = ConcurrentHashMap.newKeySet();

        // Recordings (checked by each test)
        final Set<String> scanIds = ConcurrentHashMap.newKeySet();   // one per poll, even if re-run
        final AtomicInteger scanExecutions = new AtomicInteger(0);    // every run, including repeats
        final List<String> copyAttempts = new CopyOnWriteArrayList<>();
        final CopyOnWriteArrayList<String> copied = new CopyOnWriteArrayList<>(); // acts as folder2

        @Override
        public List<String> findNewFiles(String sourceDir, String destDir) {
            ActivityInfo info = Activity.getExecutionContext().getInfo();
            scanIds.add(info.getRunId() + ":" + info.getActivityId());
            scanExecutions.incrementAndGet();

            if (scanFailuresRemaining.getAndDecrement() > 0) {
                throw new RuntimeException("simulated scan failure");
            }
            return folder1.stream().filter(f -> !copied.contains(f)).sorted().toList();
        }

        @Override
        public void copyFile(String sourceDir, String destDir, String fileName) {
            copyAttempts.add(fileName);
            if (filesThatFailToCopy.contains(fileName)) {
                throw new RuntimeException("simulated copy failure: " + fileName);
            }
            copied.addIfAbsent(fileName);   // safe to repeat, like the real copyFile
        }
    }

    // ---------- setup / teardown ----------

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnv.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(FolderSyncWorkflowImpl.class);

        fake = new FakeActivities();
        worker.registerActivitiesImplementations(fake);

        testEnv.start();
        client = testEnv.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    // ---------- helpers ----------

    private void startWorkflow(int pollSeconds, int loopsBeforeReset) {
        FolderSyncWorkflow workflow = client.newWorkflowStub(
                FolderSyncWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(TASK_QUEUE)
                        .setWorkflowId("test-folder-sync")
                        .build());

        WorkflowClient.start(workflow::sync, "folder1", "folder2", pollSeconds, loopsBeforeReset);
    }

    private void sleepUntil(BooleanSupplier condition, Duration max) {
        Duration step = Duration.ofSeconds(10);
        for (Duration waited = Duration.ZERO; waited.compareTo(max) < 0; waited = waited.plus(step)) {
            if (condition.getAsBoolean()) {
                return;
            }
            testEnv.sleep(step);
        }
    }

    // ---------- tests ----------

    @Test
    void copiesEveryFileTheScanFinds() {
        fake.folder1.addAll(List.of("a.txt", "b.txt"));

        startWorkflow(60, 100);
        sleepUntil(() -> fake.copied.size() == 2, Duration.ofSeconds(30));

        assertEquals(List.of("a.txt", "b.txt"), fake.copied);
    }

    @Test
    void scansAgainAfterEachPollInterval() {
        long start = testEnv.currentTimeMillis();
        startWorkflow(60, 100);
        testEnv.sleep(Duration.ofSeconds(210));

        int polls = fake.scanIds.size();
        long elapsedSeconds = (testEnv.currentTimeMillis() - start) / 1000;
        long expected = elapsedSeconds / 60 + 1;   // one at the start, then one per minute

        assertTrue(Math.abs(polls - expected) <= 1,
                "expected about " + expected + " polls for " + elapsedSeconds + "s, got " + polls
                        + " (total scan runs incl. repeats: " + fake.scanExecutions.get() + ")");
    }

    @Test
    void doesNotCopyAnything_whenFolder1IsEmpty() {
        startWorkflow(60, 100);
        testEnv.sleep(Duration.ofMinutes(3));

        assertTrue(fake.copyAttempts.isEmpty());
    }

    @Test
    void oneFailingFile_doesNotStopTheOthers() {
        fake.folder1.addAll(List.of("a.txt", "bad.txt", "c.txt"));
        fake.filesThatFailToCopy.add("bad.txt");

        startWorkflow(60, 100);
        sleepUntil(() -> fake.copied.size() == 2, Duration.ofMinutes(3));

        assertEquals(List.of("a.txt", "c.txt"), fake.copied);
        assertTrue(Collections.frequency(fake.copyAttempts, "bad.txt") >= 5,
                "bad.txt should be retried at least 5 times (MaximumAttempts)");
    }

    @Test
    void failedScan_doesNotKillTheWorkflow() {
        fake.folder1.add("a.txt");
        fake.scanFailuresRemaining.set(5);   // the whole first scan fails (all 5 attempts)

        startWorkflow(60, 100);
        sleepUntil(() -> !fake.copied.isEmpty(), Duration.ofMinutes(15));

        assertEquals(List.of("a.txt"), fake.copied,
                "polls: " + fake.scanIds.size() + ", scan runs: " + fake.scanExecutions.get());
    }

    @Test
    void keepsPolling_afterContinueAsNew() {
        startWorkflow(60, 2);                     // reset history every 2 loops
        testEnv.sleep(Duration.ofSeconds(330));   // 5.5 minutes

        assertTrue(fake.scanIds.size() >= 5,
                "expected at least 5 polls but got " + fake.scanIds.size());
    }
}