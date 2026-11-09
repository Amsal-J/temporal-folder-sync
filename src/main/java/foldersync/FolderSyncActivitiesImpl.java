package foldersync;

import io.temporal.activity.Activity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FolderSyncActivitiesImpl implements FolderSyncActivities {

    private static final Logger log = LoggerFactory.getLogger(FolderSyncActivitiesImpl.class);

    @Override
    public List<String> findNewFiles(String sourceDir, String destDir) {
        Path source = Paths.get(sourceDir);
        Path dest = Paths.get(destDir);

        try (Stream<Path> files = Files.list(source)) {
            List<String> newFiles = files
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .filter(name -> !Files.exists(dest.resolve(name)))
                    .sorted()
                    .toList();

            log.info("Found {} new file(s): {}", newFiles.size(), newFiles);
            return newFiles;
        } catch (IOException e) {
            throw Activity.wrap(e);
        }
    }

    @Override
    public void copyFile(String sourceDir, String destDir, String fileName) {
        Path source = Paths.get(sourceDir);
        Path dest = Paths.get(destDir);
        Path temp = Paths.get(destDir, "." + fileName + ".part");

        if (Files.exists(dest)) {
            log.info("Skipping {} (already in {})", fileName, destDir);
            return;
        }

        try {
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, dest, StandardCopyOption.ATOMIC_MOVE);
            log.info("Copied {} -> {}", source, dest);
        } catch (IOException e) {
            throw Activity.wrap(e);
        }
    }
}