package ru.amazy.crm.nativeclient;

import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class ApiClient {
    static final class ApiException extends IOException {
        final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
    }

    private final String base;
    private final String accessToken;
    private final int account;

    ApiClient(String baseUrl, String accessToken, int accountId) {
        this.base = normalize(baseUrl);
        if (accessToken == null || accessToken.trim().isEmpty()) throw new IllegalArgumentException("Не указан токен");
        if (accountId < 1) throw new IllegalArgumentException("Неверный ID аккаунта");
        this.accessToken = accessToken.trim();
        this.account = accountId;
    }

    static String normalize(String url) {
        try {
            URI u = new URI(url.trim());
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null
                || u.getUserInfo() != null || u.getRawQuery() != null || u.getRawFragment() != null
                || !("".equals(u.getRawPath()) || "/".equals(u.getRawPath()))) {
                throw new IllegalArgumentException("Нужен адрес HTTPS без пути, например https://crm.example.ru");
            }
            if (u.getPort() < -1 || u.getPort() > 65535 || u.getPort() == 0)
                throw new IllegalArgumentException("Неверный порт");
            String host = u.getHost().toLowerCase(Locale.ROOT);
            // IDN and IPv6 are handled by java.net.URI.
            return new URI("https", null, host, u.getPort(), null, null, null).toString();
        } catch (Exception ex) {
            if (ex instanceof IllegalArgumentException) throw (IllegalArgumentException) ex;
            throw new IllegalArgumentException("Некорректный HTTPS-адрес сервера");
        }
    }

    JSONObject profile() throws IOException { return request("GET", "/api/v1/profile", null); }

    JSONObject conversations(String status, int page, String search) throws IOException {
        String path = accountPath() + "/conversations?status=" + encode(status) + "&page=" + page;
        if (search != null && !search.isBlank()) path += "&q=" + encode(search);
        return request("GET", path, null);
    }

    JSONObject messages(int conversationId) throws IOException {
        return request("GET", accountPath() + "/conversations/" + conversationId + "/messages", null);
    }

    JSONObject sendMessage(int conversationId, String text, boolean privateNote) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("content", text);
            body.put("message_type", "outgoing");
            body.put("private", privateNote);
        } catch (Exception ex) { throw new IOException("Не удалось сформировать сообщение", ex); }
        return request("POST", accountPath() + "/conversations/" + conversationId + "/messages", body);
    }

    JSONObject setStatus(int conversationId, String status) throws IOException {
        JSONObject body = new JSONObject();
        try { body.put("status", status); } catch (Exception ex) { throw new IOException(ex); }
        return request("POST", accountPath() + "/conversations/" + conversationId + "/toggle_status", body);
    }

    JSONObject markSeen(int conversationId) throws IOException {
        return request("POST", accountPath() + "/conversations/" + conversationId + "/update_last_seen", new JSONObject());
    }

    private String accountPath() { return "/api/v1/accounts/" + account; }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private JSONObject request(String method, String path, JSONObject body) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(base + path).openConnection();
            conn.setConnectTimeout(16000);
            conn.setReadTimeout(26000);
            conn.setInstanceFollowRedirects(false); // Do not leak API token to redirect targets.
            conn.setRequestMethod(method);
            conn.setRequestProperty("api_access_token", accessToken);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "VolshebnoTutNativeCRM/0.2");
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }
            }
            int code = conn.getResponseCode();
            String raw = read(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code == 401 || code == 403)
                throw new ApiException(code, "Нет доступа (HTTP " + code + "). Проверь токен и аккаунт Chatwoot.");
            if (code < 200 || code >= 300)
                throw new ApiException(code, "Сервер вернул HTTP " + code + (code >= 500 ? ". Попробуй позднее." : ". Проверь запрос."));
            if (raw.isBlank()) return new JSONObject();
            try { return new JSONObject(raw); }
            catch (Exception ex) { throw new IOException("Сервер вернул неожиданный формат ответа", ex); }
        } catch (java.net.SocketTimeoutException ex) {
            throw new IOException("Превышено время ожидания сервера. Проверь Happ или подключение к Chatwoot.", ex);
        } catch (java.net.UnknownHostException ex) {
            throw new IOException("Не удалось найти сервер по адресу. Проверь DNS, VPN и адрес.", ex);
        } catch (javax.net.ssl.SSLException ex) {
            throw new IOException("Ошибка безопасного HTTPS-соединения. Проверь сертификат сервера.", ex);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int n;
            while ((n = input.read(chunk)) != -1) {
                if (out.size() + n > 5_000_000) throw new IOException("Слишком большой ответ сервера");
                out.write(chunk, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8);
        }
    }
}
