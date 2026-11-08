package foldersync;

public final class Shared {
    public static final String TASK_QUEUE = "FOLDER_SYNC_TASK_QUEUE";
    public static final String WORKFLOW_ID = "folder-sync";
    public static final String SOURCE_DIR = "folder1";
    public static final String DEST_DIR = "folder2";
    public static final int POLLING_INTERVAL = 60;
    public static final int LOOPS_BEFORE_RESET = 100;

    private Shared() {}
}
