package ru.amazy.crm.nativeclient;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Native Chatwoot actions for the current conversation. Never sends a canned reply automatically. */
final class ChatActions {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();

    private ChatActions() { }

    static void show(Activity activity, ApiClient api, int id, String fallbackName,
                     EditText composer, Runnable refresh) {
        String[] options = {"Информация о госте", "Назначить оператора",
            "Приоритет", "Готовые ответы", "Обновить переписку"};
        MobileSheet.show(activity,"Действия с диалогом",options,which -> {
                switch (which) {
                    case 0: details(activity, api, id, fallbackName); break;
                    case 1: agents(activity, api, id); break;
                    case 2: priority(activity, api, id); break;
                    case 3: canned(activity, api, composer); break;
                    case 4: refresh.run(); break;
                    default: break;
                }
            });
    }

    private static void notifyUser(Activity activity, String message) {
        activity.runOnUiThread(() -> {
            if (!activity.isFinishing()) Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        });
    }

    private static void details(Activity activity, ApiClient api, int id, String fallbackName) {
        IO.execute(() -> {
            try {
                JSONObject conversation = api.conversation(id);
                JSONObject meta = conversation.optJSONObject("meta");
                JSONObject contact = meta == null ? null : meta.optJSONObject("sender");
                if (contact == null && meta != null) contact = meta.optJSONObject("contact");
                JSONObject agent = meta == null ? null : meta.optJSONObject("assignee");
                String name = contact == null ? fallbackName : contact.optString("name", fallbackName);
                String email = contact == null ? "" : contact.optString("email", "");
                String phone = contact == null ? "" : contact.optString("phone_number", "");
                String assigned = agent == null ? "Не назначен" : agent.optString("name", "Не назначен");
                String info = "Гость: " + name + "\n" +
                    (phone.isBlank() ? "" : "Телефон: " + phone + "\n") +
                    (email.isBlank() ? "" : "Email: " + email + "\n") +
                    "Диалог: #" + id + "\n" +
                    "Канал: #" + conversation.optInt("inbox_id", 0) + "\n" +
                    "Оператор: " + assigned + "\n" +
                    "Статус: " + conversation.optString("status", "") + "\n" +
                    "Приоритет: " + conversation.optString("priority", "не указан");
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing())
                        new AlertDialog.Builder(activity).setTitle("Карточка гостя")
                            .setMessage(info).setPositiveButton("Закрыть", null).show();
                });
            } catch (Exception ex) { notifyUser(activity, ex.getMessage()); }
        });
    }

    private static void priority(Activity activity, ApiClient api, int id) {
        String[] names = {"Низкий", "Средний", "Высокий", "Срочный"};
        String[] values = {"low", "medium", "high", "urgent"};
        new AlertDialog.Builder(activity).setTitle("Приоритет диалога")
            .setItems(names, (d, which) -> IO.execute(() -> {
                try {
                    api.setPriority(id, values[which]);
                    notifyUser(activity, "Приоритет: " + names[which]);
                } catch (Exception ex) { notifyUser(activity, ex.getMessage()); }
            })).show();
    }

    private static void agents(Activity activity, ApiClient api, int id) {
        IO.execute(() -> {
            try {
                JSONArray agents = api.agents();
                if (agents == null) agents = new JSONArray();
                String[] names = new String[agents.length() + 1];
                int[] ids = new int[names.length];
                names[0] = "Без оператора";
                for (int i = 0; i < agents.length(); i++) {
                    JSONObject a = agents.optJSONObject(i);
                    names[i + 1] = a == null ? "Оператор #" + (i + 1) : a.optString("name", "Оператор");
                    ids[i + 1] = a == null ? -1 : a.optInt("id", -1);
                }
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    new AlertDialog.Builder(activity).setTitle("Назначить оператора")
                        .setItems(names, (d, which) -> {
                            if (ids[which] < 0) return;
                            IO.execute(() -> {
                                try {
                                    api.assign(id, ids[which]);
                                    notifyUser(activity, "Оператор: " + names[which]);
                                } catch (Exception ex) { notifyUser(activity, ex.getMessage()); }
                            });
                        }).setNegativeButton("Отмена", null).show();
                });
            } catch (Exception ex) { notifyUser(activity, ex.getMessage()); }
        });
    }

    private static void canned(Activity activity, ApiClient api, EditText composer) {
        IO.execute(() -> {
            try {
                JSONArray responses = api.cannedResponses();
                if (responses == null) responses = new JSONArray();
                JSONArray saved = responses;
                String[] names = new String[responses.length()];
                for (int i = 0; i < responses.length(); i++) {
                    JSONObject item = responses.optJSONObject(i);
                    String title = item == null ? "Ответ #" + (i + 1) :
                        "/" + item.optString("short_code", "") + " · " +
                        item.optString("content", "").replace('\n', ' ');
                    names[i] = title.length() > 100 ? title.substring(0, 97) + "…" : title;
                }
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    if (names.length == 0) {
                        notifyUser(activity, "Готовых ответов в Chatwoot пока нет");
                        return;
                    }
                    new AlertDialog.Builder(activity).setTitle("Готовые ответы")
                        .setItems(names, (d, which) -> {
                            JSONObject reply = saved.optJSONObject(which);
                            if (reply != null && composer != null) {
                                composer.setText(reply.optString("content", ""));
                                composer.setSelection(composer.length());
                            }
                        }).setNegativeButton("Отмена", null).show();
                });
            } catch (Exception ex) { notifyUser(activity, ex.getMessage()); }
        });
    }
}
