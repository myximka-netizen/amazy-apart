package ru.amazy.crm.nativeclient;

import org.json.JSONArray;
import org.json.JSONObject;

/** Synthetic screenshot fixtures only; no server access and no real customer data.
 * MainActivity enables them only when Android marks the app debuggable.
 */
final class UiPreview {
    private UiPreview() { }
    static JSONObject conversations() {
        JSONObject response=new JSONObject();JSONArray rows=new JSONArray();
        String[] names={"Анна Миронова","Алексей","Екатерина Волкова","Дмитрий К.","Мария Соколова","Ирина","Сергей Петров","Ольга К."};
        String[] texts={"Добрый день! Свободно с 23 по 26 ноября?","Спасибо, всё нашли. Очень удобно!","Можно заселиться немного раньше?","Отправил документы, проверьте, пожалуйста.","Нас будет двое взрослых и ребёнок","А есть апартаменты ближе к центру?","Спасибо за инструкцию!","Подскажите, где оставить ключи?"};
        try {
            long now=System.currentTimeMillis()/1000;
            for(int i=0;i<names.length;i++){
                JSONObject sender=new JSONObject().put("name",names[i]);
                JSONObject meta=new JSONObject().put("sender",sender).put("channel",i==3?"Channel::Whatsapp":"Channel::Telegram");
                if(i%2==0)meta.put("assignee",new JSONObject().put("name","Оператор"));
                JSONObject item=new JSONObject().put("id",24-i).put("status","open")
                    .put("inbox_id",1).put("unread_count",i%3==0?2:0)
                    .put("last_activity_at",now-(i+1)*600).put("meta",meta)
                    .put("last_non_activity_message",new JSONObject().put("content",texts[i]));
                if(i==1)item.put("labels",new JSONArray().put("заселение"));
                if(i==2)item.put("priority","high");
                rows.put(item);
            }
            response.put("data",new JSONObject().put("payload",rows));
        }catch(Exception ignored){}
        return response;
    }
    static JSONObject messages(){
        JSONObject response=new JSONObject();JSONArray rows=new JSONArray();
        String[] texts={"Добрый день! Свободны ли апартаменты с 23 по 26 ноября?",
            "Здравствуйте! Да, есть свободный вариант рядом с метро Динамо.",
            "Отлично! А можно посмотреть фотографии квартиры?",
            "Конечно. Сейчас пришлю фотографии и условия бронирования.",
            "Уточнить время заселения перед подтверждением."};
        try{
            long now=System.currentTimeMillis()/1000;
            for(int i=0;i<texts.length;i++){
                boolean outgoing=i%2==1||i==4;
                JSONObject sender=new JSONObject().put("name",outgoing?"Волшебно тут":"Анна Миронова");
                rows.put(new JSONObject().put("id",i+1).put("content",texts[i]).put("message_type",outgoing?1:0)
                    .put("private",i==4).put("sender",sender).put("created_at",now-((5-i)*180))
                    .put("status","read"));
            }
            response.put("payload",rows);
        }catch(Exception ignored){}
        return response;
    }
}
