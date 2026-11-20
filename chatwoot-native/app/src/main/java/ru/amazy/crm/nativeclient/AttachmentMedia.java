package ru.amazy.crm.nativeclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded native previews. No API credentials are sent to attachment hosts. */
final class AttachmentMedia {
    private static final ExecutorService IMAGES = Executors.newFixedThreadPool(2);
    private static final android.util.LruCache<String, Bitmap> CACHE =
        new android.util.LruCache<String, Bitmap>(8 * 1024 * 1024) {
            @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
        };
    private AttachmentMedia() { }

    static boolean isHttps(String url) {
        try {
            Uri u = Uri.parse(url);
            return "https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null && u.getUserInfo() == null;
        } catch (RuntimeException ex) { return false; }
    }

    static void thumbnail(ImageView view, String url) {
        if (!isHttps(url)) return;
        view.setTag(url);
        Bitmap known = CACHE.get(url);
        if (known != null) { view.setImageBitmap(known); return; }
        IMAGES.execute(() -> {
            HttpURLConnection conn = null;
            try {
                URL current = new URL(url);
                for (int attempt = 0; attempt < 4; attempt++) {
                    if (!isHttps(current.toString())) return;
                    conn = (HttpURLConnection) current.openConnection();
                    conn.setConnectTimeout(9000);
                    conn.setReadTimeout(12000);
                    conn.setInstanceFollowRedirects(false);
                    conn.setRequestProperty("Accept", "image/*");
                    int status = conn.getResponseCode();
                    if (status >= 300 && status < 400) {
                        String next = conn.getHeaderField("Location");
                        if (next == null) return;
                        current = new URL(current, next);
                        conn.disconnect(); conn = null;
                        continue;
                    }
                    if (status < 200 || status >= 300) return;
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (InputStream stream = conn.getInputStream()) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = stream.read(buf)) != -1) {
                            if (bytes.size() + n > 6 * 1024 * 1024) return;
                            bytes.write(buf, 0, n);
                        }
                    }
                    byte[] data = bytes.toByteArray();
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, opts);
                    if (opts.outWidth < 1 || opts.outHeight < 1) return;
                    opts.inSampleSize = 1;
                    while (opts.outWidth / opts.inSampleSize > 900 || opts.outHeight / opts.inSampleSize > 900)
                        opts.inSampleSize *= 2;
                    opts.inJustDecodeBounds = false;
                    Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
                    if (image != null) {
                        CACHE.put(url, image);
                        view.post(() -> { if (url.equals(view.getTag())) view.setImageBitmap(image); });
                    }
                    return;
                }
            } catch (Exception ignored) { }
            finally { if (conn != null) conn.disconnect(); }
        });
    }

    static void play(Activity activity, String source) {
        boolean local = source != null && new File(source).isFile() &&
            source.startsWith(activity.getCacheDir().getAbsolutePath() + File.separator);
        if (!local && !isHttps(source == null ? "" : source)) {
            android.widget.Toast.makeText(activity, "Аудио недоступно", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        TextView status = new TextView(activity);
        status.setText("Загрузка аудио…");
        layout.addView(status);
        Button toggle = new Button(activity);
        toggle.setText("Пауза");
        toggle.setEnabled(false);
        layout.addView(toggle);
        MediaPlayer player = new MediaPlayer();
        Handler handler = new Handler(Looper.getMainLooper());
        boolean[] ready = {false};
        boolean[] closed = {false};
        Runnable clock = new Runnable() {
            @Override public void run() {
                if (closed[0] || !ready[0]) return;
                try {
                    status.setText(stamp(player.getCurrentPosition()) + " / " + stamp(player.getDuration()));
                    handler.postDelayed(this, 500);
                } catch (RuntimeException ignored) { }
            }
        };
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Аудиосообщение")
            .setView(layout).setPositiveButton("Закрыть", null).create();
        dialog.setOnDismissListener(d -> {
            closed[0] = true;
            handler.removeCallbacks(clock);
            try { player.release(); } catch (RuntimeException ignored) { }
        });
        toggle.setOnClickListener(v -> {
            if (!ready[0] || closed[0]) return;
            try {
                if (player.isPlaying()) { player.pause(); toggle.setText("▶ Слушать"); }
                else { player.start(); toggle.setText("Пауза"); }
            } catch (RuntimeException ignored) { }
        });
        dialog.show();
        try {
            player.setDataSource(source);
            player.setOnPreparedListener(p -> {
                if (closed[0]) return;
                ready[0] = true;
                toggle.setEnabled(true);
                p.start(); handler.post(clock);
            });
            player.setOnCompletionListener(p -> {
                toggle.setText("▶ Слушать");
                try { p.seekTo(0); } catch (RuntimeException ignored) { }
            });
            player.setOnErrorListener((p, a, b) -> {
                status.setText("Не удалось воспроизвести аудио");
                toggle.setEnabled(false); ready[0] = false; return true;
            });
            player.prepareAsync();
        } catch (Exception ex) { status.setText("Не удалось открыть аудиофайл"); }
    }

    private static String stamp(int ms) {
        int sec = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%02d:%02d", sec / 60, sec % 60);
    }
}
