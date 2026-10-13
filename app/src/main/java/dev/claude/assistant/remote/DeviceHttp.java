package dev.claude.assistant.remote;

import dev.claude.assistant.ankai.AnkaiConnection;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.json.JSONObject;

public final class DeviceHttp {
    private volatile HttpURLConnection active;
    public static final class Failure extends IOException {
        public final int status;
        Failure(int status) { super("Gerätekanal: HTTP " + status); this.status = status; }
    }
    public JSONObject account(AnkaiConnection c, String path, String method, JSONObject body) throws Exception {
        return request(c.baseUrl, path, method, "Basic " + Base64.getEncoder().encodeToString(
                (c.username + ":" + c.password).getBytes(StandardCharsets.UTF_8)), body);
    }
    public JSONObject channel(DeviceLinkStore store, String path, String method, JSONObject body) throws Exception {
        return request(store.baseUrl(), "/api/device-channel/" + store.id() + path, method, "Bearer " + store.token(), body);
    }
    public void cancel() { HttpURLConnection c = active; if (c != null) c.disconnect(); }
    private JSONObject request(String base, String path, String method, String authorization, JSONObject body) throws Exception {
        URI uri = new URI(base);
        boolean localDebug = dev.claude.assistant.BuildConfig.DEBUG && "http".equals(uri.getScheme())
                && ("10.0.2.2".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if ((!"https".equals(uri.getScheme()) && !localDebug) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IOException("Gerätekopplung erfordert eine HTTPS-Instanz.");
        }
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection(); active = c;
        c.setInstanceFollowRedirects(false); c.setConnectTimeout(15000); c.setReadTimeout("GET".equals(method) && path.endsWith("/commands") ? 65000 : 15000);
        c.setRequestMethod(method); c.setRequestProperty("Authorization", authorization); c.setRequestProperty("Accept", "application/json");
        try {
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json"); c.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
            }
            int status = c.getResponseCode(); if (status < 200 || status >= 300) throw new Failure(status);
            if (status == 204) return new JSONObject();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buffer = new byte[4096]; int count;
                while ((count = in.read(buffer)) != -1) { if (out.size() + count > (path.endsWith("/result")?262144:65536)) throw new IOException("Antwort zu groß"); out.write(buffer, 0, count); }
            }
            return new JSONObject(out.toString("UTF-8"));
        } finally { c.disconnect(); if (active == c) active = null; }
    }
}
