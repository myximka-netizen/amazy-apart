package ru.amazy.crm.nativeclient;

import android.content.Context;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
        return conversations(status, page, search, "all");
    }

    JSONObject conversations(String status, int page, String search, String assigneeType) throws IOException {
        return conversations(status, page, search, assigneeType, 0);
    }

    JSONObject conversations(String status, int page, String search, String assigneeType, int inboxId) throws IOException {
        String path = accountPath() + "/conversations?status=" + encode(status) + "&page=" + page
            + "&assignee_type=" + encode(assigneeType);
        if (inboxId > 0) path += "&inbox_id=" + inboxId;
        if (search != null && !search.isBlank()) path += "&q=" + encode(search);
        return request("GET", path, null);
    }

    JSONObject conversation(int conversationId) throws IOException {
        return request("GET", accountPath() + "/conversations/" + conversationId, null);
    }

    JSONArray cannedResponses() throws IOException {
        return request("GET", accountPath() + "/canned_responses", null).optJSONArray("_array");
    }

    JSONArray agents() throws IOException {
        return request("GET", accountPath() + "/agents", null).optJSONArray("_array");
    }

    JSONObject assign(int conversationId, int agentId) throws IOException {
        JSONObject body = new JSONObject();
        try { body.put("assignee_id", agentId > 0 ? agentId : JSONObject.NULL); }
        catch (Exception ex) { throw new IOException(ex); }
        return request("POST", accountPath() + "/conversations/" + conversationId + "/assignments", body);
    }

    JSONObject setPriority(int conversationId, String priority) throws IOException {
        JSONObject body = new JSONObject();
        try { body.put("priority", priority); }
        catch (Exception ex) { throw new IOException(ex); }
        return request("POST", accountPath() + "/conversations/" + conversationId + "/toggle_priority", body);
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

    // Chatwoot's dashboard sends attachments as multipart form data, not base64 JSON.
    // Stage first to know exact size: many reverse proxies reject chunked uploads.
    JSONObject sendAttachments(Context context, int conversationId, String caption,
                               boolean note, List<AttachmentDraft> attachments) throws IOException {
        if (attachments.isEmpty() || attachments.size() > AttachmentDraft.MAX_COUNT)
            throw new IOException("Выбери от 1 до 10 файлов");
        ArrayList<File> staged = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            long total = 0;
            byte[] buffer = new byte[65536];
            for (AttachmentDraft item : attachments) {
                File file = File.createTempFile("crm-upload-", ".tmp", context.getCacheDir());
                staged.add(file);
                long count = 0;
                try (InputStream in = item.open(context); FileOutputStream out = new FileOutputStream(file)) {
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (Thread.currentThread().isInterrupted()) throw new IOException("Отправка отменена");
                        if (n == 0) continue;
                        count += n;
                        total += n;
                        if (count > AttachmentDraft.MAX_FILE || total > AttachmentDraft.MAX_TOTAL)
                            throw new IOException("Не более 40 МБ на файл и 80 МБ на сообщение");
                        out.write(buffer, 0, n);
                    }
                }
                if (count == 0) throw new IOException("Пустой файл: " + item.name);
            }

            String boundary = "----AmazyCRM" + UUID.randomUUID().toString().replace("-", "");
            ByteArrayOutputStream fields = new ByteArrayOutputStream();
            multipartField(fields, boundary, "content", caption);
            multipartField(fields, boundary, "message_type", "outgoing");
            multipartField(fields, boundary, "private", Boolean.toString(note));
            if (attachments.size() == 1 && attachments.get(0).voice)
                multipartField(fields, boundary, "is_voice_message", "true");
            ArrayList<byte[]> headers = new ArrayList<>();
            long length = fields.size();
            byte[] newline = "\r\n".getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i < attachments.size(); i++) {
                AttachmentDraft item = attachments.get(i);
                byte[] header = ("--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"attachments[]\"; filename=\"" + item.name + "\"\r\n" +
                    "Content-Type: " + item.mime + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
                headers.add(header);
                length += header.length + staged.get(i).length() + newline.length;
            }
            byte[] end = ("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            length += end.length;
            conn = (HttpURLConnection) new URL(base + accountPath() + "/conversations/" +
                conversationId + "/messages").openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(180000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("api_access_token", accessToken);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(length);
            try (OutputStream out = conn.getOutputStream()) {
                fields.writeTo(out);
                for (int i = 0; i < staged.size(); i++) {
                    out.write(headers.get(i));
                    try (FileInputStream in = new FileInputStream(staged.get(i))) {
                        int n;
                        while ((n = in.read(buffer)) != -1) {
                            if (Thread.currentThread().isInterrupted()) throw new IOException("Отправка отменена");
                            out.write(buffer, 0, n);
                        }
                    }
                    out.write(newline);
                }
                out.write(end);
            }
            return parseResponse(conn);
        } finally {
            if (conn != null) conn.disconnect();
            for (File file : staged) file.delete();
        }
    }

    private static void multipartField(OutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name +
            "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static JSONObject parseResponse(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        String raw = read(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
        if (code < 200 || code >= 300) {
            String detail = "";
            try {
                JSONObject error = new JSONObject(raw);
                detail = error.optString("message", error.optString("error", ""));
                if (detail.length() > 300) detail = detail.substring(0, 300);
            } catch (Exception ignored) { }
            if (code == 413) detail = "Файл превышает лимит сервера.";
            throw new ApiException(code, "Сервер вернул HTTP " + code + (detail.isBlank() ? "" : ": " + detail));
        }
        if (raw.isBlank() || "null".equals(raw.trim())) return new JSONObject();
        try {
            if (raw.trim().startsWith("[")) {
                JSONObject wrapped = new JSONObject();
                wrapped.put("_array", new JSONArray(raw));
                return wrapped;
            }
            return new JSONObject(raw);
        } catch (Exception ex) {
            throw new IOException("Сервер вернул неожиданный формат ответа", ex);
        }
    }

    JSONArray inboxes() throws IOException {
        JSONObject result = request("GET", accountPath() + "/inboxes", null);
        JSONArray items = result.optJSONArray("payload");
        return items == null ? result.optJSONArray("_array") : items;
    }

    JSONObject setStatus(int conversationId, String status) throws IOException {
        JSONObject body = new JSONObject();
        try { body.put("status", status); } catch (Exception ex) { throw new IOException(ex); }
        return request("POST", accountPath() + "/conversations/" + conversationId + "/toggle_status", body);
    }

    JSONObject markSeen(int conversationId) throws IOException {
        return request("POST", accountPath() + "/conversations/" + conversationId + "/update_last_seen", new JSONObject());
    }

    /** Chatwoot already provides FCM push subscriptions for the signed-in user. */
    JSONObject registerPushToken(String pushToken, String deviceId) throws IOException {
        JSONObject attributes = new JSONObject();
        JSONObject data = new JSONObject();
        JSONObject body = new JSONObject();
        try {
            attributes.put("push_token", pushToken);
            attributes.put("device_id", deviceId);
            data.put("subscription_type", "fcm");
            data.put("subscription_attributes", attributes);
            body.put("notification_subscription", data);
        } catch (Exception ex) { throw new IOException("Не удалось подготовить push-подписку", ex); }
        return request("POST", "/api/v1/notification_subscriptions", body);
    }

    JSONObject unregisterPushToken(String pushToken) throws IOException {
        JSONObject body = new JSONObject();
        try { body.put("push_token", pushToken); }
        catch (Exception ex) { throw new IOException(ex); }
        return request("DELETE", "/api/v1/notification_subscriptions", body);
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
            conn.setRequestProperty("User-Agent", "VolshebnoTutNativeCRM/0.6");
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
            if (raw.isBlank() || "null".equals(raw.trim())) return new JSONObject();
            try {
                if (raw.trim().startsWith("[")) {
                    JSONObject wrapped = new JSONObject();
                    wrapped.put("_array", new JSONArray(raw));
                    return wrapped;
                }
                return new JSONObject(raw);
            } catch (Exception ex) {
                throw new IOException("Сервер вернул неожиданный формат ответа", ex);
            }
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
