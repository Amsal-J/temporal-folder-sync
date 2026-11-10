package foldersync;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

public class FolderSyncWorker {
    public static void main(String[] args) {
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        Worker worker = factory.newWorker(Shared.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(FolderSyncWorkflowImpl.class);
        worker.registerActivitiesImplementations(new FolderSyncActivitiesImpl());

        factory.start();
        System.out.println("Worker started. Polling task queue: " + Shared.TASK_QUEUE);
    }
}