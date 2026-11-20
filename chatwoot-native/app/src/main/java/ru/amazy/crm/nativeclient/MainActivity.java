package ru.amazy.crm.nativeclient;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.ClipData;
import android.media.MediaRecorder;
import android.os.SystemClock;
import java.io.File;
import java.util.ArrayList;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
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
    private static final int NOTIFICATION_PERMISSION = 33005;
    private static final int PICK_ATTACHMENTS = 33006;
    private static final int MICROPHONE_PERMISSION = 33007;
    // Chatwoot-inspired visual system: quiet white surfaces, slate text, bright blue actions.
    private static final int NAVY = Color.rgb(31, 41, 55);
    private static final int BLUE = Color.rgb(31, 147, 255);
    private static final int PALE = Color.rgb(248, 250, 252);
    private static final int BORDER = Color.rgb(226, 232, 240);
    private static final int SECONDARY = Color.rgb(100, 116, 139);
    private static final int GREEN = Color.rgb(22, 163, 74);
    private static final int AUTO_REFRESH_MS = 15000;
    private static final int NOTE = Color.rgb(255, 245, 211);

    private enum Screen { LOGIN, LIST, CHAT }
    private Screen screen = Screen.LOGIN;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    // Poll while visible; FCM still handles background notifications independently.
    private final Runnable foregroundRefresh = new Runnable() {
        @Override public void run() {
            if (!foreground) return;
            if (sending) { main.postDelayed(this, AUTO_REFRESH_MS); return; }
            if (screen == Screen.LIST) loadConversations(false);
            else if (screen == Screen.CHAT && currentConversation > 0) loadMessages(false);
            main.postDelayed(this, AUTO_REFRESH_MS);
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
    private String assigneeType = "all";
    private int selectedInbox = 0;
    private String selectedInboxName = "Все каналы";
    private final ArrayList<AttachmentDraft> pendingAttachments = new ArrayList<>();
    private LinearLayout attachmentTray;
    private Button attachButton, micButton, replyMode, noteMode;
    private TextView recordIndicator;
    private MediaRecorder recorder;
    private File recordingFile;
    private long recordStarted;
    private boolean sending = false;
    private int pickerConversation = -1;
    private int permissionConversation = -1;
    private final Runnable recordingClock = new Runnable() {
        @Override public void run() {
            if (recorder == null) return;
            long seconds = (SystemClock.elapsedRealtime() - recordStarted) / 1000;
            if (recordIndicator != null) recordIndicator.setText(String.format(Locale.ROOT,
                "● Запись  %02d:%02d", seconds / 60, seconds % 60));
            if (seconds >= 600) { finishRecording(true); return; }
            main.postDelayed(this, 250);
        }
    };
    private String query = "";
    private boolean listLoading = false;
    private String lastListFingerprint = "";
    private String lastMessageFingerprint = "";
    private ScrollView conversationScroll;
    private TextView syncLabel;
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
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
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
                PushBridge.onLogin(this);
                openPush(getIntent());
                requestPushPermission();
            } catch (Exception ex) { showLogin(ex.getMessage()); }
        }
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        main.removeCallbacks(foregroundRefresh);
        main.post(foregroundRefresh);
    }

    @Override protected void onPause() {
        foreground = false;
        if (recorder != null) finishRecording(true);
        main.removeCallbacks(foregroundRefresh);
        super.onPause();
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(foregroundRefresh);
        if (recorder != null) finishRecording(false);
        if (!sending) discardAttachments();
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
        if (recorder != null) finishRecording(false);
        discardAttachments();
        attachmentTray = null;
        recordIndicator = null;
        attachButton = null;
        micButton = null;
        replyMode = null;
        noteMode = null;
        root.removeAllViews();
        root.setPadding(0, 0, 0, 0);
        root.setBackgroundColor(Color.WHITE);
        messageScroll = null;
        messageContainer = null;
        conversationContainer = null;
        conversationScroll = null;
        syncLabel = null;
        listLoading = false;
        lastListFingerprint = "";
        lastMessageFingerprint = "";
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
                        PushBridge.onLogin(MainActivity.this);
                        requestPushPermission();
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
        shell.setPadding(dp(17), dp(15), dp(16), dp(13));
        shell.setBackgroundColor(Color.WHITE);
        TextView header = text(title, 22, NAVY, true);
        shell.addView(header);
        if (subtitle != null && !subtitle.isEmpty()) {
            paddingView(shell, 3);
            shell.addView(text(subtitle, 12, SECONDARY, false));
        }
        return shell;
    }

    private void showList() {
        if (sending) { showError("Дождись завершения отправки"); return; }
        if (api == null) { showLogin("Сначала подключись к серверу"); return; }
        screen = Screen.LIST;
        currentConversation = -1;
        clear();
        LinearLayout header = toolbar("Диалоги", "Волшебно тут · Рабочее пространство");
        Button channels = button("☰  " + selectedInboxName + "  ▾", false);
        channels.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        channels.setPadding(dp(12), 0, dp(12), 0);
        header.addView(channels, new LinearLayout.LayoutParams(-1, dp(40)));
        channels.setOnClickListener(v -> selectInbox());
        LinearLayout actionRow = row();
        syncLabel = text("● Автообновление каждые 15 сек.", 12, GREEN, false);
        actionRow.addView(syncLabel, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button refresh = button("↻", false);
        refresh.setContentDescription("Обновить список сейчас");
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(48), dp(42));
        rp.rightMargin = dp(7);
        actionRow.addView(refresh, rp);
        refresh.setOnClickListener(v -> loadConversations(true));
        Button settings = button("⚙", false);
        settings.setContentDescription("Подключение и уведомления");
        actionRow.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(42)));
        settings.setOnClickListener(v -> showSettings());
        header.addView(actionRow);
        root.addView(header);
        View headLine = new View(this);
        headLine.setBackgroundColor(BORDER);
        root.addView(headLine, new LinearLayout.LayoutParams(-1, dp(1)));

        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = row();
        tabs.setPadding(dp(12), dp(12), dp(12), dp(10));
        addTab(tabs, "Открытые", "open");
        addTab(tabs, "Ожидание", "pending");
        addTab(tabs, "Отложенные", "snoozed");
        addTab(tabs, "Закрытые", "resolved");
        addTab(tabs, "Все", "all");
        horizontal.addView(tabs);
        root.addView(horizontal);

        LinearLayout assignmentTabs = row();
        assignmentTabs.setPadding(dp(12), dp(1), dp(12), dp(5));
        addScopeTab(assignmentTabs, "Мои", "me");
        addScopeTab(assignmentTabs, "Без оператора", "unassigned");
        addScopeTab(assignmentTabs, "Все", "all");
        root.addView(assignmentTabs);

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

        conversationScroll = new ScrollView(this);
        conversationScroll.setFillViewport(true);
        conversationContainer = column();
        conversationContainer.setBackgroundColor(Color.WHITE);
        conversationScroll.addView(conversationContainer);
        root.addView(conversationScroll, new LinearLayout.LayoutParams(-1, 0, 1));

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
        loadConversations(true);
    }

    private void addScopeTab(LinearLayout tabs, String label, String scope) {
        Button chip = button(label, scope.equals(assigneeType));
        chip.setTextSize(12);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(37));
        p.rightMargin = dp(7);
        tabs.addView(chip, p);
        chip.setOnClickListener(v -> {
            if (assigneeType.equals(scope)) return;
            assigneeType = scope;
            page = 1;
            showList();
        });
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

    private void loadConversations() { loadConversations(true); }

    private void loadConversations(boolean indicate) {
        if (conversationContainer == null || screen != Screen.LIST || listLoading) return;
        listLoading = true;
        final int request = ++generation;
        final int scrollY = conversationScroll == null ? 0 : conversationScroll.getScrollY();
        final boolean showSpinner = indicate && lastListFingerprint.isEmpty();
        if (showSpinner) {
            conversationContainer.removeAllViews();
            TextView loading = text("Загружаем диалоги…", 14, SECONDARY, false);
            loading.setGravity(Gravity.CENTER);
            conversationContainer.addView(loading, new LinearLayout.LayoutParams(-1, dp(120)));
        }
        if (syncLabel != null) syncLabel.setText("↻ Синхронизация…");
        final int requestedPage = page;
        final String requestedStatus = filterStatus;
        final String requestedQuery = query;
        final String requestedAssignee = assigneeType;
        final int requestedInbox = selectedInbox;
        worker.execute(() -> {
            try {
                JSONObject result = api.conversations(requestedStatus, requestedPage, requestedQuery, requestedAssignee, requestedInbox);
                main.post(() -> {
                    if (screen != Screen.LIST || generation != request || conversationContainer == null) return;
                    listLoading = false;
                    String fingerprint = requestedPage + ":" + requestedStatus + ":" +
                        requestedQuery + ":" + requestedAssignee + ":" + requestedInbox + ":" + result.toString();
                    if (!fingerprint.equals(lastListFingerprint) || indicate) {
                        renderConversations(result);
                        if (!lastListFingerprint.isEmpty() && conversationScroll != null) {
                            conversationScroll.post(() -> conversationScroll.scrollTo(0, scrollY));
                        }
                        lastListFingerprint = fingerprint;
                    }
                    if (syncLabel != null)
                        syncLabel.setText("● Обновлено в " + new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date())
                            + " · каждые 15 сек.");
                });
            } catch (Exception ex) {
                main.post(() -> {
                    if (screen != Screen.LIST || generation != request || conversationContainer == null) return;
                    listLoading = false;
                    if (syncLabel != null) syncLabel.setText("● Нет связи · повтор через 15 сек.");
                    if (lastListFingerprint.isEmpty()) {
                        conversationContainer.removeAllViews();
                        TextView err = text(ex.getMessage(), 15, Color.rgb(179, 45, 62), false);
                        err.setPadding(dp(18), dp(28), dp(18), dp(18));
                        conversationContainer.addView(err);
                        Button retry = button("Повторить", true);
                        conversationContainer.addView(retry, new LinearLayout.LayoutParams(-1, dp(52)));
                        retry.setOnClickListener(v -> loadConversations(true));
                    }
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
            String channel = meta == null ? "" : meta.optString("channel", "");
            JSONObject assignee = meta == null ? null : meta.optJSONObject("assignee");
            String agent = assignee == null ? "" : assignee.optString("name", "");
            JSONArray labels = item.optJSONArray("labels");
            addConversationRow(id, name, convStatus, preview, unread, inbox, ts, channel, agent, labels);
        }
    }

    private void addConversationRow(int id, String name, String status, String preview, int unread, int inbox, long timestamp, String channel, String agent, JSONArray labels) {
        LinearLayout row = row();
        row.setPadding(dp(15), dp(13), dp(15), dp(13));
        row.setBackgroundColor(Color.WHITE);
        TextView avatar = text(name.substring(0, Math.min(1, name.length())).toUpperCase(Locale.ROOT), 20, BLUE, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(bg(Color.rgb(231, 244, 255), 24));
        row.addView(avatar, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout body = column();
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1);
        bp.leftMargin = dp(12);
        row.addView(body, bp);
        TextView contact = text(name, 16, NAVY, true);
        contact.setSingleLine(true);
        contact.setEllipsize(TextUtils.TruncateAt.END);
        body.addView(contact);
        String channelLabel = channel.contains("Telegram") ? "Telegram" :
            channel.contains("Whatsapp") || channel.contains("WhatsApp") ? "WhatsApp" : "Канал #" + inbox;
        String infoText = "#" + id + "  ·  " + channelLabel + "  ·  " + displayStatus(status);
        TextView info = text(infoText, 12, SECONDARY, false);
        body.addView(info);
        TextView last = text(preview, 14, SECONDARY, false);
        last.setMaxLines(2);
        last.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(4);
        body.addView(last, lp);
        if (!agent.isBlank() || (labels != null && labels.length() > 0)) {
            String label = labels != null && labels.length() > 0 ? labels.optString(0, "") : "";
            String metaText = (agent.isBlank() ? "" : "👤 " + agent) +
                (label.isBlank() ? "" : (agent.isBlank() ? "" : " · ") + "🏷 " + label);
            if (!metaText.isBlank()) {
                TextView metaTextView = text(metaText, 11, SECONDARY, false);
                LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
                mp.topMargin = dp(5);
                body.addView(metaTextView, mp);
            }
        }
        LinearLayout right = column();
        right.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(right, new LinearLayout.LayoutParams(-2, -2));
        if (timestamp > 0) {
            TextView at = text(stamp(timestamp), 11, SECONDARY, false);
            at.setGravity(Gravity.RIGHT);
            right.addView(at);
        }
        if (unread > 0) {
            TextView count = text(Integer.toString(unread), 12, Color.WHITE, true);
            count.setGravity(Gravity.CENTER);
            count.setBackground(bg(GREEN, 18));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(31), dp(31));
            cp.leftMargin = dp(8);
            cp.topMargin = dp(8);
            right.addView(count, cp);
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
            .setTitle("Подключение и уведомления")
            .setMessage("Сервер: " + credentials.server() + "\\nАккаунт: #" + account +
                "\\n\\n" + PushBridge.status(this) +
                "\\n\\nВерсия 0.6.0 (сборка 5). Автообновление списка и переписки: каждые 15 секунд.\nДля Push сервер Chatwoot должен иметь FIREBASE_PROJECT_ID и FIREBASE_CREDENTIALS.")
            .setPositiveButton("Закрыть", (d, w) -> d.dismiss())
            .setNeutralButton("Проверить Push", (d, w) -> {
                requestPushPermission();
                PushBridge.onLogin(this);
                showError("Повторно проверяем Firebase и регистрацию Chatwoot");
            })
            .setNegativeButton("Сменить подключение", (d, w) -> {
                PushBridge.unregisterBeforeLogout(this);
                credentials.clear();
                api = null;
                showLogin("");
            })
            .show();
    }

    private void requestPushPermission() {
        if (!PushBridge.isConfigured(this)) return;
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{ Manifest.permission.POST_NOTIFICATIONS }, NOTIFICATION_PERMISSION);
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openPush(intent);
    }

    /** Open the specific native conversation from Chatwoot's FCM data payload. */
    private void openPush(Intent intent) {
        if (intent == null || api == null) return;
        int id = PushBridge.conversationFromPayload(intent.getStringExtra("payload"), account);
        if (id > 0) {
            intent.removeExtra("payload"); // avoid reopening after rotation
            showChat(id, "Диалог #" + id, "open");
        }
    }

    private void showChat(int id, String name, String status) {
        if (sending) { showError("Дождись завершения отправки"); return; }
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
        statusButton = button("Статус: " + displayStatus(status) + " ▾", false);
        statusButton.setTextSize(12);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(165), dp(44));
        sp.leftMargin = dp(8);
        actions.addView(statusButton, sp);
        statusButton.setOnClickListener(v -> chooseStatus());
        Button more = button("⋮", false);
        more.setContentDescription("Действия Chatwoot: оператор, приоритет, готовые ответы");
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        moreParams.leftMargin = dp(7);
        actions.addView(more, moreParams);
        more.setOnClickListener(v ->
            ChatActions.show(this, api, currentConversation, currentName, draft, () -> loadMessages(true)));
        header.addView(actions);
        root.addView(header);

        messageScroll = new ScrollView(this);
        messageScroll.setFillViewport(true);
        messageScroll.setBackgroundColor(Color.rgb(246, 248, 251));
        messageContainer = column();
        messageContainer.setPadding(dp(12), dp(14), dp(12), dp(16));
        messageScroll.addView(messageContainer);
        root.addView(messageScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // Same mental model as desktop Chatwoot: Reply/private-note tabs, rich
        // composer, attachment tray and a separate explicit Send action.
        LinearLayout composer = column();
        composer.setPadding(dp(12), dp(7), dp(12), dp(9));
        composer.setBackgroundColor(Color.WHITE);
        LinearLayout mode = row();
        privateNote = new CheckBox(this);
        replyMode = button("Ответ", true);
        noteMode = button("Заметка 🔒", false);
        mode.addView(replyMode, new LinearLayout.LayoutParams(0, dp(36), 1));
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, dp(36), 1);
        np.leftMargin = dp(6);
        mode.addView(noteMode, np);
        replyMode.setOnClickListener(v -> setComposerMode(false));
        noteMode.setOnClickListener(v -> setComposerMode(true));
        composer.addView(mode);
        attachmentTray = column();
        composer.addView(attachmentTray);
        recordIndicator = text("", 13, Color.rgb(190, 40, 55), true);
        recordIndicator.setVisibility(View.GONE);
        composer.addView(recordIndicator);
        draft = field("Написать ответ…", "", false);
        draft.setSingleLine(false);
        draft.setMinLines(2);
        draft.setMaxLines(5);
        draft.setGravity(Gravity.TOP | Gravity.LEFT);
        draft.setPadding(dp(12), dp(10), dp(12), dp(8));
        draft.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams dpLayout = new LinearLayout.LayoutParams(-1, -2);
        dpLayout.topMargin = dp(6);
        composer.addView(draft, dpLayout);
        LinearLayout write = row();
        attachButton = button("＋", false);
        attachButton.setContentDescription("Прикрепить фото, видео или файлы");
        write.addView(attachButton, new LinearLayout.LayoutParams(dp(42), dp(44)));
        attachButton.setOnClickListener(v -> chooseAttachmentType());
        micButton = button("🎙", false);
        micButton.setContentDescription("Записать голосовое сообщение");
        LinearLayout.LayoutParams micParams = new LinearLayout.LayoutParams(dp(48), dp(44));
        micParams.leftMargin = dp(5);
        write.addView(micButton, micParams);
        micButton.setOnClickListener(v -> toggleRecording());
        Button snippets = button("⚡", false);
        snippets.setContentDescription("Готовые ответы и действия");
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(43), dp(44));
        cp.leftMargin = dp(5);
        write.addView(snippets, cp);
        snippets.setOnClickListener(v ->
            ChatActions.show(this, api, currentConversation, currentName, draft, () -> loadMessages(true)));
        View space = new View(this);
        write.addView(space, new LinearLayout.LayoutParams(0, 1, 1));
        sendButton = button("Отправить ➤", true);
        sendButton.setContentDescription("Отправить сообщение или вложения");
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(126), dp(44));
        sendParams.leftMargin = dp(6);
        write.addView(sendButton, sendParams);
        sendButton.setOnClickListener(v -> send());
        LinearLayout.LayoutParams wr = new LinearLayout.LayoutParams(-1, -2);
        wr.topMargin = dp(6);
        composer.addView(write, wr);
        root.addView(composer);
        loadMessages(true);
    }


    private void selectInbox() {
        if (api == null) return;
        final int request = generation;
        final ApiClient client = api;
        worker.execute(() -> {
            try {
                JSONArray found = client.inboxes();
                if (found == null) found = new JSONArray();
                ArrayList<String> names = new ArrayList<>();
                ArrayList<Integer> ids = new ArrayList<>();
                names.add("Все каналы"); ids.add(0);
                for (int i = 0; i < found.length(); i++) {
                    JSONObject item = found.optJSONObject(i);
                    if (item == null || item.optInt("id", 0) < 1) continue;
                    names.add(item.optString("name", "Канал #" + item.optInt("id")));
                    ids.add(item.optInt("id"));
                }
                main.post(() -> {
                    if (screen != Screen.LIST || api != client || isFinishing()) return;
                    new AlertDialog.Builder(this)
                        .setTitle("Входящие каналы")
                        .setItems(names.toArray(new String[0]), (d, which) -> {
                            selectedInbox = ids.get(which);
                            selectedInboxName = names.get(which);
                            page = 1;
                            showList();
                        }).setNegativeButton("Отмена", null).show();
                });
            } catch (Exception ex) { main.post(() -> showError(ex.getMessage())); }
        });
    }

    private void setComposerMode(boolean note) {
        if (sending || recorder != null || privateNote == null) return;
        privateNote.setChecked(note);
        replyMode.setBackground(bg(note ? PALE : BLUE, 8));
        replyMode.setTextColor(note ? BLUE : Color.WHITE);
        noteMode.setBackground(bg(note ? NOTE : PALE, 8));
        noteMode.setTextColor(note ? Color.rgb(133, 87, 17) : BLUE);
        draft.setBackground(bg(note ? NOTE : PALE, 10));
        draft.setHint(note ? "Заметка — видна только команде" : "Написать ответ…");
        sendButton.setText(note ? "Заметка ➤" : "Отправить ➤");
    }

    private void chooseAttachmentType() {
        if (sending || recorder != null) return;
        if (pendingAttachments.stream().anyMatch(a -> a.voice)) {
            showError("Сначала отправь или удали голосовое сообщение");
            return;
        }
        new AlertDialog.Builder(this).setTitle("Добавить вложение")
            .setItems(new String[]{"Фото и видео", "Документы и другие файлы"}, (d, which) -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                if (which == 0) intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                pickerConversation = currentConversation;
                try { startActivityForResult(intent, PICK_ATTACHMENTS); }
                catch (Exception ex) { showError("Не удалось открыть системный выбор файлов"); }
            }).setNegativeButton("Отмена", null).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_ATTACHMENTS) return;
        if (resultCode != RESULT_OK || data == null || screen != Screen.CHAT ||
            currentConversation != pickerConversation || sending) return;
        ArrayList<Uri> uris = new ArrayList<>();
        ClipData clips = data.getClipData();
        if (clips != null) {
            for (int i = 0; i < Math.min(clips.getItemCount(), AttachmentDraft.MAX_COUNT); i++)
                uris.add(clips.getItemAt(i).getUri());
        } else if (data.getData() != null) uris.add(data.getData());
        pickerConversation = -1;
        for (Uri uri : uris) {
            if (pendingAttachments.size() >= AttachmentDraft.MAX_COUNT) {
                showError("Не более 10 вложений в одном сообщении");
                break;
            }
            if (pendingAttachments.stream().anyMatch(item -> uri.equals(item.uri))) continue;
            try {
                AttachmentDraft item = AttachmentDraft.document(this, uri);
                long total = Math.max(0, item.size);
                for (AttachmentDraft old : pendingAttachments) total += Math.max(0, old.size);
                if (total > AttachmentDraft.MAX_TOTAL) {
                    showError("Общий размер вложений больше 80 МБ");
                    continue;
                }
                pendingAttachments.add(item);
            } catch (Exception ex) { showError(ex.getMessage()); }
        }
        updateAttachmentTray();
    }

    private void updateAttachmentTray() {
        if (attachmentTray == null) return;
        attachmentTray.removeAllViews();
        for (AttachmentDraft item : new ArrayList<>(pendingAttachments)) {
            LinearLayout entry = row();
            entry.setPadding(dp(9), dp(3), dp(5), dp(3));
            entry.setBackground(bg(PALE, 8));
            LinearLayout details = column();
            TextView title = text(item.voice ? "▶ Голосовое сообщение" : item.name, 13, NAVY, true);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            details.addView(title);
            details.addView(text(item.kind() + (item.displaySize().isEmpty() ? "" : " · " + item.displaySize()), 11, SECONDARY, false));
            entry.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
            if (item.voice && item.recording != null)
                details.setOnClickListener(v -> AttachmentMedia.play(this, item.recording.getAbsolutePath()));
            Button remove = button("✕", false);
            remove.setContentDescription("Удалить " + item.name);
            entry.addView(remove, new LinearLayout.LayoutParams(dp(38), dp(34)));
            remove.setEnabled(!sending && recorder == null);
            remove.setOnClickListener(v -> {
                if (sending || recorder != null) return;
                pendingAttachments.remove(item);
                item.discard();
                updateAttachmentTray();
            });
            LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
            layout.topMargin = dp(5);
            attachmentTray.addView(entry, layout);
        }
    }

    private void discardAttachments() {
        for (AttachmentDraft attachment : pendingAttachments) attachment.discard();
        pendingAttachments.clear();
    }

    private void toggleRecording() {
        if (sending) return;
        if (recorder != null) { finishRecording(true); return; }
        if (!pendingAttachments.isEmpty()) {
            showError("Голосовое отправляется отдельно от других вложений");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionConversation = currentConversation;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE_PERMISSION);
            return;
        }
        startRecording();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != MICROPHONE_PERMISSION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED &&
            screen == Screen.CHAT && currentConversation == permissionConversation && foreground)
            startRecording();
        else if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED)
            showError("Для голосовых сообщений нужен доступ к микрофону");
        permissionConversation = -1;
    }

    private void startRecording() {
        if (screen != Screen.CHAT || sending || recorder != null || !pendingAttachments.isEmpty()) return;
        try {
            recordingFile = File.createTempFile("crm-voice-", ".m4a", getCacheDir());
            recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setOutputFile(recordingFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recordStarted = SystemClock.elapsedRealtime();
            micButton.setText("■");
            micButton.setContentDescription("Остановить запись");
            attachButton.setEnabled(false);
            sendButton.setEnabled(false);
            replyMode.setEnabled(false);
            noteMode.setEnabled(false);
            recordIndicator.setVisibility(View.VISIBLE);
            main.post(recordingClock);
        } catch (Exception ex) {
            finishRecording(false);
            showError("Не удалось включить микрофон: " + ex.getMessage());
        }
    }

    private void finishRecording(boolean keep) {
        main.removeCallbacks(recordingClock);
        MediaRecorder active = recorder;
        File file = recordingFile;
        recorder = null;
        recordingFile = null;
        boolean valid = false;
        if (active != null) {
            try { active.stop(); valid = true; } catch (RuntimeException ignored) { }
            try { active.release(); } catch (RuntimeException ignored) { }
        }
        if (keep && valid && file != null && file.length() > 0 && screen == Screen.CHAT) {
            pendingAttachments.add(AttachmentDraft.voice(file));
        } else if (file != null) {
            file.delete();
            if (keep) showError("Слишком короткая запись");
        }
        if (micButton != null) {
            micButton.setText("🎙");
            micButton.setContentDescription("Записать голосовое сообщение");
        }
        if (recordIndicator != null) recordIndicator.setVisibility(View.GONE);
        setSendingUi(false);
        updateAttachmentTray();
    }

    private void setSendingUi(boolean active) {
        sending = active;
        if (sendButton != null) {
            sendButton.setEnabled(!active);
            sendButton.setText(active ? "Отправка…" :
                privateNote != null && privateNote.isChecked() ? "Заметка ➤" : "Отправить ➤");
        }
        if (draft != null) draft.setEnabled(!active);
        if (attachButton != null) attachButton.setEnabled(!active);
        if (micButton != null) micButton.setEnabled(!active);
        if (replyMode != null) replyMode.setEnabled(!active);
        if (noteMode != null) noteMode.setEnabled(!active);
        if (statusButton != null) statusButton.setEnabled(!active);
        updateAttachmentTray();
    }

    private void loadMessages(boolean indicate) {
        if (screen != Screen.CHAT || messageContainer == null || loadingMessages) return;
        final int id = currentConversation;
        final int request = generation;
        loadingMessages = true;
        if (indicate) {
            lastMessageFingerprint = "";
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
        String fingerprint = result.toString();
        if (fingerprint.equals(lastMessageFingerprint)) return;
        boolean firstRender = lastMessageFingerprint.isEmpty();
        boolean nearBottom = messageScroll == null || messageScroll.getChildAt(0) == null ||
            messageScroll.getScrollY() >= messageScroll.getChildAt(0).getHeight() - messageScroll.getHeight() - dp(120);
        lastMessageFingerprint = fingerprint;
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
        if (messageScroll != null && (firstRender || nearBottom))
            messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
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
        bubble.setBackground(bg(note ? NOTE : outgoing ? Color.rgb(222, 240, 255) : Color.WHITE, 15));
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
        if (screen != Screen.CHAT || draft == null || sendButton == null || sending || recorder != null) return;
        final String body = draft.getText().toString().trim();
        final ArrayList<AttachmentDraft> files = new ArrayList<>(pendingAttachments);
        if (body.isBlank() && files.isEmpty()) return;
        final int id = currentConversation;
        final int request = generation;
        final boolean note = privateNote != null && privateNote.isChecked();
        final ApiClient client = api;
        setSendingUi(true);
        worker.execute(() -> {
            try {
                JSONObject result = files.isEmpty() ? client.sendMessage(id, body, note) :
                    client.sendAttachments(getApplicationContext(), id, body, note, files);
                for (AttachmentDraft file : files) file.discard();
                main.post(() -> {
                    if (screen != Screen.CHAT || request != generation || currentConversation != id || isDestroyed()) return;
                    pendingAttachments.clear();
                    if (draft != null) draft.setText("");
                    setSendingUi(false);
                    loadMessages(false);
                    if ("failed".equals(result.optString("status", "")))
                        showError("Chatwoot принял сообщение, но канал не смог доставить его");
                });
            } catch (Exception ex) {
                main.post(() -> {
                    if (isDestroyed()) { for (AttachmentDraft file : files) file.discard(); return; }
                    if (screen != Screen.CHAT || request != generation || currentConversation != id) return;
                    setSendingUi(false);
                    new AlertDialog.Builder(this)
                        .setTitle("Отправка не подтверждена")
                        .setMessage(ex.getMessage() + "\n\nЧерновик и вложения сохранены. Проверь переписку перед повтором.")
                        .setPositiveButton("Понятно", null).show();
                });
            }
        });
    }

    private void chooseStatus() {
        if (screen != Screen.CHAT || statusButton == null) return;
        String[] options = {"Открыт", "Ожидание", "Закрыт"};
        String[] values = {"open", "pending", "resolved"};
        new AlertDialog.Builder(this)
            .setTitle("Статус диалога")
            .setSingleChoiceItems(options, currentStatus.equals("resolved") ? 2 :
                currentStatus.equals("pending") ? 1 : 0, (dialog, which) -> {
                dialog.dismiss();
                setConversationStatus(values[which]);
            })
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void setConversationStatus(String next) {
        if (screen != Screen.CHAT || currentConversation < 1 || statusButton == null) return;
        if (next.equals(currentStatus)) return;
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
                    statusButton.setText("Статус: " + displayStatus(next) + " ▾");
                    showError("Статус изменён: " + displayStatus(next));
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
        if (sending) { showError("Дождись завершения отправки"); return; }
        if (screen == Screen.CHAT && recorder != null) { finishRecording(false); return; }
        if (screen == Screen.CHAT && !pendingAttachments.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Удалить черновик с вложениями?")
                .setNegativeButton("Остаться", null)
                .setPositiveButton("Удалить", (d, w) -> showList()).show();
            return;
        }
        if (screen == Screen.CHAT) showList();
        else super.onBackPressed();
    }
}
