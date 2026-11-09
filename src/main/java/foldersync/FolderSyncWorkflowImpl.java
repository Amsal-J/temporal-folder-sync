package foldersync;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;

public class FolderSyncWorkflowImpl implements FolderSyncWorkflow {

    private static final Logger log = Workflow.getLogger(FolderSyncWorkflowImpl.class);

    private final FolderSyncActivities activities = Workflow.newActivityStub(
        FolderSyncActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(5))
            .setRetryOptions(RetryOptions.newBuilder()
                .setInitialInterval(Duration.ofSeconds(5))
                .setBackoffCoefficient(2.0)
                .setMaximumInterval(Duration.ofMinutes(1))
                .setMaximumAttempts(5)
                .build())
            .build());

    @Override
    public void sync(String sourceDir, String destDir, int pollingInterval, int loopsBeforeReset) {
        for (int i = 0; i < loopsBeforeReset; i++) {
            try {
                List<String> newFiles = activities.findNewFiles(sourceDir, destDir);

                for (String fileName : newFiles) {
                    try {
                        activities.copyFile(sourceDir, destDir, fileName);
                    } catch (ActivityFailure e) {
                        log.warn("Could not copy {} after retries; will try again next poll", fileName);
                    }
                }
            } catch (ActivityFailure e) {
                log.warn("Could not scan {} after retries; will try again next poll", sourceDir);
            }
            Workflow.sleep(Duration.ofSeconds(pollingInterval));
        }
        Workflow.continueAsNew(sourceDir, destDir, pollingInterval, loopsBeforeReset);
    }
}
