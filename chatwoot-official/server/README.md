# Собственный Firebase и проверка на Honor

Понадобятся доступ к PowerShell на ПК с Docker, тестовый чат и Honor с установленным APK. Клиент уже настроен на Android-приложение `ru.amazy.crm.nativeclient` проекта `volshebno-tut-crm`. Серверный JSON сервисного аккаунта хранится у администратора. Настройка серверного Firebase действует для всей инсталляции Chatwoot.

Для push о каждом входящем сообщении в аккаунте 2 используйте [настройку уведомлений без назначения](incoming/README.md). Обновление Android-приложения для этого не требуется.

## Проверить сервер и регистрацию

На ПК, подключённом к Tailscale, выполните команды. Путь к проекту соответствует установленному стеку.

```powershell
Set-Location C:\chatwoot_amazy_start
$crmInfo = Invoke-RestMethod -Uri 'https://desktop-to6ives.taile101ff.ts.net/api'
$crmInfo.version
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$diag = Join-Path $env:TEMP 'amazy-check-push.rb'
$base = 'https://raw.githubusercontent.com/myximka-netizen/amazy-apart/chatwoot-official-android/chatwoot-official/server'
Invoke-WebRequest "$base/check_push.rb" -OutFile $diag
Get-Content -Raw -Encoding UTF8 $diag | docker compose exec -T sidekiq bundle exec rails runner -
```

На Honor запустите Tailscale, войдите в CRM и разрешите уведомления. Повторите последнюю команду. Она выводит версию сервера, наличие аккаунта 2, число доступных маршрутов мобильного API, совпадение проекта Firebase, номер подписки и оператора, время обновления и наличие токена. Сам токен, адрес почты, тексты сообщений и ключи в вывод не попадают. Чтобы выбрать нужную подписку при нескольких телефонах, сопоставьте оператора и время регистрации после входа на Honor.

## Настроить отправку через свой проект

1. В Google Cloud включите **Firebase Cloud Messaging API** для `volshebno-tut-crm`. Сервисному аккаунту нужна роль **Firebase Cloud Messaging API Admin** (`roles/firebasecloudmessaging.admin`) или разрешение `cloudmessaging.messages.create` в этом проекте.
2. При `firebase_project_matches=true` и `service_account_matches=true` переходите к доставке. Иначе откройте **Super Admin → App Config** или **Installation Configs** и задайте `FIREBASE_PROJECT_ID` равным `volshebno-tut-crm`, а `FIREBASE_CREDENTIALS` — полному JSON сервисного аккаунта. Конфиг Android `google-services.json` используется при сборке телефона. Для сервера нужен файл сервисного аккаунта с полем `private_key`.
3. Если поля скрыты, на ПК можно применить те же настройки через Rails. Укажите путь к собственному серверному ключу. В контейнер копируется только программа настройки, JSON передаётся ей через стандартный ввод. Скрипт проверяет проект и тип ключа, затем сохраняет оба значения одной транзакцией.

```powershell
$setup = Join-Path $env:TEMP 'amazy-configure-firebase.rb'
Invoke-WebRequest "$base/configure_firebase.rb" -OutFile $setup
docker compose cp $setup rails:/tmp/amazy-configure-firebase.rb
$keyPath = Read-Host 'Полный путь к JSON сервисного аккаунта Firebase'
Get-Content -Raw -Encoding UTF8 -LiteralPath $keyPath | docker compose exec -T rails bundle exec rails runner /tmp/amazy-configure-firebase.rb
Get-Content -Raw -Encoding UTF8 $diag | docker compose exec -T sidekiq bundle exec rails runner -
```

На сервере Chatwoot 4.19.1 при двух заполненных параметрах `Notification::PushNotificationService` использует собственный FCM HTTP v1. Фоновый процесс Sidekiq должен иметь исходящий HTTPS-доступ к `oauth2.googleapis.com` и `fcm.googleapis.com`. При работающем выборочном прокси проверьте именно этот маршрут. Изменение конфигурации через Rails сбрасывает кеш. Если конкретная версия использует значения из окружения, примените их к обоим сервисам `rails` и `sidekiq` с тем же набором Compose-файлов, которым запущен ваш стек.

## Проверить доставку на телефон

Сверните CRM кнопкой «Домой». Укажите `agent_id` и `subscription_id` строки с `android=true` из первой проверки. Из каталога Docker-проекта выполните команды. Один тест уйдёт только на выбранный телефон. В уведомлении и в выводе будет один и тот же шестизначный код из цифр и букв.

```powershell
$agentId = Read-Host 'agent_id вашего оператора'
$subscriptionId = Read-Host 'subscription_id вашего Honor'
curl.exe -fsSL "$base/check_push.rb" | docker compose exec -T -e CRM_PUSH_MODE=probe -e "CRM_AGENT_ID=$agentId" -e "CRM_SUBSCRIPTION_ID=$subscriptionId" sidekiq bundle exec rails runner -
```

`push_new_conversation_enabled` и `push_assigned_message_enabled` показывают настройки оператора. При `false` откройте в приложении **Настройки → Настройки уведомлений** и включите соответствующие события. Новое обращение должно поступить в канал, в котором оператор состоит. Для проверки следующего сообщения в существующем диалоге назначьте себе тестовый чат. При `FCM HTTP` сохраните код ошибки. `FCM accepted` означает, что Google принял запрос. Доставку подтверждает совпадение кода в шторке телефона.

## Проверить открытие диалога и входящие из Telegram

1. На ПК создайте или выберите **свой тестовый диалог** во входящем канале Telegram Business. Назначьте его оператору, под которым выполнен вход на Honor. В его настройках Chatwoot включите push о новых сообщениях в назначенных диалогах. На Honor проверьте разрешение приложения, канал «Сообщения», доступность Google Play Services и отображение уведомлений на экране блокировки.
2. На ПК задайте значения из диагностики и номер диалога из адреса `/app/accounts/2/conversations/НОМЕР`.

```powershell
$agentId = Read-Host 'agent_id вашего оператора'
$subscriptionId = Read-Host 'subscription_id вашего Honor'
$conversationId = Read-Host 'Номер тестового диалога'
Get-Content -Raw -Encoding UTF8 $diag | docker compose exec -T -e "CRM_AGENT_ID=$agentId" sidekiq bundle exec rails runner -
```

3. Сверните приложение кнопкой «Домой». Следующая команда отправит одно диагностическое уведомление на указанную подписку. Оно содержит случайный короткий код и номер тестового диалога. Проверка ограничена аккаунтом 2, выбранным оператором и назначенным ему диалогом.

```powershell
Get-Content -Raw -Encoding UTF8 $diag | docker compose exec -T -e CRM_PUSH_MODE=send -e "CRM_AGENT_ID=$agentId" -e "CRM_SUBSCRIPTION_ID=$subscriptionId" -e "CRM_CONVERSATION_ID=$conversationId" sidekiq bundle exec rails runner -
```

4. Сверьте код в строке `FCM accepted` с кодом в шторке Android. Нажмите уведомление и проверьте номер открытого диалога. Заблокируйте Honor, погасите экран, повторите команду и сверьте **новый** код после разблокировки. Тот же тест можно повторить после обычного закрытия приложения из списка недавних. Android «Принудительно остановить» отключает доставку до следующего запуска.
5. Завершите проверку **двумя настоящими входящими сообщениями** из другого тестового Telegram-аккаунта. Для первого сверните CRM, для второго заблокируйте экран. Для каждого зафиксируйте время сообщения, запись `notification_id` в диагностике оператора, время системного уведомления и номер диалога, открытого по нажатию. Это дополнительно проверяет Telegram, политику уведомлений Chatwoot, очередь Sidekiq и подписку в одной цепочке.
6. Если есть `FCM accepted`, а системного уведомления нет, проверьте на Honor разрешения, режим «Не беспокоить» и ограничения фоновой работы MagicOS. Если системное уведомление есть, а диалог не загружается, отдельно проверьте Tailscale и доступ к серверу с телефона. Если прямой тест успешен, а настоящий Telegram не вызвал push, проверьте назначение диалога, настройки оператора и задачу `Notification::PushNotificationJob` в Sidekiq.

| Наблюдение | Что подтверждено |
| --- | --- |
| Строка подписки с наличием токена | Устройство зарегистрировано в Chatwoot |
| `FCM accepted` с кодом | Google принял тест от серверного контейнера |
| Тот же код в системном уведомлении | Android получил и показал конкретный тест |
| Нажатие открыло ожидаемый номер | Переход из уведомления работает в проверенном состоянии |
| Реальное входящее из тестового Telegram прошло при двух состояниях экрана | Проверена рабочая цепочка доставки |

## Вложения Telegram

Android записывает голосовое в M4A с AAC и прикрепляет его к диалогу. Вид сообщения у гостя зависит от реализации канала. В серверной версии Chatwoot 4.19.1 Telegram-сервис передаёт фото, видео и аудио по ссылкам `attachment.download_url`, а документы — через multipart. Если текст и документы доходят, но медиа остаются только в CRM, проверьте, откуда Telegram может скачать вложение. Частный адрес Tailscale доступен участникам вашей сети. Для доставки медиа Telegram нужен доступ к самим файлам, например через ограниченные по времени ссылки объектного хранилища.

[Отправка через Firebase HTTP v1](https://firebase.google.com/docs/cloud-messaging/send/v1-api) · [Серверная реализация Chatwoot 4.19.1](https://github.com/chatwoot/chatwoot/blob/v4.19.1/app/services/notification/push_notification_service.rb) · [Вложения Telegram](https://github.com/chatwoot/chatwoot/blob/v4.19.1/app/services/telegram/send_attachments_service.rb)
