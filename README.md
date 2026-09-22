# GoChat

A fast, clean Android chat client for [OpenCode Go](https://opencode.ai/docs/go/) — one subscription key, forty open models, on-device only.

<p align="center">
  <b>>_</b>
</p>

## Features

- **All Go models** — live model list from the Go gateway, grouped picker, per-model protocol routing (`/chat/completions`, `/responses`, `/messages`) with automatic fallback
- **Real streaming** — SSE token-by-token replies with thinking indicator and stop button
- **Full markdown** — headings, lists, quotes, links, tables, and fenced code with tinting + per-block copy
- **Multi-chat** — persistent conversations (Room), auto-titles, rename, delete, search, restore-on-launch
- **Message actions** — copy, share, edit & resend, regenerate, delete-from-here; interrupted replies resume with one tap
- **Attachments** — send code/docs as fenced context; images on vision models (auto-downscaled)
- **Cost controls** — context-window slider, temperature, custom system prompt, token + latency readout per reply
- **Private** — API key encrypted with AES-256/GCM in the AndroidKeyStore; backups disabled; zero analytics

## Build

```bash
./gradlew assembleDebug   # APK at app/build/outputs/apk/debug/app-debug.apk
```

Requires Android SDK 34, JDK 17.

## Usage

1. Subscribe to OpenCode Go at [opencode.ai/zen](https://opencode.ai/zen) and copy your API key.
2. Paste it in GoChat → Connect. The key is validated against `/v1/models` and stored encrypted.
3. Pick a model, chat. Chats stay on the device.

The app sends a stable `x-opencode-session` header per install so the gateway routes and caches correctly.

## License

MIT — see [LICENSE](LICENSE).
