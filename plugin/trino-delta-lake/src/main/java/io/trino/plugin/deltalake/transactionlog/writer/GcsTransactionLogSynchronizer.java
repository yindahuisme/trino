/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.deltalake.transactionlog.writer;

import com.google.inject.Inject;
import io.trino.filesystem.Location;
import io.trino.filesystem.TrinoFileSystem;
import io.trino.filesystem.TrinoFileSystemFactory;
import io.trino.spi.connector.ConnectorSession;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import static java.util.Objects.requireNonNull;

public class GcsTransactionLogSynchronizer
        implements TransactionLogSynchronizer
{
    private final TrinoFileSystemFactory fileSystemFactory;

    @Inject
    public GcsTransactionLogSynchronizer(TrinoFileSystemFactory fileSystemFactory)
    {
        this.fileSystemFactory = requireNonNull(fileSystemFactory, "fileSystemFactory is null");
    }

    // This approach is compatible with OSS Delta Lake
    // https://github.com/delta-io/delta/blob/225e2bbf9ecaf034d08ef8d2fee1929e51c951bf/storage/src/main/java/io/delta/storage/GCSLogStore.java
    @Override
    public void write(ConnectorSession session, String clusterId, Location newLogEntryPath, byte[] entryContents)
    {
        TrinoFileSystem fileSystem = fileSystemFactory.create(session);
        try (OutputStream outputStream = fileSystem.newOutputFile(newLogEntryPath).createExclusive()) {
            outputStream.write(entryContents);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean isUnsafe()
    {
        return false;
    }
}
