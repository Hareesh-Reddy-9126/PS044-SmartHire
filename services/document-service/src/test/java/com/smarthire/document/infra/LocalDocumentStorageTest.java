package com.smarthire.document.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link LocalDocumentStorage}. */
class LocalDocumentStorageTest {

  @TempDir Path tempDir;
  private LocalDocumentStorage storage;

  @BeforeEach
  void setUp() {
    storage = new LocalDocumentStorage(tempDir.toString());
  }

  @Nested
  @DisplayName("Store")
  class Store {

    @Test
    @DisplayName("Stores file and returns storage key")
    void storesFile() throws Exception {
      String key = "test/document.pdf";
      InputStream content = new ByteArrayInputStream("test content".getBytes());

      String result = storage.store(key, content);

      assertThat(result).isEqualTo(key);
      assertThat(Files.exists(tempDir.resolve(key))).isTrue();
    }

    @Test
    @DisplayName("Creates parent directories if needed")
    void createsParentDirectories() throws Exception {
      String key = "candidates/123/resume/test.pdf";
      InputStream content = new ByteArrayInputStream("test".getBytes());

      storage.store(key, content);

      assertThat(Files.exists(tempDir.resolve(key))).isTrue();
    }

    @Test
    @DisplayName("Rejects empty storage key")
    void rejectsEmptyKey() {
      InputStream content = new ByteArrayInputStream("test".getBytes());

      assertThatThrownBy(() -> storage.store("", content))
          .isInstanceOf(DocumentStorage.StorageException.class)
          .hasMessageContaining("cannot be null or empty");
    }

    @Test
    @DisplayName("Rejects null storage key")
    void rejectsNullKey() {
      InputStream content = new ByteArrayInputStream("test".getBytes());

      assertThatThrownBy(() -> storage.store(null, content))
          .isInstanceOf(DocumentStorage.StorageException.class)
          .hasMessageContaining("cannot be null or empty");
    }
  }

  @Nested
  @DisplayName("Retrieve")
  class Retrieve {

    @Test
    @DisplayName("Retrieves stored file content")
    void retrievesContent() throws Exception {
      String key = "test/document.pdf";
      String originalContent = "test content";
      InputStream content = new ByteArrayInputStream(originalContent.getBytes());

      storage.store(key, content);

      try (InputStream retrieved = storage.retrieve(key)) {
        assertThat(new String(retrieved.readAllBytes())).isEqualTo(originalContent);
      }
    }

    @Test
    @DisplayName("Throws for non-existent file")
    void throwsForMissingFile() {
      assertThatThrownBy(() -> storage.retrieve("non/existent.pdf"))
          .isInstanceOf(DocumentStorage.StorageException.class)
          .hasMessageContaining("not found");
    }
  }

  @Nested
  @DisplayName("Delete")
  class Delete {

    @Test
    @DisplayName("Deletes stored file")
    void deletesFile() throws Exception {
      String key = "test/document.pdf";
      InputStream content = new ByteArrayInputStream("test".getBytes());

      storage.store(key, content);
      storage.delete(key);

      assertThat(Files.exists(tempDir.resolve(key))).isFalse();
    }

    @Test
    @DisplayName("Does not throw for non-existent file")
    void noThrowForMissingFile() throws Exception {
      storage.delete("non/existent.pdf");
      // Should not throw
    }
  }

  @Nested
  @DisplayName("Exists")
  class Exists {

    @Test
    @DisplayName("Returns true for existing file")
    void returnsTrueForExisting() throws Exception {
      String key = "test/document.pdf";
      InputStream content = new ByteArrayInputStream("test".getBytes());

      storage.store(key, content);

      assertThat(storage.exists(key)).isTrue();
    }

    @Test
    @DisplayName("Returns false for non-existent file")
    void returnsFalseForMissing() {
      assertThat(storage.exists("non/existent.pdf")).isFalse();
    }
  }

  @Nested
  @DisplayName("Path traversal protection")
  class PathTraversal {

    @Test
    @DisplayName("Rejects absolute paths")
    void rejectsAbsolutePath() {
      assertThatThrownBy(
              () -> storage.store("/etc/passwd", new ByteArrayInputStream("x".getBytes())))
          .isInstanceOf(DocumentStorage.StorageException.class)
          .hasMessageContaining("path traversal");
    }

    @Test
    @DisplayName("Rejects parent directory traversal")
    void rejectsParentTraversal() {
      assertThatThrownBy(
              () -> storage.store("../etc/passwd", new ByteArrayInputStream("x".getBytes())))
          .isInstanceOf(DocumentStorage.StorageException.class)
          .hasMessageContaining("path traversal");
    }

    @Test
    @DisplayName("Rejects nested traversal")
    void rejectsNestedTraversal() {
      assertThatThrownBy(
              () ->
                  storage.store("test/../../etc/passwd", new ByteArrayInputStream("x".getBytes())))
          .isInstanceOf(DocumentStorage.StorageException.class)
          .hasMessageContaining("path traversal");
    }

    @Test
    @DisplayName("Allows valid relative paths")
    void allowsValidPaths() throws Exception {
      String key = "candidates/123/resume/file.pdf";
      InputStream content = new ByteArrayInputStream("test".getBytes());

      storage.store(key, content);

      assertThat(Files.exists(tempDir.resolve(key))).isTrue();
    }
  }
}
