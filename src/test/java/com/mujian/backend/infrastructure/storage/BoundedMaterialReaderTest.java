package com.mujian.backend.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

/** 协议边界测试仅控制传输回调，不访问业务库或外部COS。 */
class BoundedMaterialReaderTest {
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC);
  private final PrivateMaterialStore privateMaterialStore = mock(PrivateMaterialStore.class);
  private final HttpClient httpClient = mock(HttpClient.class);
  private final Flow.Subscription subscription = mock(Flow.Subscription.class);

  /** 使用真实响应订阅器处理分块数据，而不是绕过字节上限返回模拟body。 */
  private BoundedMaterialReader reader(int status, byte[]... chunks) {
    when(privateMaterialStore.sign("trusted/key", clock.instant().plusSeconds(60)))
        .thenReturn("https://storage.invalid/image");
    when(httpClient.sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
        .thenAnswer(
            invocation -> {
              HttpRequest request = invocation.getArgument(0);
              assertEquals("GET", request.method());
              assertEquals(Duration.ofSeconds(3), request.timeout().orElseThrow());
              HttpResponse.BodyHandler<byte[]> handler = invocation.getArgument(1);
              HttpResponse.BodySubscriber<byte[]> subscriber =
                  handler.apply(mock(HttpResponse.ResponseInfo.class));
              subscriber.onSubscribe(subscription);
              for (byte[] chunk : chunks) subscriber.onNext(List.of(ByteBuffer.wrap(chunk)));
              subscriber.onComplete();
              return subscriber
                  .getBody()
                  .thenApply(
                      bytes -> {
                        HttpResponse<byte[]> response = mock(HttpResponse.class);
                        when(response.statusCode()).thenReturn(status);
                        when(response.body()).thenReturn(bytes);
                        return response;
                      })
                  .toCompletableFuture();
            });
    return new BoundedMaterialReader(privateMaterialStore, clock, httpClient);
  }

  /** 合法分块完整返回，并复用已有签名到期时间。 */
  @Test
  void signedReadSuccess() throws Exception {
    assertArrayEquals(
        new byte[] {1, 2, 3},
        reader(200, new byte[] {1}, new byte[] {2, 3}).readImage("trusted/key"));
    verify(privateMaterialStore).sign("trusted/key", clock.instant().plusSeconds(60));
    verify(subscription, never()).cancel();
  }

  /** 正好5MiB允许，跨分块多一个字节即取消订阅并失败。 */
  @Test
  void exactLimitAndOverflowAcrossChunks() throws Exception {
    byte[] limit = new byte[5 * 1024 * 1024];
    assertEquals(limit.length, reader(200, limit).readImage("trusted/key").length);
    assertThrows(
        IOException.class, () -> reader(200, limit, new byte[] {1}).readImage("trusted/key"));
    verify(subscription).cancel();
  }

  /** 控制超时结果，无需实际等待3秒；必须取消仍未完成的传输。 */
  @Test
  void timeoutCancelsTransport() throws Exception {
    BoundedMaterialReader boundedMaterialReader = reader(200);
    CompletableFuture<HttpResponse<byte[]>> future = mock(CompletableFuture.class);
    when(future.get(3, TimeUnit.SECONDS)).thenThrow(new TimeoutException());
    when(httpClient.sendAsync(
            any(HttpRequest.class),
            org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
        .thenReturn(future);
    assertThrows(IOException.class, () -> boundedMaterialReader.readImage("trusted/key"));
    verify(future).cancel(true);
  }

  /** 非HTTPS签名不得发起网络请求，重定向响应也不得作为图片接受。 */
  @Test
  void rejectsNonHttpsAndNonSuccessStatus() throws Exception {
    BoundedMaterialReader boundedMaterialReader = reader(302, new byte[] {1});
    assertThrows(IOException.class, () -> boundedMaterialReader.readImage("trusted/key"));
    clearInvocations(httpClient);
    when(privateMaterialStore.sign("trusted/key", clock.instant().plusSeconds(60)))
        .thenReturn("http://storage.invalid/image");
    assertThrows(IOException.class, () -> boundedMaterialReader.readImage("trusted/key"));
    verifyNoInteractions(httpClient);
  }
}
