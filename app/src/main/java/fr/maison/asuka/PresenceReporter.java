package fr.maison.asuka;

import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ArrayBlockingQueue;

final class PresenceReporter {
    interface TokenSource { String get() throws Exception; }
    private final Settings settings;
    private final TokenSource tokenSource;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(16), new ThreadPoolExecutor.DiscardOldestPolicy());
    private String previous = "";
    PresenceReporter(Settings s) { this(s, () -> s.secret("token")); }
    PresenceReporter(Settings s, TokenSource source) {
        settings=s; tokenSource=source;
        if (!s.p.contains("device")) s.p.edit().putString("device", "asuka-" + UUID.randomUUID()).apply();
    }
    void changed(String state, boolean teddy) {
        String identity = settings.server() + settings.room() + state;
        if (identity.equals(previous)) return; previous=identity;
        final String endpoint=settings.server()+"api/presence", room=settings.room(), instant=Instant.now().toString();
        worker.execute(() -> {
            HttpURLConnection connection=null;
            try {
                String token=tokenSource.get();
                if (token.isEmpty()) return; // Configure the house token first; never send anonymous presence.
                JSONObject body=new JSONObject().put("appareil", settings.p.getString("device", "asuka"))
                    .put("piece", room).put("teddy", teddy).put("heure", instant).put("etat", state);
                byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);
                connection=(HttpURLConnection)new URL(endpoint).openConnection();
                connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(3000); connection.setReadTimeout(3000);
                connection.setRequestMethod("POST"); connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Authorization", "Bearer " + token); connection.setDoOutput(true); connection.setFixedLengthStreamingMode(data.length);
                try (java.io.OutputStream out=connection.getOutputStream()) { out.write(data); }
                connection.getResponseCode(); // Including 404: best effort, no retries, no crash, no logs of secrets.
            } catch (Exception ignored) { } finally { if (connection != null) connection.disconnect(); }
        });
    }
    void close() { worker.shutdown(); }
}
