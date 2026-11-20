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
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.Drawable;
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
    private static final int NAVY = Color.rgb(32, 32, 32);
    private static final int BLUE = Color.rgb(0, 145, 255);
    private static final int PALE = Color.rgb(246, 246, 246);
    private static final int BORDER = Color.rgb(235, 235, 235);
    private static final int SECONDARY = Color.rgb(126, 126, 126);
    private static final int GREEN = Color.rgb(22, 163, 74);
    private static final int AUTO_REFRESH_MS = 15000;
    private static final int NOTE = Color.rgb(255, 245, 211);

    private enum Screen { LOGIN, LIST, CHAT, INBOX, SETTINGS }
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
    private String currentAvatar = "";
    private String currentChannel = "";
    private LinearLayout composerPanel;
    private TextView chatTitle;
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
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root = column();
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() |
                    android.view.WindowInsets.Type.displayCutout());
                android.graphics.Insets keyboard = insets.getInsets(android.view.WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
                return insets;
            });
            root.requestApplyInsets();
        }
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
        t.setFontFeatureSettings("kern");
        t.setIncludeFontPadding(false);
        t.setTypeface(Typeface.create(strong ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private GradientDrawable outlined(int color, int radius, int stroke) {
        GradientDrawable d = bg(color, radius);
        if (stroke != Color.TRANSPARENT) d.setStroke(dp(1), stroke);
        return d;
    }

    private Drawable ripple(int color, int radius, int stroke) {
        return new RippleDrawable(ColorStateList.valueOf(0x16000000), outlined(color, radius, stroke),
            bg(Color.WHITE, radius));
    }

    private Button button(String s, boolean primary) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(14);
        b.setAllCaps(false);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setIncludeFontPadding(false);
        b.setGravity(Gravity.CENTER);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setStateListAnimator(null);
        b.setElevation(0);
        b.setTextColor(primary ? Color.WHITE : NAVY);
        b.setBackground(ripple(primary ? BLUE : PALE, 9, Color.TRANSPARENT));
        return b;
    }

    private void setGlyph(Button b, String kind, int color) {
        if (b == null) return;
        b.setText("");
        b.setPadding(0, 0, 0, 0);
        MobileIcon icon = new MobileIcon(kind, color, dp(22));
        // Use a full-width centered drawable instead of an emoji glyph or an old-style button.
        b.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        // Android places the drawable at content left; horizontal padding centers it.
        b.post(() -> {
            int pad = Math.max(0, (b.getWidth() - dp(22)) / 2);
            b.setPadding(pad, 0, pad, 0);
        });
    }

    private Button glyph(String kind, String accessibility, boolean filled) {
        Button b = button("", filled);
        b.setBackground(ripple(filled ? BLUE : Color.TRANSPARENT, 24, Color.TRANSPARENT));
        b.setContentDescription(accessibility);
        setGlyph(b, kind, filled ? Color.WHITE : NAVY);
        return b;
    }

    private void separator(LinearLayout parent) {
        View v = new View(this);
        v.setBackgroundColor(BORDER);
        parent.addView(v, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private FrameLayout avatar(String name, String url, int size, String channel) {
        FrameLayout wrapper = new FrameLayout(this);
        int tint = Color.rgb(230, 238, 249);
        int[] tones = {0xffe3edf9,0xffe7eee3,0xfff4e6dc,0xffede5f6,0xfff3e7ee};
        if (name != null) tint = tones[Math.floorMod(name.hashCode(), tones.length)];
        FrameLayout circle = new FrameLayout(this);
        circle.setBackground(bg(tint, size / 2));
        circle.setClipToOutline(true);
        String initials = "";
        if (name != null) for (String part : name.trim().split("\\s+")) {
            if (!part.isEmpty()) initials += part.substring(0, 1);
            if (initials.length() >= 2) break;
        }
        TextView fallback = text(initials.toUpperCase(Locale.ROOT), size > 40 ? 16 : 12, 0xff536477, true);
        fallback.setGravity(Gravity.CENTER);
        circle.addView(fallback, new FrameLayout.LayoutParams(-1, -1));
        if (AttachmentMedia.isHttps(url == null ? "" : url)) {
            ImageView photo = new ImageView(this);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            circle.addView(photo, new FrameLayout.LayoutParams(-1, -1));
            AttachmentMedia.thumbnail(photo, url);
        }
        wrapper.addView(circle, new FrameLayout.LayoutParams(dp(size), dp(size)));
        if (size >= 44 && channel != null && !channel.isEmpty()) {
            String lower = channel.toLowerCase(Locale.ROOT);
            String mark = lower.contains("telegram") ? "➤" : lower.contains("whatsapp") ? "W" :
                lower.contains("email") ? "@" : lower.contains("api") ? "A" : "·";
            TextView badge = text(mark, 10, Color.WHITE, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(outlined(lower.contains("whatsapp") ? 0xff25a769 : 0xff0091e6, 12, Color.WHITE));
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(19), dp(19), Gravity.BOTTOM | Gravity.RIGHT);
            wrapper.addView(badge, bp);
        }
        return wrapper;
    }

    private EditText field(String hint, String value, boolean secret) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(outlined(PALE, 12, Color.TRANSPARENT));
        input.setTextColor(NAVY);
        input.setHintTextColor(SECONDARY);
        input.setIncludeFontPadding(false);
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
        composerPanel = null;
        chatTitle = null;
        attachmentTray = null;
        recordIndicator = null;
        attachButton = null;
        micButton = null;
        replyMode = null;
        noteMode = null;
        root.removeAllViews();
        // Preserve safe area padding while changing screens.
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

        LinearLayout top = row();
        top.setPadding(dp(9), dp(2), dp(9), dp(2));
        Button search = glyph("search", "Поиск по сообщениям", false);
        top.addView(search, new LinearLayout.LayoutParams(dp(46), dp(46)));
        search.setOnClickListener(v -> showSearch());
        TextView title = text("Диалоги", 17, NAVY, true);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button filter = glyph("filter", "Фильтры диалогов", false);
        top.addView(filter, new LinearLayout.LayoutParams(dp(46), dp(46)));
        filter.setOnClickListener(v -> showFilterMenu());
        root.addView(top);

        // As on Chatwoot mobile, filters are hidden in the toolbar by default.
        if (!"all".equals(assigneeType) || !"open".equals(filterStatus) || selectedInbox > 0 || !query.isEmpty()) {
            LinearLayout active = row();
            active.setPadding(dp(16), 0, dp(16), dp(6));
            String scope = "me".equals(assigneeType) ? "Мои" : "unassigned".equals(assigneeType) ? "Без оператора" : "Все";
            TextView description = text(scope + " · " + ("all".equals(filterStatus) ? "Все статусы" : displayStatus(filterStatus))
                + (selectedInbox > 0 ? " · " + selectedInboxName : "") +
                (!query.isEmpty() ? " · «" + query + "»" : ""), 12, SECONDARY, false);
            description.setSingleLine(true); description.setEllipsize(TextUtils.TruncateAt.END);
            active.addView(description, new LinearLayout.LayoutParams(0, dp(32), 1));
            Button reset = glyph("close", "Сбросить фильтры и поиск", false);
            reset.setOnClickListener(v -> { filterStatus = "open"; assigneeType = "all"; selectedInbox = 0;
                selectedInboxName = "Все каналы"; query = ""; page = 1; showList(); });
            active.addView(reset, new LinearLayout.LayoutParams(dp(38), dp(32)));
            root.addView(active);
        }
        syncLabel = text("", 12, 0xffae4728, false);
        syncLabel.setPadding(dp(16),dp(6),dp(16),dp(6));
        syncLabel.setVisibility(View.GONE);
        root.addView(syncLabel);
        separator(root);
        conversationScroll = new ScrollView(this);
        conversationScroll.setFillViewport(true);
        conversationScroll.setVerticalScrollBarEnabled(false);
        conversationContainer = column();
        conversationContainer.setBackgroundColor(Color.WHITE);
        conversationScroll.addView(conversationContainer);
        root.addView(conversationScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        addBottomNav(root, "chat");
        loadConversations(true);
    }

    private void showSearch() {
        EditText edit = field("Поиск по сообщениям", query, false);
        edit.setSingleLine(true);
        edit.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        new AlertDialog.Builder(this).setTitle("Поиск").setView(edit)
            .setPositiveButton("Найти", (d,w) -> { query = edit.getText().toString().trim(); page = 1; showList(); })
            .setNeutralButton("Очистить", (d,w) -> { query = ""; page = 1; showList(); })
            .setNegativeButton("Отмена", null).show();
    }

    private void showFilterMenu() {
        String scope = "me".equals(assigneeType) ? "Мои" : "unassigned".equals(assigneeType) ? "Без оператора" : "Все";
        String state = "all".equals(filterStatus) ? "Все" : displayStatus(filterStatus);
        MobileSheet.show(this,"Фильтры диалогов",
            new String[]{"Статус · "+state, "Ответственный · "+scope, "Канал · "+selectedInboxName,
                "Обновить список", "Сбросить фильтры"}, w -> {
                if (w == 0) {
                    String[] labels = {"Открытые", "Ожидание", "Отложенные", "Закрытые", "Все"};
                    String[] values = {"open", "pending", "snoozed", "resolved", "all"};
                    for(int i=0;i<labels.length;i++)if(filterStatus.equals(values[i]))labels[i]="✓   "+labels[i];
                    MobileSheet.show(this,"Статус",labels,i->{filterStatus=values[i];page=1;showList();});
                } else if (w == 1) {
                    String[] values = {"me", "unassigned", "all"};
                    String[] labels={"Мои","Без оператора","Все"};
                    for(int i=0;i<labels.length;i++)if(assigneeType.equals(values[i]))labels[i]="✓   "+labels[i];
                    MobileSheet.show(this,"Ответственный",labels,i->{assigneeType=values[i];page=1;showList();});
                } else if (w == 2) selectInbox();
                else if (w == 3) loadConversations(true);
                else { filterStatus="open";assigneeType="all";selectedInbox=0;
                    selectedInboxName="Все каналы";query="";page=1;showList(); }
            });
    }

    private void addBottomNav(LinearLayout parent, String selection) {
        separator(parent);
        LinearLayout menu = row();
        menu.setPadding(dp(15), dp(3), dp(15), dp(5));
        addNavItem(menu, "inbox", "Входящие", selection.equals("inbox"), this::showInbox);
        addNavItem(menu, "chat", "Диалоги", selection.equals("chat"), this::showList);
        addNavItem(menu, "settings", "Настройки", selection.equals("settings"), this::showSettings);
        parent.addView(menu, new LinearLayout.LayoutParams(-1, dp(62)));
    }

    private void addNavItem(LinearLayout menu, String icon, String title, boolean active, Runnable action) {
        LinearLayout cell = column();
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(ripple(Color.WHITE, 12, Color.TRANSPARENT));
        ImageView picture = new ImageView(this);
        picture.setImageDrawable(new MobileIcon(active && icon.equals("chat") ? "chat-filled" : icon,
            active ? NAVY : SECONDARY, dp(24)));
        cell.addView(picture, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView label = text(title, 10, active ? NAVY : SECONDARY, active);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(20));
        lp.topMargin = dp(2);
        cell.addView(label, lp);
        menu.addView(cell, new LinearLayout.LayoutParams(0, -1, 1));
        cell.setContentDescription(title);
        cell.setOnClickListener(v -> action.run());
    }

    private void showInbox() {
        if (api == null || sending) return;
        screen = Screen.INBOX;
        currentConversation = -1;
        clear();
        LinearLayout top = row();
        top.setPadding(dp(9), dp(2), dp(9), dp(2));
        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(dp(46), dp(46)));
        TextView title = text("Входящие", 17, NAVY, true);
        title.setGravity(Gravity.CENTER);
        top.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button refresh = glyph("refresh", "Обновить уведомления", false);
        refresh.setOnClickListener(v -> showInbox());
        top.addView(refresh, new LinearLayout.LayoutParams(dp(46), dp(46)));
        root.addView(top);
        separator(root);
        ScrollView scroll = new ScrollView(this);
        LinearLayout itemsView = column();
        scroll.addView(itemsView);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        addBottomNav(root, "inbox");
        TextView loading = text("Загрузка…", 14, SECONDARY, false);
        loading.setGravity(Gravity.CENTER);
        itemsView.addView(loading, new LinearLayout.LayoutParams(-1, dp(100)));
        int request = generation;
        ApiClient client = api;
        worker.execute(() -> {
            try {
                JSONObject result = client.notifications();
                JSONObject data = result.optJSONObject("data");
                JSONArray found = data == null ? null : data.optJSONArray("payload");
                main.post(() -> {
                    if (screen != Screen.INBOX || request != generation) return;
                    itemsView.removeAllViews();
                    if (found == null || found.length() == 0) {
                        TextView empty = text("Нет уведомлений", 15, SECONDARY, false);
                        empty.setGravity(Gravity.CENTER);
                        itemsView.addView(empty, new LinearLayout.LayoutParams(-1, dp(140)));
                        return;
                    }
                    for (int i=0; i<found.length(); i++) {
                        JSONObject item = found.optJSONObject(i);
                        if (item == null) continue;
                        JSONObject actor = item.optJSONObject("primary_actor");
                        int id = actor == null ? -1 : actor.optInt("display_id", actor.optInt("id", -1));
                        JSONObject meta = actor == null ? null : actor.optJSONObject("meta");
                        JSONObject sender = meta == null ? null : meta.optJSONObject("sender");
                        String name = sender == null ? "Диалог #" + id : sender.optString("name", "Диалог #" + id);
                        String status = actor == null ? "open" : actor.optString("status", "open");
                        LinearLayout entry = row();
                        entry.setPadding(dp(16),dp(13),dp(16),dp(13));
                        entry.setBackground(ripple(item.isNull("read_at") ? 0xfff2f8ff : Color.WHITE, 8,Color.TRANSPARENT));
                        entry.addView(avatar(name, sender == null ? "" : sender.optString("thumbnail",""), 42, ""),
                            new LinearLayout.LayoutParams(dp(44),dp(44)));
                        LinearLayout detail = column();
                        detail.setPadding(dp(12),0,0,0);
                        detail.addView(text(item.optString("push_message_title", "Уведомление"), 14, NAVY, true));
                        TextView body = text(item.optString("push_message_body",""), 13,SECONDARY,false);
                        body.setMaxLines(3); body.setEllipsize(TextUtils.TruncateAt.END);
                        detail.addView(body);
                        detail.addView(text(stamp(item.optLong("created_at",0)),11,SECONDARY,false));
                        entry.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
                        itemsView.addView(entry);
                        separator(itemsView);
                        if (id > 0 && "Conversation".equals(item.optString("primary_actor_type","Conversation")))
                            entry.setOnClickListener(v -> { currentAvatar = sender == null ? "" : sender.optString("thumbnail","");
                                currentChannel = ""; showChat(id,name,status); });
                    }
                });
            } catch(Exception ex) {
                main.post(() -> { if (screen == Screen.INBOX && request == generation) {
                    loading.setText("Не удалось загрузить уведомления. Нажми обновить.");
                }});
            }
        });
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
        if (syncLabel != null && lastListFingerprint.isEmpty()) syncLabel.setVisibility(View.GONE);
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
                    if (syncLabel != null) syncLabel.setVisibility(View.GONE);
                });
            } catch (Exception ex) {
                main.post(() -> {
                    if (screen != Screen.LIST || generation != request || conversationContainer == null) return;
                    listLoading = false;
                    if (syncLabel != null) {
                        syncLabel.setText("Нет связи · повтор через 15 сек.");
                        syncLabel.setVisibility(View.VISIBLE);
                    }
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
            if (page > 1) { Button previous = button("Вернуться на предыдущую страницу", false);
                previous.setOnClickListener(v -> {page--;showList();}); conversationContainer.addView(previous, new LinearLayout.LayoutParams(-1,dp(44))); }
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
            String preview = recent == null || recent.isNull("content") ? "" : recent.optString("content", "");
            if (preview.isBlank()) {
                JSONArray media = recent == null ? null : recent.optJSONArray("attachments");
                JSONObject first = media == null ? null : media.optJSONObject(0);
                String kind = first == null ? "" : first.optString("file_type");
                preview = "image".equals(kind) ? "Фото" : "video".equals(kind) ? "Видео" :
                    "audio".equals(kind) ? "Аудиосообщение" : "file".equals(kind) ? "Файл" : "Нет текста сообщения";
            }
            long ts = item.optLong("last_activity_at", 0L);
            String channel = meta == null ? "" : meta.optString("channel", "");
            JSONObject assignee = meta == null ? null : meta.optJSONObject("assignee");
            String agent = assignee == null ? "" : assignee.optString("name", "");
            JSONArray labels = item.optJSONArray("labels");
            String photo = sender == null ? "" : sender.optString("thumbnail", "");
            String agentPhoto = assignee == null ? "" : assignee.optString("thumbnail", "");
            String priority = item.optString("priority", "");
            addConversationRow(id, name, convStatus, preview, unread, inbox, ts, channel, agent, labels, photo, agentPhoto, priority);
        }
        if (page > 1 || items.length() >= 20) {
            LinearLayout paging = row();paging.setPadding(dp(14),dp(8),dp(14),dp(8));
            if(page>1){Button prev=button("Назад",false);prev.setOnClickListener(v->{page--;showList();});
                paging.addView(prev,new LinearLayout.LayoutParams(0,dp(36),1));}
            TextView number=text("Стр. "+page,12,SECONDARY,false);number.setGravity(Gravity.CENTER);
            paging.addView(number,new LinearLayout.LayoutParams(0,dp(36),1));
            if(items.length()>=20){Button next=button("Далее",false);next.setOnClickListener(v->{page++;showList();});
                paging.addView(next,new LinearLayout.LayoutParams(0,dp(36),1));}
            conversationContainer.addView(paging);
        }
    }

    private void addConversationRow(int id, String name, String status, String preview, int unread,
                                    int inbox, long timestamp, String channel, String agent, JSONArray labels,
                                    String photo, String agentPhoto, String priority) {
        LinearLayout cell = row();
        cell.setGravity(Gravity.TOP);
        cell.setPadding(dp(14),dp(13),dp(15),dp(13));
        cell.setBackground(ripple(Color.WHITE, 0, Color.TRANSPARENT));
        cell.setMinimumHeight(dp(77));
        cell.addView(avatar(name, photo, 48, channel), new LinearLayout.LayoutParams(dp(50),dp(50)));
        LinearLayout body = column();
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0,-2,1);
        bp.leftMargin = dp(10);
        cell.addView(body,bp);
        LinearLayout nameRow = row();
        TextView contact = text(name,15,NAVY,true);
        contact.setSingleLine(true); contact.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(contact,new LinearLayout.LayoutParams(0,dp(20),1));
        if ("urgent".equals(priority) || "high".equals(priority)) {
            TextView p = text("!",12,0xffe58a00,true); p.setGravity(Gravity.CENTER);
            nameRow.addView(p,new LinearLayout.LayoutParams(dp(15),dp(18)));
        }
        if (timestamp > 0) {
            TextView at = text(stamp(timestamp),12,SECONDARY,false);
            at.setGravity(Gravity.RIGHT);
            nameRow.addView(at,new LinearLayout.LayoutParams(-2,dp(20)));
        }
        body.addView(nameRow);
        LinearLayout msgRow = row();
        TextView last = text(preview,14, 0xff646464, false);
        last.setMaxLines(1); last.setEllipsize(TextUtils.TruncateAt.END);
        msgRow.addView(last,new LinearLayout.LayoutParams(0,dp(23),1));
        if (unread > 0) {
            TextView count = text(unread > 99 ? "99+" : Integer.toString(unread),11,Color.WHITE,true);
            count.setGravity(Gravity.CENTER);
            count.setBackground(bg(BLUE,12));
            LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(dp(unread > 99 ? 30 : 23),dp(22));
            c.leftMargin=dp(8);msgRow.addView(count,c);
        }
        body.addView(msgRow);
        LinearLayout meta = row();
        TextView statusText = text("#"+id+"  ·  "+displayStatus(status),11,SECONDARY,false);
        statusText.setMaxLines(1); statusText.setEllipsize(TextUtils.TruncateAt.END);
        meta.addView(statusText,new LinearLayout.LayoutParams(0,dp(19),1));
        if(labels!=null && labels.length()>0){
            String value=labels.optString(0,"");
            if(!value.isBlank()){
                TextView tag=text(value,10,0xff696969,false);
                tag.setSingleLine(true);tag.setEllipsize(TextUtils.TruncateAt.END);
                tag.setMaxWidth(dp(92));tag.setPadding(dp(6),0,dp(6),0);
                tag.setBackground(bg(PALE,6));
                meta.addView(tag,new LinearLayout.LayoutParams(-2,dp(18)));
            }
        }
        if(!agent.isBlank()) {
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(20),dp(20));ap.leftMargin=dp(6);
            meta.addView(avatar(agent,agentPhoto,20,""),ap);
        }
        body.addView(meta);
        conversationContainer.addView(cell);
        View hairline=new View(this);hairline.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,dp(1));hp.leftMargin=dp(74);
        conversationContainer.addView(hairline,hp);
        cell.setOnClickListener(v -> { currentAvatar = photo; currentChannel = channel; showChat(id,name,status); });
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
        if (sending || api == null) return;
        screen = Screen.SETTINGS;
        currentConversation = -1;
        clear();
        LinearLayout header = row();
        header.setPadding(dp(12),dp(2),dp(12),dp(3));
        TextView title = text("Настройки",17,NAVY,true);
        title.setGravity(Gravity.CENTER);
        header.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        root.addView(header);
        separator(root);
        ScrollView scroll=new ScrollView(this);
        LinearLayout content=column();
        content.setPadding(dp(16),dp(18),dp(16),dp(18));
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout profile=row();
        profile.setPadding(dp(14),dp(14),dp(14),dp(14));
        profile.setBackground(outlined(Color.WHITE,12,BORDER));
        profile.addView(avatar("Волшебно тут","",48,""),new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout identity=column();
        identity.setPadding(dp(12),0,0,0);
        identity.addView(text("Волшебно тут",16,NAVY,true));
        identity.addView(text("Рабочее пространство · #" + account,13,SECONDARY,false));
        profile.addView(identity,new LinearLayout.LayoutParams(0,-2,1));
        content.addView(profile);
        paddingView(content,24);
        content.addView(text("ПОДКЛЮЧЕНИЕ",11,SECONDARY,true));
        paddingView(content,9);
        settingsRow(content,"Сервер Chatwoot",credentials.server(),null);
        settingsRow(content,"Аккаунт","#"+account,null);
        settingsRow(content,"Синхронизация","Автоматически каждые 15 секунд",null);
        paddingView(content,22);
        content.addView(text("УВЕДОМЛЕНИЯ",11,SECONDARY,true));
        paddingView(content,9);
        settingsRow(content,"Push-уведомления",PushBridge.status(this),()->{
            requestPushPermission();PushBridge.onLogin(this);
            showError("Регистрация FCM повторно запущена");
        });
        settingsRow(content,"Разрешения Android","Настройки уведомлений приложения",()->{
            try {
                Intent settings = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                settings.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());
                startActivity(settings);
            }catch(Exception e){showError("Открой настройки уведомлений Android");}
        });
        paddingView(content,24);
        content.addView(text("ПРИЛОЖЕНИЕ",11,SECONDARY,true));
        paddingView(content,9);
        settingsRow(content,"Версия","0.7.0 · сборка 6",null);
        settingsRow(content,"Сменить подключение","Выйти из текущего аккаунта",()->{
            new AlertDialog.Builder(this).setTitle("Выйти из аккаунта?")
                .setMessage("Переписки останутся на сервере. Для повторного входа понадобится API-токен.")
                .setNegativeButton("Отмена",null).setPositiveButton("Выйти",(d,w)->{
                    PushBridge.unregisterBeforeLogout(this);credentials.clear();api=null;
                    selectedInbox=0;selectedInboxName="Все каналы";query="";page=1;
                    showLogin("");
                }).show();
        });
        addBottomNav(root,"settings");
    }

    private void settingsRow(LinearLayout parent,String name,String subtitle,Runnable action) {
        LinearLayout line=row();line.setPadding(dp(13),dp(12),dp(13),dp(12));
        line.setBackground(outlined(Color.WHITE,10,BORDER));
        LinearLayout detail=column();
        detail.addView(text(name,14,NAVY,true));
        TextView sub=text(subtitle,12,SECONDARY,false);
        sub.setPadding(0,dp(4),0,0);detail.addView(sub);
        line.addView(detail,new LinearLayout.LayoutParams(0,-2,1));
        if(action!=null){
            ImageView arrow=new ImageView(this);arrow.setImageDrawable(new MobileIcon("chevron",SECONDARY,dp(20)));
            arrow.setRotation(-90);
            line.addView(arrow,new LinearLayout.LayoutParams(dp(20),dp(20)));
            line.setOnClickListener(v->action.run());
        }
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(6);
        parent.addView(line,p);
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
            currentAvatar = ""; currentChannel = "";
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
        LinearLayout header = row();
        header.setPadding(dp(4),dp(3),dp(8),dp(5));
        Button back = glyph("back","Назад к диалогам",false);
        back.setOnClickListener(v -> onBackPressed());
        header.addView(back,new LinearLayout.LayoutParams(dp(42),dp(48)));
        header.addView(avatar(name,currentAvatar,38,""),new LinearLayout.LayoutParams(dp(38),dp(38)));
        chatTitle=text(name,16,NAVY,true);
        chatTitle.setSingleLine(true);chatTitle.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,dp(48),1);tp.leftMargin=dp(9);
        header.addView(chatTitle,tp);
        Button more=glyph("more","Действия с диалогом",false);
        more.setOnClickListener(v -> ChatActions.show(this,api,currentConversation,currentName,draft,()->loadMessages(true)));
        header.addView(more,new LinearLayout.LayoutParams(dp(44),dp(48)));
        root.addView(header);
        LinearLayout sub=row();sub.setPadding(dp(15),0,dp(15),dp(9));
        String ch=currentChannel.toLowerCase(Locale.ROOT);
        String channelName=ch.contains("telegram")?"Telegram":ch.contains("whatsapp")?"WhatsApp":
            ch.contains("email")?"Email":"Диалог #"+id;
        TextView channelChip=text(channelName,12,SECONDARY,false);
        channelChip.setPadding(dp(9),0,dp(9),0);
        channelChip.setBackground(outlined(Color.WHITE,8,BORDER));
        sub.addView(channelChip,new LinearLayout.LayoutParams(-2,dp(31)));
        View stretch=new View(this);sub.addView(stretch,new LinearLayout.LayoutParams(0,1,1));
        statusButton=button(status.equals("resolved")?"Открыть": "Закрыть",false);
        statusButton.setTextSize(12);
        statusButton.setBackground(ripple(0xfff0f3f3,8,Color.TRANSPARENT));
        statusButton.setOnClickListener(v -> chooseStatus());
        statusButton.setText(status.equals("open")?"Закрыть ✓":displayStatus(status)+" ▾");
        sub.addView(statusButton,new LinearLayout.LayoutParams(-2,dp(32)));
        root.addView(sub);
        separator(root);

        messageScroll = new ScrollView(this);
        messageScroll.setFillViewport(true);
        messageScroll.setBackgroundColor(Color.WHITE);
        messageScroll.setVerticalScrollBarEnabled(false);
        messageContainer = column();
        messageContainer.setPadding(dp(12), dp(10), dp(12), dp(12));
        messageScroll.addView(messageContainer);
        root.addView(messageScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout composer=column();
        composer.setPadding(dp(10),dp(5),dp(10),dp(6));
        composer.setBackgroundColor(Color.WHITE);
        privateNote=new CheckBox(this);
        LinearLayout mode=row();
        replyMode=button("Ответ",false); noteMode=button("Личная заметка",false);
        replyMode.setTextSize(12); noteMode.setTextSize(12);
        replyMode.setPadding(dp(9),0,dp(9),0);noteMode.setPadding(dp(9),0,dp(9),0);
        replyMode.setBackground(ripple(PALE,7,Color.TRANSPARENT));
        noteMode.setBackground(ripple(Color.TRANSPARENT,7,Color.TRANSPARENT));
        noteMode.setTextColor(SECONDARY);
        mode.addView(replyMode,new LinearLayout.LayoutParams(-2,dp(30)));
        mode.addView(noteMode,new LinearLayout.LayoutParams(-2,dp(30)));
        replyMode.setOnClickListener(v -> setComposerMode(false));
        noteMode.setOnClickListener(v -> setComposerMode(true));
        composer.addView(mode);
        attachmentTray=column();
        composer.addView(attachmentTray);
        recordIndicator=text("",13,0xffbe2837,true);
        recordIndicator.setVisibility(View.GONE);composer.addView(recordIndicator);

        composerPanel=row();
        composerPanel.setGravity(Gravity.BOTTOM);
        composerPanel.setPadding(dp(4),dp(4),dp(4),dp(4));
        composerPanel.setBackground(outlined(PALE,22,BORDER));
        attachButton=glyph("plus","Прикрепить фото, видео или файлы",false);
        composerPanel.addView(attachButton,new LinearLayout.LayoutParams(dp(40),dp(40)));
        attachButton.setOnClickListener(v -> chooseAttachmentType());

        draft=field("Написать сообщение…","",false);
        draft.setTextSize(15);draft.setBackgroundColor(Color.TRANSPARENT);
        draft.setSingleLine(false);draft.setMinLines(1);draft.setMaxLines(5);
        draft.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);draft.setPadding(dp(4),dp(8),dp(2),dp(8));
        draft.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        composerPanel.addView(draft,new LinearLayout.LayoutParams(0,-2,1));
        Button snippets=glyph("bolt","Готовые ответы",false);
        snippets.setOnClickListener(v -> ChatActions.show(this,api,currentConversation,currentName,draft,()->loadMessages(true)));
        composerPanel.addView(snippets,new LinearLayout.LayoutParams(dp(36),dp(40)));
        micButton=glyph("mic","Записать голосовое сообщение",false);
        micButton.setOnClickListener(v -> toggleRecording());
        composerPanel.addView(micButton,new LinearLayout.LayoutParams(dp(36),dp(40)));
        sendButton=glyph("send","Отправить сообщение",true);
        sendButton.setOnClickListener(v -> send());
        composerPanel.addView(sendButton,new LinearLayout.LayoutParams(dp(40),dp(40)));
        LinearLayout.LayoutParams panel=new LinearLayout.LayoutParams(-1,-2);panel.topMargin=dp(5);
        composer.addView(composerPanel,panel);
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
                    MobileSheet.show(this,"Входящие каналы",names.toArray(new String[0]),which -> {
                            selectedInbox = ids.get(which);
                            selectedInboxName = names.get(which);
                            page = 1;
                            showList();
                        });
                });
            } catch (Exception ex) { main.post(() -> showError(ex.getMessage())); }
        });
    }

    private void setComposerMode(boolean note) {
        if (sending || recorder != null || privateNote == null) return;
        privateNote.setChecked(note);
        replyMode.setBackground(ripple(note?Color.TRANSPARENT:PALE,7,Color.TRANSPARENT));
        replyMode.setTextColor(note?SECONDARY:NAVY);
        noteMode.setBackground(ripple(note?0xffffefcb:Color.TRANSPARENT,7,Color.TRANSPARENT));
        noteMode.setTextColor(note?0xff93601f:SECONDARY);
        if(composerPanel!=null)composerPanel.setBackground(outlined(note?0xfffff8e8:PALE,22,note?0xfff8dfaa:BORDER));
        draft.setHint(note?"Видно только команде":"Написать сообщение…");
        sendButton.setBackground(ripple(note?0xffd28a23:BLUE,24,Color.TRANSPARENT));
    }

    private void chooseAttachmentType() {
        if (sending || recorder != null) return;
        if (pendingAttachments.stream().anyMatch(a -> a.voice)) {
            showError("Сначала отправь или удали голосовое сообщение");
            return;
        }
        MobileSheet.show(this,"Добавить вложение",new String[]{"Фото и видео", "Документы и другие файлы"},which -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                if (which == 0) intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                pickerConversation = currentConversation;
                try { startActivityForResult(intent, PICK_ATTACHMENTS); }
                catch (Exception ex) { showError("Не удалось открыть системный выбор файлов"); }
            });
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
            screen == Screen.CHAT && currentConversation == permissionConversation)
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
            setGlyph(micButton, "stop", 0xffbe2837);
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
            setGlyph(micButton, "mic", NAVY);
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
            sendButton.setAlpha(active ? 0.45f : 1f);
            sendButton.setContentDescription(active ? "Отправка…" : "Отправить сообщение");
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
        String previousDay = "";
        for (int i = 0; i < items.length(); i++) {
            JSONObject m = items.optJSONObject(i);
            if (m == null) continue;
            int type = m.optInt("message_type", 0);
            boolean note = m.optBoolean("private", false);
            String content = m.optString("content", "");
            JSONArray attachments = m.optJSONArray("attachments");
            if (type == 2 && content.isBlank()) continue;
            long created = m.optLong("created_at", 0);
            String day = created > 0 ? new SimpleDateFormat("dd MMMM yyyy", new Locale("ru")).format(new Date(created * 1000)) : "";
            if (!day.isEmpty() && !day.equals(previousDay)) {
                TextView date = text(day, 12, SECONDARY, true);
                date.setGravity(Gravity.CENTER);
                messageContainer.addView(date, new LinearLayout.LayoutParams(-1, dp(33)));
                previousDay = day;
            }
            JSONObject sender = m.optJSONObject("sender");
            String author = sender == null ? "" : sender.optString("name", "");
            String authorPhoto = sender == null ? "" : sender.optString("thumbnail", "");
            addMessageBubble(content, type, note, created, attachments, author, m.optString("status", ""), authorPhoto);
        }
        if (messageScroll != null && (firstRender || nearBottom))
            messageScroll.post(() -> messageScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void addMessageBubble(String content, int type, boolean note, long created, JSONArray attachments, String author, String status, String authorPhoto) {
        boolean outgoing = type == 1;
        if (type == 2 && !note) {
            TextView event = text(content, 12, SECONDARY, false);
            event.setGravity(Gravity.CENTER);
            event.setPadding(dp(12), dp(5), dp(12), dp(8));
            messageContainer.addView(event, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        LinearLayout line = row();
        line.setGravity(Gravity.LEFT | Gravity.BOTTOM);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, -2);
        lineParams.bottomMargin = dp(8);
        messageContainer.addView(line, lineParams);
        LinearLayout bubble = column();
        bubble.setPadding(dp(12), dp(9), dp(12), dp(8));
        bubble.setBackground(bg(note ? 0xffffefcf : outgoing ? BLUE : 0xffefefef, 16));
        String shownAuthor = author.isBlank() ? (outgoing ? "Оператор" : currentName) : author;
        line.addView(avatar(shownAuthor, authorPhoto, 25, ""),new LinearLayout.LayoutParams(dp(25),dp(25)));
        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(-2,-2);
        bubbleParams.leftMargin=dp(7);
        line.addView(bubble,bubbleParams);

        if (note) bubble.addView(text("🔒 Внутренняя заметка", 11, Color.rgb(154, 107, 20), true));
        else if (!author.isBlank()) bubble.addView(text(author, 11, outgoing ? 0xffd9efff : SECONDARY, true));
        if (!content.isBlank() && !"null".equals(content)) {
            TextView body = text(content, 15, outgoing && !note ? Color.WHITE : NAVY, false);
            body.setLineSpacing(dp(2),1f);
            body.setMaxWidth(Math.min(dp(290),getResources().getDisplayMetrics().widthPixels-dp(90)));
            body.setTextIsSelectable(true);
            bubble.addView(body);
        }
        if (attachments != null) {
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject attachment = attachments.optJSONObject(i);
                if (attachment == null) continue;
                String link = attachment.optString("data_url", "");
                if (!AttachmentMedia.isHttps(link)) link = attachment.optString("external_url", "");
                final String url = link;
                String kind = attachment.optString("file_type", "file");
                String label = "file".equals(kind) ? "Файл" : "image".equals(kind) ? "Фото" :
                    "video".equals(kind) ? "Видео" : "audio".equals(kind) ? "Аудиосообщение" : "Вложение";
                long size = attachment.optLong("file_size", 0);
                String suffix = size > 0 ? "  ·  " + (size >= 1048576 ?
                    String.format(Locale.US, "%.1f МБ", size / 1048576.0) : String.format(Locale.US, "%.0f КБ", size / 1024.0)) : "";
                if ("image".equals(kind)) {
                    ImageView image = new ImageView(this);
                    image.setBackground(bg(PALE, 8));
                    image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    image.setContentDescription("Открыть фото");
                    LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(224), dp(148));
                    ip.topMargin = dp(6);
                    bubble.addView(image, ip);
                    String thumb = attachment.optString("thumb_url", "");
                    AttachmentMedia.thumbnail(image, AttachmentMedia.isHttps(thumb) ? thumb : url);
                    image.setOnClickListener(v -> openAttachment(url));
                }
                String extension = attachment.optString("extension", "");
                Button open = button(("audio".equals(kind) ? "▶ " : "video".equals(kind) ? "▶ " : "📎 ") +
                    label + ("file".equals(kind) && !extension.isBlank() && !"null".equals(extension) ? " ." + extension : "") + suffix, false);
                open.setTextSize(12);
                LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(44));
                ap.topMargin = dp(5);
                bubble.addView(open, ap);
                open.setOnClickListener(v -> {
                    if ("audio".equals(kind)) AttachmentMedia.play(this, url);
                    else openAttachment(url);
                });
            }
        }
        if (created > 0) {
            String indicator = !outgoing || note ? "" : "failed".equals(status) ? "  ⚠ Не доставлено" :
                "read".equals(status) ? "  ✓✓" : "delivered".equals(status) ? "  ✓✓" : "sent".equals(status) ? "  ✓" : "";
            TextView at = text(stamp(created) + indicator, 10, "failed".equals(status) ? 0xffb4232c : outgoing && !note ? 0xffe3f2ff : SECONDARY, false);
            at.setGravity(Gravity.RIGHT);
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(-1, -2);
            timeParams.topMargin = dp(4);
            bubble.addView(at, timeParams);
        }
    }

    private void openAttachment(String url) {
        try {
            if (!AttachmentMedia.isHttps(url)) { showError("Ссылка на вложение недоступна по HTTPS"); return; }
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ex) { showError("Нет приложения для открытия вложения"); }
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
        int[] colors = {0xfff1f1f1, 0xffffefd7, 0xffe2f5ea};
        LinearLayout choices = column();
        choices.setPadding(dp(15),dp(7),dp(15),dp(12));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Статус диалога")
            .setView(choices).setNegativeButton("Отмена", null).create();
        LinearLayout group = row();
        for (int i=0;i<options.length;i++) {
            final int choice=i;
            Button card=button((currentStatus.equals(values[i])?"✓  ":"")+options[i],false);
            card.setTextSize(12);
            card.setPadding(dp(5),0,dp(5),0);
            card.setBackground(ripple(colors[i],12,currentStatus.equals(values[i])?BLUE:Color.TRANSPARENT));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(66),1);p.leftMargin=dp(3);p.rightMargin=dp(3);
            group.addView(card,p);
            card.setOnClickListener(v->{dialog.dismiss();setConversationStatus(values[choice]);});
        }
        choices.addView(group);
        dialog.show();
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
                    statusButton.setText(next.equals("open") ? "Закрыть ✓" : displayStatus(next) + " ▾");
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
        if (screen == Screen.CHAT || screen == Screen.INBOX || screen == Screen.SETTINGS) showList();
        else super.onBackPressed();
    }
}
