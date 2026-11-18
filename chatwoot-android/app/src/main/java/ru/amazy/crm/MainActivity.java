package ru.amazy.crm;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Independent Android shell for an existing HTTPS Chatwoot installation.
 * Authentication takes place on the user's Chatwoot server, not inside this application.
 */
public final class MainActivity extends Activity {
    private static final String PREFS = "amazy_chatwoot_prefs";
    private static final String KEY_URL = "server_url";
    private static final int FILE_REQUEST = 4170;
    private final int indigo = Color.rgb(52, 92, 232);

    private LinearLayout root;
    private WebView webView;
    private ProgressBar progress;
    private ValueCallback<Uri[]> fileCallback;
    private String serverUrl;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(21, 43, 103));
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);
        serverUrl = getPreferences(MODE_PRIVATE).getString(KEY_URL, "");
        if (serverUrl.isEmpty()) showSetup(); else showBrowser();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String text, int size, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        return b;
    }

    private void closeWebView() {
        if (webView != null) {
            root.removeView(webView);
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
    }

    private void showSetup() {
        closeWebView();
        root.removeAllViews();
        root.setPadding(dp(20), dp(24), dp(20), dp(16));
        TextView title = text("Волшебно тут CRM", 26, indigo);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);
        TextView instructions = text("Укажи HTTPS-адрес своего сервера Chatwoot. Адрес можно изменить в любой момент.", 16, Color.DKGRAY);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.topMargin = dp(18);
        root.addView(instructions, ip);
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        field.setHint("https://your-server.example.com");
        field.setText(serverUrl);
        field.setSelectAllOnFocus(true);
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(60));
        ep.topMargin = dp(14);
        root.addView(field, ep);
        Button connect = button("Подключиться");
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(54));
        cp.topMargin = dp(18);
        root.addView(connect, cp);
        connect.setOnClickListener(v -> {
            String candidate = validateServerUrl(field.getText().toString());
            if (candidate == null) {
                field.setError("Нужен корректный HTTPS-адрес сервера");
                return;
            }
            serverUrl = candidate;
            getPreferences(MODE_PRIVATE).edit().putString(KEY_URL, serverUrl).apply();
            showBrowser();
        });
        TextView note = text("Логин и пароль вводятся только на странице твоего сервера. Приложение не отправляет их сторонним сервисам.", 13, Color.GRAY);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(20);
        root.addView(note, np);
    }

    private String validateServerUrl(String input) {
        try {
            String s = input.trim();
            if (s.isEmpty()) return null;
            if (!s.contains("://")) s = "https://" + s;
            URI uri = new URI(s);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().isEmpty()) return null;
            if (uri.getUserInfo() != null) return null;
            return new URI("https", null, uri.getHost(), uri.getPort(), null, null, null).toString();
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean sameServer(Uri uri) {
        try {
            URI main = new URI(serverUrl);
            return "https".equalsIgnoreCase(uri.getScheme())
                && main.getHost().equalsIgnoreCase(uri.getHost())
                && main.getPort() == uri.getPort();
        } catch (Exception ex) { return false; }
    }

    private void showBrowser() {
        closeWebView();
        root.removeAllViews();
        root.setPadding(0, 0, 0, 0);
        LinearLayout header = new LinearLayout(this);
        header.setPadding(dp(12), 0, dp(6), 0);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.rgb(242, 245, 252));
        TextView title = text("Волшебно тут CRM", 17, indigo);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(52), 1));
        title.setGravity(Gravity.CENTER_VERTICAL);
        Button reload = button("↻");
        header.addView(reload, new LinearLayout.LayoutParams(dp(50), dp(52)));
        Button settings = button("⚙");
        header.addView(settings, new LinearLayout.LayoutParams(dp(50), dp(52)));
        root.addView(header);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(3)));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(true);
        ws.setJavaScriptCanOpenWindowsAutomatically(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setSupportZoom(false);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                if (!req.isForMainFrame()) return false;
                Uri uri = req.getUrl();
                if (sameServer(uri)) return false;
                String scheme = uri.getScheme();
                if ("about".equalsIgnoreCase(scheme)) return false;
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)
                    || "mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme)) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                    catch (ActivityNotFoundException ignored) {
                        Toast.makeText(MainActivity.this, "Нет приложения для открытия ссылки", Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) {
                if (progress == null) return;
                progress.setProgress(value);
                progress.setVisibility(value >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public boolean onShowFileChooser(WebView view,
                                                        ValueCallback<Uri[]> callback,
                                                        FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_REQUEST);
                    return true;
                } catch (ActivityNotFoundException ex) {
                    fileCallback = null;
                    return false;
                }
            }
        });
        webView.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String url, String userAgent,
                                                   String disposition, String mimeType, long length) {
                try {
                    Uri uri = Uri.parse(url);
                    if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Not HTTPS");
                    DownloadManager.Request req = new DownloadManager.Request(uri);
                    req.setMimeType(mimeType);
                    req.addRequestHeader("User-Agent", userAgent);
                    if (sameServer(uri)) {
                        String cookies = CookieManager.getInstance().getCookie(url);
                        if (cookies != null) req.addRequestHeader("Cookie", cookies);
                    }
                    String name = URLUtil.guessFileName(url, disposition, mimeType).replaceAll("[/\\\\]", "_");
                    req.setTitle(name);
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                    ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(req);
                    Toast.makeText(MainActivity.this, "Скачивание: " + name, Toast.LENGTH_SHORT).show();
                } catch (Exception ex) {
                    Toast.makeText(MainActivity.this, "Не удалось скачать файл", Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(webView, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        reload.setOnClickListener(v -> webView.reload());
        settings.setOnClickListener(v -> showSetup());
        webView.loadUrl(serverUrl);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, intent));
            fileCallback = null;
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        closeWebView();
        super.onDestroy();
    }
}
