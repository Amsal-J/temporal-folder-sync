package foldersync;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FolderSyncActivitiesImplTest {

    @TempDir 
    Path tempDir;

    private Path folder1;
    private Path folder2;
    private FolderSyncActivitiesImpl activities;

    @BeforeEach 
    void setUp() throws IOException {
        folder1 = Files.createDirectory(tempDir.resolve("folder1"));
        folder2 = Files.createDirectory(tempDir.resolve("folder2"));
        activities = new FolderSyncActivitiesImpl();
    }

    // -------------- helpers --------------

    private void createFile(Path folder, String name, String content) throws IOException {
        Files.writeString(folder.resolve(name), content);
    }

    private List<String> namesIn(Path folder) throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    private List<String> findNew() {
        return activities.findNewFiles(folder1.toString(), folder2.toString());
    }

    // -------------- findNewFiles --------------

    @Test 
    void findNewFiles_returnsEmptyList_whenFolder1IsEmpty() {
        assertTrue(findNew().isEmpty());
    }

    @Test 
    void findNewFiles_returnsFilesThatAreOnlyInFolder1() throws IOException {
        createFile(folder1, "a.txt", "A");
        createFile(folder1, "b.txt", "B");

        assertEquals(List.of("a.txt", "b.txt"), findNew());
    }

    @Test 
    void findNewFiles_skipsFilesAlreadyInFolder2() throws IOException {
        createFile(folder1, "a.txt", "A");
        createFile(folder1, "b.txt", "B");
        createFile(folder2, "a.txt", "A");

        assertEquals(List.of("b.txt"), findNew());
    }

    @Test
    void findNewFiles_skipsHiddenFiles() throws IOException {
        createFile(folder1, ".DS_Score", "junk");
        createFile(folder1, "visible.txt", "hi");

        assertEquals(List.of("visible.txt"), findNew());
    }

    @Test 
    void findNewFiles_skipsSubfolders() throws IOException {
        Files.createDirectory(folder1.resolve("subfolder"));
        createFile(folder1, "file.txt", "hi");

        assertEquals(List.of("file.txt"), findNew());
    }

    @Test 
    void findNewFiles_returnsNamesInAlphabeticalOrder() throws IOException {
        createFile(folder1, "c.txt", "C");
        createFile(folder1, "a.txt", "A");
        createFile(folder1, "b.txt", "B");

        assertEquals(List.of("a.txt", "b.txt", "c.txt"), findNew());
    }

    @Test 
    void findNewFiles_throws_whenFolder1DoesNotExist() {
        String missing = tempDir.resolve("does-not-exist").toString();

        assertThrows(RuntimeException.class, () -> activities.findNewFiles(missing, folder2.toString()));
    }

    // -------------- copyFile --------------

    @Test 
    void copyFile_copiesFileWithSameContent_andKeepsOriginal() throws IOException {
        createFile(folder1, "a.txt", "hello");

        activities.copyFile(folder1.toString(), folder2.toString(), "a.txt");

        assertEquals("hello", Files.readString(folder2.resolve("a.txt")));
        assertTrue(Files.exists(folder1.resolve("a.txt")), "original should still be in folder1");
    }

    @Test 
    void copyFile_doesNotOverwrite_whenFileAlreadyInFolder2() throws IOException {
        createFile(folder1, "a.txt", "new version");
        createFile(folder2, "a.txt", "old version");

        activities.copyFile(folder1.toString(), folder2.toString(), "a.txt");

        assertEquals("old version", Files.readString(folder2.resolve("a.txt")));
    }

    @Test 
    void copyFile_skipsQuietly_whenSourceFileIsMissing() throws IOException {
        assertDoesNotThrow(() -> activities.copyFile(folder1.toString(), folder2.toString(), "ghost.txt"));
        assertTrue(namesIn(folder2).isEmpty());
    }

    @Test 
    void copyFile_leavesNoTempFileBehind() throws IOException {
        createFile(folder1, "a.txt", "hello");

        activities.copyFile(folder1.toString(), folder2.toString(), "a.txt");

        assertEquals(List.of("a.txt"), namesIn(folder2));
    }
}