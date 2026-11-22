package com.mujian.backend.scheme.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.PlanningReason;
import com.mujian.backend.common.enums.SchemeExportStatus;
import com.mujian.backend.scheme.domain.PdfFileMetadata;
import com.mujian.backend.scheme.domain.SchemeExportDO;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 私有临时文件、有限容量和导出级读租约，无Web静态映射。 */
@Service
public class PdfFiles {
  private final Path directory;
  private final Clock clock;
  private final Semaphore capacity;
  private final int concurrency;
  private long cleanupOffset;
  private final ReentrantLock[] locks = new ReentrantLock[256];
  private final long maxFileBytes, totalBytes;

  /** 启动时创建专用私有目录并验证可写，不使用COS或.local。 */
  public PdfFiles(
      Clock clock,
      @Value("${mj.planning.pdf.directory:${java.io.tmpdir}/mujian-scheme-pdf}") String directory,
      @Value("${mj.planning.pdf.concurrency:2}") int concurrency,
      @Value("${mj.planning.pdf.max-file-bytes:20971520}") long maxFileBytes,
      @Value("${mj.planning.pdf.total-bytes:1073741824}") long totalBytes)
      throws IOException {
    this.clock = clock;
    this.directory = Path.of(directory).toAbsolutePath().normalize();
    capacity = new Semaphore(concurrency);
    this.concurrency = concurrency;
    this.maxFileBytes = maxFileBytes;
    this.totalBytes = totalBytes;
    if (concurrency < 1 || maxFileBytes < 1 || totalBytes < maxFileBytes)
      throw new IllegalArgumentException("invalid PDF capacity");
    if (Files.isSymbolicLink(this.directory))
      throw new IOException("PDF directory cannot be symlink");
    Files.createDirectories(this.directory);
    Files.setPosixFilePermissions(this.directory, PosixFilePermissions.fromString("rwx------"));
    if (!Files.isWritable(this.directory)) throw new IOException("PDF directory not writable");
    for (int index = 0; index < locks.length; index++) locks[index] = new ReentrantLock();
  }

  /** 固定条带锁限制内存大小，生成发布和清理共用。 */
  public ReentrantLock lock(String id) {
    return locks[(id.hashCode() & Integer.MAX_VALUE) % locks.length];
  }

  /** 文件名只接受服务端UUID。 */
  public Path path(String id, boolean temporary) {
    return directory.resolve(id(id) + (temporary ? ".tmp" : ".pdf"));
  }

  /** 缓存判定不续期，不生成。 */
  public boolean available(SchemeExportDO schemeExportDO) {
    return schemeExportDO != null
        && SchemeExportStatus.success.name().equals(schemeExportDO.exportStatus())
        && schemeExportDO.fileExpireAt() != null
        && now(clock).isBefore(schemeExportDO.fileExpireAt())
        && Files.isRegularFile(path(schemeExportDO.id(), false), LinkOption.NOFOLLOW_LINKS);
  }

  /** 预留前以非阻塞方式检查并发和磁盘预算。 */
  public synchronized boolean acquire() {
    if (!capacity.tryAcquire()) return false;
    try (java.util.stream.Stream<Path> streamFiles = Files.list(directory)) {
      long used = 0;
      for (java.util.Iterator<Path> iterator = streamFiles.iterator(); iterator.hasNext(); ) {
        Path path = iterator.next();
        {
          if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            used = Math.addExact(used, Files.size(path));
        }
      }
      if (used + maxFileBytes * (concurrency - capacity.availablePermits()) > totalBytes
          || Files.getFileStore(directory).getUsableSpace() < maxFileBytes) {
        capacity.release();
        return false;
      }
      return true;
    } catch (IOException | ArithmeticException exception) {
      capacity.release();
      return false;
    }
  }

  /** 每次接纳生成终结后归还容量。 */
  public void release() {
    capacity.release();
  }

  /** 限制真实写出字节，超过时使此次已接纳生成失败。 */
  public OutputStream output(String id) throws IOException {
    OutputStream outputStreamOutput =
        Files.newOutputStream(
            path(id, true), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    return new FilterOutputStream(outputStreamOutput) {
      private long size;

      /** 单字节也计入限制。 */
      public void write(int value) throws IOException {
        if (++size > maxFileBytes) throw new IOException("PDF size limit");
        out.write(value);
      }

      /** 批量写入前检查限制。 */
      public void write(byte[] data, int offset, int length) throws IOException {
        size += length;
        if (size > maxFileBytes) throw new IOException("PDF size limit");
        out.write(data, offset, length);
      }
    };
  }

  /** 完整文件原子发布，不能覆盖其他导出。 */
  public void publish(String id) throws IOException {
    Files.move(path(id, true), path(id, false), StandardCopyOption.ATOMIC_MOVE);
  }

  /** 调用方持有导出锁，正在下载的文件不会被删除。 */
  public void delete(String id) throws IOException {
    Files.deleteIfExists(path(id, true));
    Files.deleteIfExists(path(id, false));
  }

  /** 以有界枚举供分钟清理，不递归访问其他目录。 */
  public synchronized java.util.List<Path> candidates() throws IOException {
    try (java.util.stream.Stream<Path> stream = Files.list(directory)) {
      java.util.List<Path> pathListBatch = stream.skip(cleanupOffset).limit(1000).toList();
      cleanupOffset = pathListBatch.size() < 1000 ? 0 : cleanupOffset + pathListBatch.size();
      return pathListBatch;
    }
  }

  /** 为下载打开读租约，完成传输前保持互斥。 */
  public Lease open(SchemeExportDO schemeExportDO) throws IOException {
    if (!SchemeExportStatus.success.name().equals(schemeExportDO.exportStatus()))
      throw conflict(PlanningReason.PDF_CACHE_EXPIRED.name());
    return open(new PdfFileMetadata(schemeExportDO.id(), schemeExportDO.fileExpireAt()));
  }

  /** 已鉴权的缓存定位直接打开私有成品，与原下载和清理共用文件读锁。 */
  public Lease open(PdfFileMetadata file) throws IOException {
    ReentrantLock fileLock = lock(file.id());
    fileLock.lock();
    try {
      Path path = path(file.id(), false);
      if (file.fileExpireAt() == null
          || !now(clock).isBefore(file.fileExpireAt())
          || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        throw conflict(PlanningReason.PDF_CACHE_EXPIRED.name());
      long length = Files.size(path);
      InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS);
      return new Lease(input, length, fileLock);
    } catch (Exception exception) {
      fileLock.unlock();
      throw exception;
    }
  }

  public record Lease(InputStream input, long length, ReentrantLock lock) implements AutoCloseable {
    /** 传输结束释放句柄和清理互斥。 */
    public void close() throws IOException {
      try {
        input.close();
      } finally {
        lock.unlock();
      }
    }
  }
}
