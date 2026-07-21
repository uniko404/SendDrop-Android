# 📡 SendDrop P2P for Android

[![Version](https://img.shields.io/badge/version-0.2.0--alpha-blue)](https://github.com/uniko404/SendDrop-Android/releases)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0+-brightgreen)](https://developer.android.com/)

**SendDrop P2P for Android** — децентрализованный обмен файлами по локальной сети без сервера. Устройства видят друг друга автоматически, файлы передаются напрямую. Никаких серверов, никаких настроек.

---

## ✨ Features

- 🚀 **Peer‑to‑peer** – каждый участник равноправен, нет единой точки отказа
- 📡 **Автоматическое обнаружение** – устройства видят друг друга в локальной сети
- 📁 **Отправка файлов** – выберите устройство и отправьте файл
- 📂 **Приём файлов** – автоматическое сохранение в `Download/SendDrop/`
- 🔒 **Без интернета** – всё работает по локальной сети
- 🌐 **Кроссплатформенность** – совместим с десктопной версией SendDrop P2P

---

## 📥 Download

[![Download](https://img.shields.io/badge/download-APK-green)](https://github.com/uniko404/SendDrop-Android/releases)

Скачайте APK из раздела [Releases](https://github.com/uniko404/SendDrop-Android/releases).

---

## 🚀 Quick Start

1. Установите APK на телефон
2. Откройте приложение – оно автоматически начнёт поиск устройств
3. Выберите устройство из списка
4. Нажмите **"📤 Отправить файл"** и выберите файл
5. Файл будет передан напрямую на выбранное устройство

---

## 🛠️ Build from Source

```bash
git clone https://github.com/uniko404/SendDrop-Android.git
cd SendDrop-Android
./gradlew assembleDebug
