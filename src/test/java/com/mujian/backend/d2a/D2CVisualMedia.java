package com.mujian.backend.d2a;

import com.mujian.backend.common.support.Ids;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Loopback fixture media, opaque 120-second tickets. Test-only; no real COS credentials. */
final class D2CVisualMedia implements AutoCloseable {
  record Ticket(String key, Instant expires) {}

  final HttpServer server;
  final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();

  D2CVisualMedia(D2ApplicationTest.MemoryStore store) throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 18082), 0);
    server.createContext(
        "/media/",
        exchange -> {
          D2CVisualMedia.Ticket ticket = tickets.get(exchange.getRequestURI().getPath().substring(7));
          byte[] bytes = ticket == null ? null : store.payloads.get(ticket.key());
          if (ticket == null || !Instant.now().isBefore(ticket.expires()) || bytes == null) {
            exchange.sendResponseHeaders(404, -1);
          } else {
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
          }
          exchange.close();
        });
    server.start();
    store.visualSigner =
        (key, until) -> {
          String id = Ids.next();
          tickets.put(id, new Ticket(key, Instant.now().plusSeconds(120)));
          return "http://127.0.0.1:18082/media/" + id;
        };
  }

  /** 关闭夹具持有的资源。 */
  public void close() {
    server.stop(0);
  }
}
