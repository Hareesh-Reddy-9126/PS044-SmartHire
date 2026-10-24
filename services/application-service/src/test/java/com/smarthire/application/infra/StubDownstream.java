package com.smarthire.application.infra;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Minimal scripted HTTP downstream for exercising the real Feign clients (and the Resilience4j
 * aspects around them) without Docker or live services. Uses the JDK's built-in HTTP server, so it
 * adds no test dependency.
 */
final class StubDownstream implements AutoCloseable {

  record RecordedRequest(String method, String path, String body) {}

  private record ScriptedResponse(int status, String body) {}

  private final HttpServer server;
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final Queue<ScriptedResponse> script = new ConcurrentLinkedQueue<>();
  private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
  private volatile ScriptedResponse defaultResponse = new ScriptedResponse(500, "");
  private volatile CountDownLatch gate;

  private StubDownstream(HttpServer server) {
    this.server = server;
  }

  static StubDownstream start() {
    try {
      HttpServer server =
          HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      StubDownstream stub = new StubDownstream(server);
      server.setExecutor(stub.executor);
      server.createContext("/", stub::handle);
      server.start();
      return stub;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  /** Queues a one-off response; queued responses are served before the default response. */
  void enqueue(int status, String body) {
    script.add(new ScriptedResponse(status, body));
  }

  /** Serves this response for every request once the queue is empty. */
  void respondAlways(int status, String body) {
    defaultResponse = new ScriptedResponse(status, body);
  }

  /** Holds every response until the latch is released (to keep a call in flight). */
  void holdResponsesUntil(CountDownLatch release) {
    gate = release;
  }

  List<RecordedRequest> requests() {
    return List.copyOf(requests);
  }

  void reset() {
    script.clear();
    requests.clear();
    defaultResponse = new ScriptedResponse(500, "");
    gate = null;
  }

  private void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      requests.add(
          new RecordedRequest(
              exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body));
      CountDownLatch release = gate;
      if (release != null) {
        release.await(10, TimeUnit.SECONDS);
      }
      ScriptedResponse response = script.poll();
      if (response == null) {
        response = defaultResponse;
      }
      byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
      if (bytes.length > 0) {
        try (OutputStream out = exchange.getResponseBody()) {
          out.write(bytes);
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
  }
}
