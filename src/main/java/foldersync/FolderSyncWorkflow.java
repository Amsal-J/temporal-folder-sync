package foldersync;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface FolderSyncWorkflow {

    @WorkflowMethod
    void sync(String sourceDir, String destDir, int pollingInterval, int loopsBeforeReset);
}