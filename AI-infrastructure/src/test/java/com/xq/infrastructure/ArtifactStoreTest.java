package com.xq.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import java.io.RandomAccessFile;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactStoreTest {
  @TempDir Path directory;
  final String key = "requests/00000000-0000-0000-0000-000000000001/input.csv";

  @Test
  void rejectsTraversalAndAbsolutePaths() throws Exception {
    var store = new ArtifactStore(directory.toString());
    for (String invalid :
        new String[] {
          "../outside", "/etc/passwd", "C:/outside", key + "/../../outside", "requests/x/input.csv"
        }) assertThrows(IllegalArgumentException.class, () -> store.path(invalid));
  }

  @Test
  void frozenInputsAreImmutableAndTamperingFailsHashCheck() throws Exception {
    var store = new ArtifactStore(directory.toString());
    byte[] original = "timestamp,elec\n".getBytes();
    String hash = store.write(key, original);
    assertArrayEquals(original, store.verified(key, hash));
    assertEquals(hash, store.write(key, original));
    assertThrows(IllegalArgumentException.class, () -> store.write(key, new byte[] {1}));
    Files.write(store.path(key), new byte[] {1});
    assertThrows(IllegalArgumentException.class, () -> store.verified(key, hash));
  }

  @Test
  void oversizedArtifactsAreRejectedBeforeReading() throws Exception {
    var store = new ArtifactStore(directory.toString());
    Path file = store.path(key);
    Files.createDirectories(file.getParent());
    try (var output = new RandomAccessFile(file.toFile(), "rw")) {
      output.setLength(ArtifactStore.MAX_BYTES + 1);
    }
    assertThrows(IllegalArgumentException.class, () -> store.read(key));
  }
}
