package foldersync;

import io.temporal.activity.ActivityInterface;
import java.util.List;

@ActivityInterface
public interface FolderSyncActivities {
    List<String> findNewFiles(String sourceDir, String destDir);
    void copyFile(String sourceDir, String destDir, String fileName);
}
