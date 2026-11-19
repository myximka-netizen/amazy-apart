package ru.amazy.crm.nativeclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Independent, entirely native Android UI backed by the Chatwoot application API.
 * No WebView is instantiated anywhere.
 */
public final class MainActivity extends Activity {
    private static final int NAVY = Color.rgb(24, 42, 82);
    private static final int BLUE = Color.rgb(56, 91, 206);
    private static final int PALE = Color.rgb(243, 246, 252);
    private static final int BORDER = Color.rgb(225, 231, 241);
    private static final int SECONDARY = Color.rgb(104, 117, 142);
    private static final int GREEN = Color.rgb(30, 132, 92);
    private static final int NOTE = Color.rgb(255, 245, 211);

    private enum Screen { LOGIN, LIST, CHAT }
    private Screen screen = Screen.LOGIN;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable foregroundRefresh = new Runnable() {
        @Override public void run() {
            if (foreground && screen == Screen.CHAT && currentConversation > 0) loadMessages(false);
            if (foreground) main.postDelayed(this, 25000);
        }
    };

    private LinearLayout root;
    private SecureTokenStore credentials;
    private ApiClient api;
    private boolean foreground = false;
    private int generation = 0;
    private int account = 2;
    private int page = 1;
    private String filterStatus = "open";
    private String query = "";
    private int currentConversation = -1;
    private String currentName = "";
    private String currentStatus = "open";
    private boolean loadingMessages = false;
    private ScrollView messageScroll;
    private LinearLayout messageContainer;
    private LinearLayout conversationContainer;
    private EditText draft;
    private CheckBox privateNote;
    private Button sendButton;
    private Button statusButton;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root = column();
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);
        credentials = new SecureTokenStore(this);
        String token = credentials.loadToken();
        if (token == null) showLogin("");
        else {
            try {
                api = new ApiClient(credentials.server(), token, credentials.account());
                account = credentials.account();
                showList();
            } catch (Exception ex) { showLogin(ex.getMessage()); }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        main.removeCallbacks(foregroundRefresh);
        main.postDelayed(foregroundRefresh, 25000);
    }

    @Override protected void onPause() {
        foreground = false;
        main.removeCallbacks(foregroundRefresh);
        super.onPause();
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(foregroundRefresh);
        worker.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView text(String s, int size, int color, boolean strong) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (strong) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setMinHeight(dp(46));
        b.setTextColor(primary ? Color.WHITE : BLUE);
        b.setBackground(bg(primary ? BLUE : PALE, 12));
        return b;
    }

    private EditText field(String hint, String value, boolean secret) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(bg(PALE, 12));
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        if (secret) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        return input;
    }

    private void paddingView(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private void clear() {
        generation++;
        root.removeAllViews();
        root.setPadding(0, 0, 0, 0);
        root.setBackgroundColor(Color.WHITE);
        messageScroll = null;
        messageContainer = null;
        conversationContainer = null;
        draft = null;
        privateNote = null;
        sendButton = null;
        statusButton = null;
        loadingMessages = false;
    }

    private void showError(String message) {
        Toast.makeText(this, message == null ? "Неизвестная ошибка" : message, Toast.LENGTH_LONG).show();
    }

    private void showLogin(String error) {
        screen = Screen.LOGIN;
        currentConversation = -1;
        clear();
        ScrollView scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
        LinearLayout form = column();
        form.setPadding(dp(22), dp(48), dp(22), dp(28));
        scroll.addView(form);
        TextView symbol = text("✦  ВОЛШЕБНО ТУТ", 14, BLUE, true);
        form.addView(symbol);
        paddingView(form, 22);
        form.addView(text("CRM для гостей", 29, NAVY, true));
        paddingView(form, 12);
        form.addView(text("Нативное Android-приложение, напрямую подключённое к API твоего Chatwoot.", 15, SECONDARY, false));
        paddingView(form, 30);

        form.addView(text("Адрес HTTPS-сервера", 14, NAVY, true));
        paddingView(form, 7);
        EditText server = field("https://crm.example.ru", credentials.server(), false);
        server.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        form.addView(server, new LinearLayout.LayoutParams(-1, dp(56)));
        paddingView(form, 18);

        form.addView(text("ID аккаунта Chatwoot", 14, NAVY, true));
        paddingView(form, 7);
        EditText acc = field("2", Integer.toString(credentials.account()), false);
        acc.setInputType(InputType.TYPE_CLASS_NUMBER);
        form.addView(acc, new LinearLayout.LayoutParams(-1, dp(56)));
        paddingView(form, 18);

        form.addView(text("Личный API Access Token", 14, NAVY, true));
        paddingView(form, 7);
        EditText token = field("Вставь токен из настроек профиля", "", true);
        form.addView(token, new LinearLayout.LayoutParams(-1, dp(56)));
        paddingView(form, 11);
        form.addView(text("Chatwoot на компьютере → Настройки профиля → Access Token. Никому не пересылай токен, включая этот чат.", 13, SECONDARY, false));
        paddingView(form, 22);
        Button connect = button("Подключиться к Chatwoot", true);
        form.addView(connect, new LinearLayout.LayoutParams(-1, dp(56)));
        paddingView(form, 13);
        TextView errorBox = text(error == null ? "" : error, 14, Color.rgb(177, 41, 57), false);
        form.addView(errorBox);
        paddingView(form, 12);
        form.addView(text("Токен сохраняется зашифрованным через Android Keystore. Логин и пароль Chatwoot приложению не требуются.", 12, SECONDARY, false));

        connect.setOnClickListener(v -> {
            String address;
            int chosenAccount;
            String key = token.getText().toString().trim();
            try {
                address = ApiClient.normalize(server.getText().toString());
                chosenAccount = Integer.parseInt(acc.getText().toString().trim());
                if (chosenAccount < 1 || key.isBlank()) throw new IllegalArgumentException("Укажи ID аккаунта и токен");
            } catch (Exception ex) {
                errorBox.setText(ex.getMessage() == null ? "Проверь заполненные поля" : ex.getMessage());
                return;
            }
            connect.setEnabled(false);
            connect.setText("Проверяем подключение...");
            errorBox.setText("");
            int tokenForCall = generation;
            worker.execute(() -> {
                try {
                    ApiClient candidate = new ApiClient(address, key, chosenAccount);
                    candidate.profile();
                    candidate.conversations("open", 1, "");
                    credentials.save(address, chosenAccount, key);
                    main.post(() -> {
                        if (screen != Screen.LOGIN || tokenForCall != generation) return;
                        api = candidate;
                        account = chosenAccount;
                        page = 1;
                        showList();
                    });
                } catch (Exception ex) {
                    main.post(() -> {
                        if (screen != Screen.LOGIN || tokenForCall != generation) return;
                        errorBox.setText(ex.getMessage());
                        connect.setEnabled(true);
                        connect.setText("Подключиться к Chatwoot");
                    });
                }
            });
        });
    }

    private LinearLayout toolbar(String title, String subtitle) {
        LinearLayout shell = column();
        shell.setPadding(dp(17), dp(14), dp(16), dp(12));
        shell.setBackgroundColor(NAVY);
        TextView header = text(title, 21, Color.WHITE, true);
        shell.addView(header);
        if (subtitle != null && !subtitle.isEmpty()) {
            paddingView(shell, 5);
            shell.addView(text(subtitle, 12, Color.rgb(205, 218, 248), false));
        }
        return shell;
    }

    private void showList() {
        if (api == null) { showLogin("Сначала подключись к серверу"); return; }
        screen = Screen.LIST;
        currentConversation = -1;
        clear();
        LinearLayout header = toolbar("Волшебно тут", "Диалоги гостей · Chatwoot #" + account);
        LinearLayout actionRow = row();
        actionRow.addView(text("Все каналы в одном месте", 14, Color.WHITE, false),
            new LinearLayout.LayoutParams(0, dp(44), 1));
        Button refresh = button("↻", false);
        refresh.setContentDescription("Обновить диалоги");
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(48), dp(44));
        rp.rightMargin = dp(7);
        actionRow.addView(refresh, rp);
        refresh.setOnClickListener(v -> loadConversations());
        Button settings = button("⚙", false);
        settings.setContentDescription("Настройки соединения");
        actionRow.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(44)));
        settings.setOnClickListener(v -> showSettings());
        header.addView(actionRow);
        root.addView(header);

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = row();
        tabs.setPadding(dp(12), dp(12), dp(12), dp(10));
        addTab(tabs, "Открытые", "open");
        addTab(tabs, "Ожидание", "pending");
        addTab(tabs, "Закрытые", "resolved");
        addTab(tabs, "Все", "all");
        horizontal.addView(tabs);
        root.addView(horizontal);

        LinearLayout search = row();
        search.setPadding(dp(15), dp(3), dp(15), dp(12));
        EditText input = field("Поиск по сообщениям", query, false);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.addView(input, new LinearLayout.LayoutParams(0, dp(49), 1));
        Button go = button("Найти", true);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(82), dp(49));
        gp.leftMargin = dp(9);
        search.addView(go, gp);
        go.setOnClickListener(v -> {
            query = input.getText().toString().trim();
            page = 1;
            loadConversations();
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            query = input.getText().toString().trim();
            page = 1;
            loadConversations();
            return true;
        });
        root.addView(search);

        View divider = new View(this);
        divider.setBackgroundColor(BORDER);
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        conversationContainer = column();
        conversationContainer.setBackgroundColor(Color.WHITE);
        scroll.addView(conversationContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = row();
        footer.setPadding(dp(15), dp(7), dp(15), dp(9));
        footer.setBackgroundColor(PALE);
        Button prev = button("←", false);
        footer.addView(prev, new LinearLayout.LayoutParams(dp(54), dp(43)));
        prev.setOnClickListener(v -> { if (page > 1) { page--; loadConversations(); } });
        TextView pageLabel = text("Страница " + page, 14, NAVY, true);
        pageLabel.setGravity(Gravity.CENTER);
        footer.addView(pageLabel, new LinearLayout.LayoutParams(0, dp(43), 1));
        Button next = button("Дальше →", false);
        footer.addView(next, new LinearLayout.LayoutParams(dp(108), dp(43)));
        next.setOnClickListener(v -> { page++; loadConversations(); });
        root.addView(footer);
        loadConversations();
    }

    private void addTab(LinearLayout tabs, String label, String status) {
        boolean selected = status.equals(filterStatus);
        Button btn = button(label, selected);
        btn.setTextSize(13);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(43));
        params.rightMargin = dp(8);
        tabs.addView(btn, params);
        btn.setOnClickListener(v -> {
            filterStatus = status;
            page = 1;
            showList();
        });
    }

    private void loadConversations() {
        if (conversationContainer == null || screen != Screen.LIST) return;
        final int request = ++generation;
        conversationContainer.removeAllViews();
        TextView loading = text("Загружаем диалоги…", 15, SECONDARY, false);
        loading.setGravity(Gravity.CENTER);
        conversationContainer.addView(loading, new LinearLayout.LayoutParams(-1, dp(160)));
        final int requestedPage = page;
        final String requestedStatus = filterStatus;
        final String requestedQuery = query;
        worker.execute(() -> {
            try {
                JSONObject result = api.conversations(requestedStatus, requestedPage, requestedQuery);
                main.post(() -> {
                    if (screen != Screen.LIST || generation != request || conversationContainer == null) return;
                    renderConversations(result);
                });
            } catch (Exception ex) {
                main.post(() -> {
                    if (screen != Screen.LIST || generation != request || conversationContainer == null) return;
                    conversationContainer.removeAllViews();
                    TextView err = text(ex.getMessage(), 15, Color.rgb(179, 45, 62), false);
                    err.setPadding(dp(18), dp(28), dp(18), dp(18));
                    conversationContainer.addView(err);
                    Button retry = button("Повторить", true);
                    conversationContainer.addView(retry, new LinearLayout.LayoutParams(-1, dp(52)));
                    retry.setOnClickListener(v -> loadConversations());
                });
            }
        });
    }

    private void renderConversations(JSONObject result) {
        conversationContainer.removeAllViews();
        JSONObject data = result.optJSONObject("data");
        JSONArray items = data != null ? data.optJSONArray("payload") : result.optJSONArray("payload");
        if (items == null) items = new JSONArray();
        if (items.length() == 0) {
            TextView empty = text("Диалогов не найдено. Измени фильтр или поисковый запрос.", 15, SECONDARY, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(25), dp(30), dp(25), dp(30));
            conversationContainer.addView(empty, new LinearLayout.LayoutParams(-1, dp(180)));
            return;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            int id = item.optInt("id", -1);
            if (id < 1) continue;
            JSONObject meta = item.optJSONObject("meta");
            JSONObject sender = meta == null ? null : meta.optJSONObject("sender");
            if (sender == null && meta != null) sender = meta.optJSONObject("contact");
            String name = sender != null ? sender.optString("name", "") : "";
            if (name.isBlank()) name = "Гость #" + id;
            String convStatus = item.optString("status", "open");
            int inbox = item.optInt("inbox_id", 0);
            int unread = item.optInt("unread_count", 0);
            JSONObject recent = item.optJSONObject("last_non_activity_message");
            if (recent == null) {
                JSONArray messages = item.optJSONArray("messages");
                if (messages != null && messages.length() > 0)
                    recent = messages.optJSONObject(messages.length() - 1);
            }
            String preview = recent == null ? "Нет текста последнего сообщения" : recent.optString("content", "");
            if (preview.isBlank()) preview = "Вложение или системное сообщение";
            long ts = item.optLong("last_activity_at", 0L);
            addConversationRow(id, name, convStatus, preview, unread, inbox, ts);
        }
    }

    private void addConversationRow(int id, String name, String status, String preview, int unread, int inbox, long timestamp) {
        LinearLayout row = row();
        row.setPadding(dp(16), dp(15), dp(16), dp(15));
        row.setBackgroundColor(Color.WHITE);
        TextView avatar = text(name.substring(0, Math.min(1, name.length())).toUpperCase(Locale.ROOT), 20, BLUE, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(bg(Color.rgb(234, 239, 255), 24));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout body = column();
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1);
        bp.leftMargin = dp(12);
        row.addView(body, bp);
        TextView contact = text(name, 16, NAVY, true);
        contact.setSingleLine(true);
        contact.setEllipsize(TextUtils.TruncateAt.END);
        body.addView(contact);
        TextView info = text("Канал #" + inbox + " · " + displayStatus(status) + (timestamp > 0 ? " · " + stamp(timestamp) : ""), 12, SECONDARY, false);
        body.addView(info);
        TextView last = text(preview, 14, SECONDARY, false);
        last.setMaxLines(2);
        last.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        body.addView(last, lp);
        if (unread > 0) {
            TextView count = text(Integer.toString(unread), 12, Color.WHITE, true);
            count.setGravity(Gravity.CENTER);
            count.setBackground(bg(GREEN, 18));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(31), dp(31));
            cp.leftMargin = dp(8);
            row.addView(count, cp);
        }
        conversationContainer.addView(row);
        View line = new View(this);
        line.setBackgroundColor(BORDER);
        conversationContainer.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        row.setOnClickListener(v -> showChat(id, name, status));
    }

    private String displayStatus(String s) {
        switch (s) {
            case "open": return "Открыт";
            case "resolved": return "Закрыт";
            case "pending": return "Ожидание";
            case "snoozed": return "Отложен";
            default: return s;
        }
    }

    private String stamp(long epochSec) {
        try {
            long ms = epochSec * 1000;
            boolean today = new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date(ms))
                .equals(new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date()));
            return new SimpleDateFormat(today ? "HH:mm" : "dd.MM", new Locale("ru")).format(new Date(ms));
        } catch (Exception ex) { return ""; }
    }

    private void showSettings() {
        new AlertDialog.Builder(this)
            .setTitle("Подключение")
            .setMessage("Сервер: " + credentials.server() + "\nАккаунт: #" + account +
                "\n\nТокен хранится в Android Keystore. Чтобы изменить сервер или токен, выйди из аккаунта.")
            .setPositiveButton("Закрыть", (d, w) -> d.dismiss())
            .setNegativeButton("Сменить подключение", (d, w) -> {
                credentials.clear();
                api = null;
                showLogin("");
            })
            .show();
    }

    private void showChat(int id, String name, String status) {
        screen = Screen.CHAT;
        currentConversation = id;
        currentName = name;
        currentStatus = status;
        clear();
        LinearLayout header = toolbar("‹  " + name, "Диалог #" + id + " · " + displayStatus(status));
        header.setOnClickListener(v -> showList());
        LinearLayout actions = row();
        Button back = button("← Диалоги", false);
        actions.addView(back, new LinearLayout.LayoutParams(0, dp(44), 1));
        back.setOnClickListener(v -> showList());
        statusButton = button(status.equals("resolved") ? "Открыть" : "Закрыть", false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(110), dp(44));
        sp.leftMargin = dp(8);
        actions.addView(statusButton, sp);
        statusButton.setOnClickListener(v -> toggleStatus());
        header.addView(actions);
        root.addView(header);

        messageScroll = new ScrollView(this);
        messageScroll.setFillViewport(true);
        messageScroll.setBackgroundColor(PALE);
        messageContainer = column();
        messageContainer.setPadding(dp(12), dp(14), dp(12), dp(16));
        messageScroll.addView(messageContainer);
        root.addView(messageScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composer = column();
        composer.setPadding(dp(12), dp(10), dp(12), dp(12));
        composer.setBackgroundColor(Color.WHITE);
        privateNote = new CheckBox(this);
        privateNote.setText("Внутренняя заметка (не отправлять гостю)");
        privateNote.setTextColor(NAVY);
        privateNote.setTextSize(13);
        composer.addView(privateNote);
        LinearLayout write = row();
        draft = field("Введите сообщение...", "", false);
        draft.setSingleLine(false);
        draft.setMinLines(1);
        draft.setMaxLines(4);
        draft.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        write.addView(draft, new LinearLayout.LayoutParams(0, dp(60), 1));
        sendButton = button("➤", true);
        sendButton.setContentDescription("Отправить сообщение");
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(59), dp(60));
        sendParams.leftMargin = dp(8);
        write.addView(sendButton, sendParams);
        sendButton.setOnClickListener(v -> send());
        composer.addView(write);
        root.addView(composer);
        loadMessages(true);
    }

    private void loadMessages(boolean indicate) {
        if (screen != Screen.CHAT || messageContainer == null || loadingMessages) return;
        final int id = currentConversation;
        final int request = generation;
        loadingMessages = true;
        if (indicate) {
            messageContainer.removeAllViews();
            TextView loading = text("Загрузка переписки…", 15, SECONDARY, false);
            loading.setGravity(Gravity.CENTER);
            messageContainer.addView(loading, new LinearLayout.LayoutParams(-1, dp(160)));
        }
        worker.execute(() -> {
            try {
                JSONObject result = api.messages(id);
                main.post(() -> {
                    loadingMessages = false;
                    if (screen != Screen.CHAT || request != generation || currentConversation != id || messageContainer == null) return;
                    renderMessages(result);
                });
                // Read receipts are best-effort and do not block the user interface.
                try { api.markSeen(id); } catch (Exception ignored) { }
            } catch (Exception ex) {
                main.post(() -> {
                    loadingMessages = false;
                    if (screen != Screen.CHAT || request != generation || currentConversation != id || messageContainer == null) return;
                    if (indicate) {
                        messageContainer.removeAllViews();
                        messageContainer.addView(text("Не удалось загрузить переписку: " + ex.getMessage(), 15, Color.RED, false));
                    } else showError(ex.getMessage());
                });
            }
        });
    }

    private void renderMessages(JSONObject result) {
        if (messageContainer == null) return;
        messageContainer.removeAllViews();
        JSONArray items = result.optJSONArray("payload");
        if (items == null) items = new JSONArray();
        if (items.length() == 0) {
            TextView empty = text("В этом диалоге пока нет сообщений", 14, SECONDARY, false);
            empty.setGravity(Gravity.CENTER);
            messageContainer.addView(empty, new LinearLayout.LayoutParams(-1, dp(140)));
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject m = items.optJSONObject(i);
            if (m == null) continue;
            int type = m.optInt("message_type", 0);
            boolean note = m.optBoolean("private", false);
            String content = m.optString("content", "");
            JSONArray attachments = m.optJSONArray("attachments");
            if (type == 2 && content.isBlank()) continue;
            addMessageBubble(content, type, note, m.optLong("created_at", 0), attachments);
        }
        if (messageScroll != null) messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addMessageBubble(String content, int type, boolean note, long created, JSONArray attachments) {
        boolean outgoing = type == 1;
        LinearLayout line = row();
        line.setGravity(outgoing ? Gravity.RIGHT : Gravity.LEFT);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, -2);
        lineParams.bottomMargin = dp(10);
        messageContainer.addView(line, lineParams);
        LinearLayout bubble = column();
        bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
        bubble.setBackground(bg(note ? NOTE : outgoing ? Color.rgb(222, 233, 255) : Color.WHITE, 15));
        line.addView(bubble, new LinearLayout.LayoutParams(-2, -2));

        if (note) bubble.addView(text("🔒 Внутренняя заметка", 11, Color.rgb(154, 107, 20), true));
        if (type == 2 && !note) bubble.addView(text("Событие", 11, SECONDARY, true));
        TextView body = text(content.isBlank() ? " " : content, 15, NAVY, false);
        body.setMaxWidth(dp(285));
        body.setTextIsSelectable(true);
        bubble.addView(body);
        if (attachments != null) {
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject a = attachments.optJSONObject(i);
                if (a == null) continue;
                String link = a.optString("data_url", "");
                if (link.isBlank()) link = a.optString("external_url", "");
                final String url = link;
                Button open = button("📎 Открыть вложение " + (i + 1), false);
                LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(44));
                ap.topMargin = dp(5);
                bubble.addView(open, ap);
                open.setOnClickListener(v -> {
                    try {
                        if (!url.startsWith("https://")) { showError("Недоступная ссылка вложения"); return; }
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                    } catch (Exception ex) { showError("Не удалось открыть вложение"); }
                });
            }
        }
        if (created > 0) {
            TextView at = text(stamp(created), 11, SECONDARY, false);
            at.setGravity(Gravity.RIGHT);
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-1, -2);
            timeParams.topMargin = dp(4);
            bubble.addView(at, timeParams);
        }
    }

    private void send() {
        if (screen != Screen.CHAT || draft == null || sendButton == null) return;
        final String body = draft.getText().toString().trim();
        if (body.isBlank()) return;
        final int id = currentConversation;
        final int request = generation;
        final boolean note = privateNote != null && privateNote.isChecked();
        sendButton.setEnabled(false);
        sendButton.setText("...");
        worker.execute(() -> {
            try {
                api.sendMessage(id, body, note);
                main.post(() -> {
                    if (screen != Screen.CHAT || request != generation || currentConversation != id) return;
                    if (draft != null) draft.setText("");
                    if (sendButton != null) { sendButton.setEnabled(true); sendButton.setText("➤"); }
                    loadMessages(false);
                });
            } catch (Exception ex) {
                main.post(() -> {
                    if (screen != Screen.CHAT || request != generation || currentConversation != id) return;
                    if (sendButton != null) { sendButton.setEnabled(true); sendButton.setText("➤"); }
                    new AlertDialog.Builder(this)
                        .setTitle("Сообщение не подтверждено")
                        .setMessage(ex.getMessage() + "\n\nПроверь переписку перед повторной отправкой, чтобы не создать дубликат.")
                        .setPositiveButton("Понятно", null).show();
                });
            }
        });
    }

    private void toggleStatus() {
        if (screen != Screen.CHAT || currentConversation < 1 || statusButton == null) return;
        String next = currentStatus.equals("resolved") ? "open" : "resolved";
        int id = currentConversation;
        int request = generation;
        statusButton.setEnabled(false);
        worker.execute(() -> {
            try {
                api.setStatus(id, next);
                main.post(() -> {
                    if (screen != Screen.CHAT || request != generation || currentConversation != id) return;
                    currentStatus = next;
                    statusButton.setEnabled(true);
                    statusButton.setText(next.equals("resolved") ? "Открыть" : "Закрыть");
                    showError(next.equals("resolved") ? "Диалог закрыт" : "Диалог открыт");
                });
            } catch (Exception ex) {
                main.post(() -> {
                    if (screen != Screen.CHAT || request != generation || currentConversation != id) return;
                    statusButton.setEnabled(true);
                    showError(ex.getMessage());
                });
            }
        });
    }

    @Override public void onBackPressed() {
        if (screen == Screen.CHAT) showList();
        else super.onBackPressed();
    }
}
