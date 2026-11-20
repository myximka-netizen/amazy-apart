package ru.amazy.crm.nativeclient;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Draft metadata; content is staged to a private cache only when sending. */
final class AttachmentDraft {
    static final long MAX_FILE = 40L * 1024 * 1024;
    static final long MAX_TOTAL = 80L * 1024 * 1024;
    static final int MAX_COUNT = 10;
    final Uri uri;
    final File recording;
    final String name;
    final String mime;
    final long size;
    final boolean voice;

    private AttachmentDraft(Uri uri, File recording, String name, String mime, long size, boolean voice) {
        this.uri = uri;
        this.recording = recording;
        this.name = safeName(name);
        this.mime = mime == null || !mime.matches("[A-Za-z0-9.+_-]+/[A-Za-z0-9.+_-]+")
            ? "application/octet-stream" : mime;
        this.size = size;
        this.voice = voice;
    }

    static AttachmentDraft document(Context context, Uri uri) throws IOException {
        if (uri == null || !"content".equals(uri.getScheme()))
            throw new IOException("Выбери файл из галереи или файлов Android");
        ContentResolver resolver = context.getContentResolver();
        String name = "attachment";
        long length = -1;
        try (Cursor c = resolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                if (!c.isNull(0)) name = c.getString(0);
                if (!c.isNull(1)) length = c.getLong(1);
            }
        } catch (RuntimeException ignored) { }
        String mime = resolver.getType(uri);
        if (mime == null || mime.equals("application/octet-stream")) {
            int dot = name.lastIndexOf('.');
            String guessed = dot < 0 ? null : MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                name.substring(dot + 1).toLowerCase(Locale.ROOT));
            if (guessed != null) mime = guessed;
        }
        if (length > MAX_FILE) throw new IOException("Файл больше 40 МБ: " + name);
        if (length == 0) throw new IOException("Файл пуст: " + name);
        return new AttachmentDraft(uri, null, name, mime, length, false);
    }

    static AttachmentDraft voice(File file) {
        return new AttachmentDraft(null, file, "voice-" + System.currentTimeMillis() + ".m4a",
            "audio/mp4", file.length(), true);
    }

    InputStream open(Context context) throws IOException {
        InputStream in = recording != null ? new FileInputStream(recording)
            : context.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Файл недоступен: " + name);
        return in;
    }

    String displaySize() {
        if (size < 0) return "";
        if (size < 1024) return size + " Б";
        if (size < 1024 * 1024) return String.format(Locale.US, "%.0f КБ", size / 1024.0);
        return String.format(Locale.US, "%.1f МБ", size / 1048576.0);
    }

    String kind() {
        if (voice) return "Голосовое сообщение";
        if (mime.startsWith("image/")) return "Фото";
        if (mime.startsWith("video/")) return "Видео";
        if (mime.startsWith("audio/")) return "Аудио";
        return "Файл";
    }

    void discard() {
        if (recording != null) recording.delete();
    }

    private static String safeName(String name) {
        if (name == null) return "attachment";
        String result = name.replaceAll("[\\\\/\\p{Cntrl}\"]", "_").trim();
        if (result.length() > 180) result = result.substring(0, 160) + result.substring(result.length() - 20);
        return result.isEmpty() ? "attachment" : result;
    }
}
