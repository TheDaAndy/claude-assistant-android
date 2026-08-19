package dev.claude.assistant.ankai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Minimale Ankai-Attrappe fuer die Client-Tests. */
public class FakeAnkai {

    public final Map<String, String[]> routes = new ConcurrentHashMap<>();
    public final List<String> requestLog = new ArrayList<>();
    public final List<String> authHeaders = new ArrayList<>();
    public final List<String> cookieHeaders = new ArrayList<>();
    public volatile String lastBody = "";
    public volatile boolean requireAuth = true;

    private final HttpServer server;

    public FakeAnkai() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new Handler());
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
    }

    /** Antwort fuer einen Pfad festlegen: Status, Content-Type, Body. */
    public void respond(String path, int status, String contentType, String body) {
        routes.put(path, new String[]{String.valueOf(status), contentType, body});
    }

    private class Handler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            requestLog.add(exchange.getRequestMethod() + " " + path);
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            authHeaders.add(auth == null ? "" : auth);
            String cookie = exchange.getRequestHeaders().getFirst("Cookie");
            cookieHeaders.add(cookie == null ? "" : cookie);
            lastBody = read(exchange.getRequestBody());

            if (requireAuth && (auth == null || !auth.startsWith("Basic "))
                    && (cookie == null || cookie.isEmpty())) {
                byte[] out = "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(401, out.length);
                exchange.getResponseBody().write(out);
                exchange.close();
                return;
            }

            String[] route = routes.get(path);
            if (route == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] out = route[2].getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", route[1]);
            exchange.getResponseHeaders().add("Set-Cookie", "ankai_session=abc123; Path=/; HttpOnly");
            exchange.sendResponseHeaders(Integer.parseInt(route[0]), out.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(out);
            }
            exchange.close();
        }

        private String read(InputStream in) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int n;
            while ((n = in.read(chunk)) > 0) buffer.write(chunk, 0, n);
            return new String(buffer.toByteArray(), StandardCharsets.ISO_8859_1);
        }
    }
}
