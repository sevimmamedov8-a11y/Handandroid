# HandARBrowser Android V11

Отдельная Android-версия HandARBrowser для телефона в VR-очках.

## Что исправлено в V11

- `MainActivity` теперь наследуется от `androidx.activity.ComponentActivity`, поэтому корректно удовлетворяет `LifecycleOwner`, который нужен `ProcessCameraProvider.bindToLifecycle(...)`.
- Для `MPImage` используются официальные Java-методы `getWidth()` и `getHeight()` вместо несуществующих `width()` / `height()`.
- GitHub Actions переведён на `android-actions/setup-android@v4` и Node 24-compatible actions.
- Runner закреплён на `ubuntu-24.04`, чтобы сборка не зависела от миграции `ubuntu-latest`.
- Gradle 9.6.0 + Android Gradle Plugin 9.4.0 + JDK 17 сохранены: это совместимая связка для AGP 9.4.
- Перед сборкой workflow выполняет preflight-проверки именно тех мест, на которых упал предыдущий билд.

## Сборка

1. Загрузить содержимое ZIP в отдельный GitHub-репозиторий.
2. Сделать commit и Push в `main`.
3. Открыть GitHub → Actions → `Build HandARBrowser Android`.
4. После зелёной галочки открыть Artifacts → `HandARBrowser-APK`.

Полную APK-сборку в этом окружении я не заявляю как локально протестированную: здесь нет полного Android SDK/Gradle окружения. Сам CI workflow содержит предсборочные проверки и затем реальную сборку на GitHub runner.
