/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.copycat.server.storage;

import io.atomix.catalyst.serializer.Serializer;
import io.atomix.catalyst.serializer.ServiceLoaderTypeResolver;
import io.atomix.copycat.server.storage.compaction.Compaction;
import org.testng.annotations.Test;

import java.util.concurrent.CountDownLatch;

import static org.testng.Assert.*;

/**
 * Major compaction test.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
@Test
public class MajorCompactionTest extends AbstractLogTest {

  protected Log createLog() {
    return tempStorageBuilder()
      .withMaxEntriesPerSegment(10)
      .withSerializer(new Serializer(new ServiceLoaderTypeResolver()))
      .build()
      .open("copycat");
  }

  /**
   * Tests compacting the log.
   */
  public void testMajorCompaction() throws Throwable {
    writeEntries(31);

    assertEquals(log.length(), 31L);

    for (long index = 21; index < 28; index++) {
      log.clean(index);
    }
    log.commit(31).compactor().minorIndex(31).majorIndex(31);

    CountDownLatch latch = new CountDownLatch(1);
    log.compactor().compact(Compaction.MAJOR).thenRun(latch::countDown);
    latch.await();

    assertEquals(log.length(), 31L);

    for (long index = 21; index < 28; index++) {
      assertTrue(log.lastIndex() >= index);
      assertFalse(log.contains(index));
      try (TestEntry entry = log.get(index)) {
        assertNull(entry);
      }
    }
  }

  /**
   * Writes a set of session entries to the log.
   */
  private void writeEntries(int entries) {
    for (int i = 0; i < entries; i++) {
      try (TestEntry entry = log.create(TestEntry.class)) {
        entry.setTerm(1);
        if (entry.getIndex() % 2 == 0) {
          entry.setTombstone(true);
        } else {
          entry.setTombstone(false);
        }
        log.append(entry);
      }
    }
  }

}
