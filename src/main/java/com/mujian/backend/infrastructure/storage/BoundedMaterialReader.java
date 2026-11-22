package com.mujian.backend.infrastructure.storage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 私有材料的有界图片读取：复用存储签名，不接受调用方提供的URL。 */
@Component
public class BoundedMaterialReader {
  private final PrivateMaterialStore privateMaterialStore;
  private final Clock clock;
  private final HttpClient httpClient;

  /** 固定连接超时且禁止重定向，保持私有存储的访问边界。 */
  @Autowired
  public BoundedMaterialReader(PrivateMaterialStore privateMaterialStore, Clock clock) {
    this(
        privateMaterialStore,
        clock,
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
  }

  /** 为包内协议测试注入传输；业务入口始终使用固定配置的客户端。 */
  BoundedMaterialReader(
      PrivateMaterialStore privateMaterialStore, Clock clock, HttpClient httpClient) {
    this.privateMaterialStore = privateMaterialStore;
    this.clock = clock;
    this.httpClient = httpClient;
  }

  /** 在事务外按可信存储键签名读取，累计最多5MiB，整次响应最多等待3秒。 */
  public byte[] readImage(String storageKey) throws IOException, InterruptedException {
    URI uri = URI.create(privateMaterialStore.sign(storageKey, clock.instant().plusSeconds(60)));
    if (!"https".equals(uri.getScheme())) throw new IOException("image requires HTTPS");
    CompletableFuture<HttpResponse<byte[]>> responseFuture =
        httpClient.sendAsync(
            HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(3)).GET().build(),
            responseInfo -> new LimitedImageSubscriber());
    HttpResponse<byte[]> response;
    try {
      response = responseFuture.get(3, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      responseFuture.cancel(true);
      throw exception;
    } catch (ExecutionException | TimeoutException exception) {
      responseFuture.cancel(true);
      throw new IOException("image read failed", exception);
    }
    if (response.statusCode() != 200) throw new IOException("image response rejected");
    return response.body();
  }

  /** 下载订阅器在聚合前限制字节，并允许整次请求超时取消。 */
  private static final class LimitedImageSubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> delegate =
        HttpResponse.BodySubscribers.ofByteArray();
    private java.util.concurrent.Flow.Subscription subscription;
    private long size;
    private boolean done;

    /** 返回受限聚合结果供生成线程读取。 */
    public java.util.concurrent.CompletionStage<byte[]> getBody() {
      return delegate.getBody();
    }

    /** 转发订阅并保留取消句柄。 */
    public void onSubscribe(java.util.concurrent.Flow.Subscription subscriptionValue) {
      subscription = subscriptionValue;
      delegate.onSubscribe(subscriptionValue);
    }

    /** 在交给聚合器之前检查累计大小。 */
    public void onNext(List<java.nio.ByteBuffer> byteBufferListBuffers) {
      if (done) return;
      for (java.nio.ByteBuffer byteBufferBuffer : byteBufferListBuffers)
        size += byteBufferBuffer.remaining();
      if (size > 5 * 1024 * 1024) {
        done = true;
        subscription.cancel();
        delegate.onError(new IOException("image byte limit"));
        return;
      }
      delegate.onNext(byteBufferListBuffers);
    }

    /** 一次性结束失败响应。 */
    public void onError(Throwable throwableError) {
      if (!done) {
        done = true;
        delegate.onError(throwableError);
      }
    }

    /** 完成有界图片字节聚合。 */
    public void onComplete() {
      if (!done) {
        done = true;
        delegate.onComplete();
      }
    }
  }
}
