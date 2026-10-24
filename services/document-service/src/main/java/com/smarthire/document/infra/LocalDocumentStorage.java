package com.smarthire.document.infra;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Local filesystem implementation of {@link DocumentStorage}.
 *
 * <p>Files are stored under a configurable root directory. The storage key is used as a relative
 * path within that root. Path traversal is prevented by normalizing the path and ensuring it stays
 * within the root directory.
 */
@Component
public class LocalDocumentStorage implements DocumentStorage {

  private static final Logger log = LoggerFactory.getLogger(LocalDocumentStorage.class);

  private final Path rootDir;
  private final IllegalStateException initializationFailure;

  public LocalDocumentStorage(
      @Value("${smarthire.document.storage.local.root-dir:/tmp/smarthire/documents}")
          String rootDir) {
    this.rootDir = Paths.get(rootDir).toAbsolutePath().normalize();
    IllegalStateException initializationFailure = null;
    try {
      Files.createDirectories(this.rootDir);
      log.info("Document storage initialized at: {}", this.rootDir);
    } catch (IOException e) {
      initializationFailure =
          new IllegalStateException("Failed to create storage root directory: " + this.rootDir, e);
      log.error("Failed to create storage root directory: {}", this.rootDir, e);
    }
    this.initializationFailure = initializationFailure;
  }

  @Override
  public String store(String storageKey, InputStream content) throws StorageException {
    Path targetPath = resolveAndValidate(storageKey);
    Path parentDir = targetPath.getParent();
    if (parentDir == null) {
      throw new StorageException("Cannot determine storage directory for: " + storageKey);
    }

    try {
      Files.createDirectories(parentDir);
      try (OutputStream out = Files.newOutputStream(targetPath)) {
        content.transferTo(out);
      }
      log.debug("Stored document at: {}", targetPath);
      return storageKey;
    } catch (IOException e) {
      log.error("Failed to store document with key: {}", storageKey, e);
      throw new StorageException("Failed to store document: " + storageKey, e);
    }
  }

  @Override
  public InputStream retrieve(String storageKey) throws StorageException {
    Path targetPath = resolveAndValidate(storageKey);

    if (!Files.exists(targetPath)) {
      throw new StorageException("Document not found: " + storageKey);
    }

    try {
      return Files.newInputStream(targetPath);
    } catch (IOException e) {
      log.error("Failed to retrieve document with key: {}", storageKey, e);
      throw new StorageException("Failed to retrieve document: " + storageKey, e);
    }
  }

  @Override
  public void delete(String storageKey) throws StorageException {
    Path targetPath = resolveAndValidate(storageKey);

    try {
      boolean deleted = Files.deleteIfExists(targetPath);
      if (!deleted) {
        log.warn("Attempted to delete non-existent document: {}", storageKey);
      } else {
        log.debug("Deleted document: {}", storageKey);
      }
    } catch (IOException e) {
      log.error("Failed to delete document with key: {}", storageKey, e);
      throw new StorageException("Failed to delete document: " + storageKey, e);
    }
  }

  @Override
  public boolean exists(String storageKey) {
    try {
      Path targetPath = resolveAndValidate(storageKey);
      return Files.exists(targetPath);
    } catch (StorageException e) {
      return false;
    }
  }

  /**
   * Resolves a storage key to an absolute path and validates it doesn't escape the root directory.
   *
   * @param storageKey the logical storage key
   * @return the resolved absolute path
   * @throws StorageException if path traversal is detected
   */
  private Path resolveAndValidate(String storageKey) throws StorageException {
    if (initializationFailure != null) {
      throw new StorageException("Document storage is unavailable", initializationFailure);
    }

    if (storageKey == null || storageKey.isBlank()) {
      throw new StorageException("Storage key cannot be null or empty");
    }

    // Normalize the storage key to prevent path traversal
    Path requestedPath = Paths.get(storageKey).normalize();

    // Prevent absolute paths or paths that try to escape
    if (requestedPath.isAbsolute() || requestedPath.startsWith("..")) {
      throw new StorageException("Invalid storage key (path traversal attempt): " + storageKey);
    }

    Path resolvedPath = rootDir.resolve(requestedPath).normalize();

    // Ensure the resolved path is still within the root directory
    if (!resolvedPath.startsWith(rootDir)) {
      throw new StorageException("Invalid storage key (path traversal attempt): " + storageKey);
    }

    return resolvedPath;
  }
}
