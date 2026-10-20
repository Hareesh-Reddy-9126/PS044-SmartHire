package com.smarthire.document.infra;

import java.io.InputStream;

/**
 * Storage abstraction for document files. Provider-agnostic interface that can be implemented for
 * local filesystem, S3, Azure Blob Storage, etc.
 */
public interface DocumentStorage {

  /**
   * Stores a document file.
   *
   * @param storageKey the logical key for the file (not the original filename)
   * @param content the file content
   * @return the storage key (may be normalized)
   * @throws StorageException if storage fails
   */
  String store(String storageKey, InputStream content) throws StorageException;

  /**
   * Retrieves a document file as an input stream.
   *
   * @param storageKey the logical key
   * @return input stream of the file content
   * @throws StorageException if file not found or retrieval fails
   */
  InputStream retrieve(String storageKey) throws StorageException;

  /**
   * Deletes a document file.
   *
   * @param storageKey the logical key
   * @throws StorageException if deletion fails
   */
  void delete(String storageKey) throws StorageException;

  /**
   * Checks if a file exists.
   *
   * @param storageKey the logical key
   * @return true if the file exists
   */
  boolean exists(String storageKey);

  /** Exception thrown when a storage operation fails. */
  class StorageException extends Exception {
    public StorageException(String message) {
      super(message);
    }

    public StorageException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
