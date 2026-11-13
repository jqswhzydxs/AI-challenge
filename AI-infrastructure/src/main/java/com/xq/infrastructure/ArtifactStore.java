package com.xq.infrastructure;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ArtifactStore {
  private final Path root;
  public static final long MAX_BYTES = 20 * 1024 * 1024;

  public ArtifactStore(@Value("${algorithm.artifacts:target/artifacts}") String directory)
      throws IOException {
    root = Path.of(directory).toAbsolutePath().normalize();
    Files.createDirectories(root);
  }

  public Path path(String key) throws IOException {
    if (key == null
        || !key.matches("requests/[a-f0-9-]{36}/[a-zA-Z0-9/_.-]+")
        || key.contains("..")) throw new IllegalArgumentException("INVALID_ARTIFACT_KEY");
    Path result = root.resolve(key).normalize();
    if (!result.startsWith(root)) throw new IllegalArgumentException("INVALID_ARTIFACT_KEY");
    for (Path current = result;
        current != null && current.startsWith(root);
        current = current.getParent())
      if (Files.isSymbolicLink(current))
        throw new IllegalArgumentException("SYMLINK_ARTIFACT_REJECTED");
    return result;
  }

  public String write(String key, byte[] bytes) throws IOException {
    if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("ARTIFACT_TOO_LARGE");
    Path target = path(key);
    Files.createDirectories(target.getParent());
    if (Files.exists(target)) {
      if (!hash(read(key)).equals(hash(bytes)))
        throw new IllegalArgumentException("IMMUTABLE_ARTIFACT_CONFLICT");
      return hash(bytes);
    }
    Path temporary = Files.createTempFile(target.getParent(), "artifact-", ".tmp");
    try {
      Files.write(temporary, bytes);
      try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
        channel.force(true);
      }
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(temporary);
    }
    return hash(bytes);
  }

  public byte[] read(String key) throws IOException {
    Path file = path(key);
    if (Files.size(file) > MAX_BYTES) throw new IllegalArgumentException("ARTIFACT_TOO_LARGE");
    try (var input = Files.newInputStream(file)) {
      byte[] bytes = input.readNBytes((int) MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("ARTIFACT_TOO_LARGE");
      return bytes;
    }
  }

  public byte[] verified(String key, String expected) throws IOException {
    byte[] bytes = read(key);
    if (!hash(bytes).equals(expected)) throw new IllegalArgumentException("ARTIFACT_HASH_MISMATCH");
    return bytes;
  }

  public static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}
