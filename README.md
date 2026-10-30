> **Небольшой проект для портирования.** Десктопное приложение → Android за пару вечеров:
> UI на Compose, тяжёлое ядро — Rust через JNI, сборка и релизы в GitHub Actions.
> Ставится сайдлоадом, обновляется само из Releases.

# Claude Code for Android

[Последний релиз](https://github.com/degradatione-cloud/claude-code-android/releases/latest) · `com.degradatione.claudecode` · Android 15+ (API 35)

Нативный порт десктопного Claude Code (Linux-сборка `claude-desktop` 4.29.0) на Android 15/16.

Что внутри:

- **Фирменные визуалы Claude**: шрифты Anthropic Sans/Serif и палитра вытащены из официального `.deb`
  (`app.asar` → woff2 → статические инстансы по весам), иконка — родная из пакета.
- **Реальные инструменты Claude Code** на телефоне: `Bash` (`/system/bin/sh`), `Read`, `Write`, `Edit`,
  `LS`, `Glob`, `Grep` — не имитация, а исполнение в рабочей папке приложения.
- **Потоковый агентский цикл** Anthropic Messages API (SSE + tool_use/tool_result), совместим с
  `api.anthropic.com`, токеном `claude setup-token` и любым совместимым relay'ем.
- **Мобильный UI**: ящик сессий, композер с клавиатурой/insets, длинный тап, диффы, разрешения
  на инструменты, всё под 48dp тач-таргеты.
- **Плавающая кнопка логов** — перетаскиваемая, с индикатором ошибок, живая консоль с фильтрами.
- **Автообновление**: приложение само проверяет GitHub Releases, качает APK, сверяет sha256 и
  открывает системный установщик.

## Сборка

`.github/workflows/build.yml` → release APK + GitHub Release `v<versionName>`.
Ключ подписи лежит в секрете `KEYSTORE_B64` (постоянный, чтобы обновления ставились поверх).

## Версии

`versionCode = major*10000 + minor*100 + patch` — та же формула, что в `Updater.versionCodeOf`,
поэтому сравнение с тегом релиза работает без ручных таблиц.
