# Android translation keyboard

**Status:** Approved for implementation

**Scope:** Android MVP; iOS follows after Android behavior and model quality are stable.

## Goal

Provide a normal Vietnamese keyboard with a button to switch into translation mode. Translate an unsent Vietnamese chat draft into casual English or Russian, let the user review it, and place it back in the chat composer. The user sends the message themselves.

## Recommended approach

- Implement a native Android IME with `InputMethodService`; Gboard cannot be extended as a third-party plugin.
- Normal mode is a usable Vietnamese QWERTY keyboard. Proposed input method is Telex; no prediction, swipe typing, or emoji catalog in the first MVP.
- A toolbar button switches between normal and translation modes. Translation mode keeps the keyboard available, offers English/Russian as the target, and has a casual tone by default.
- On an explicit Translate tap, read the current plain-text draft exposed by the focused app. Generate a preview locally. Apply replaces the draft; Cancel leaves it unchanged. Never send automatically. If the app does not expose the draft, tell the user and leave the text untouched.
- Do not translate password, numeric, or other secure fields. Keep draft and generated text in memory only; do not log, persist, or upload them.
- Run a compact multilingual model on-device through LiteRT-LM 0.17.1. Use `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` (344,671,744 bytes; SHA-256 `03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856`) as the first benchmark candidate, not a quality guarantee. Download model weights once from the public LiteRT Community Hugging Face repository; show download size/status and allow deleting the local model. Translation must work offline after download.
- Do not make Gemini Nano the translation engine in this MVP: Google currently documents Prompt API inference as foreground-only, which may prevent reliable use while an IME is active. Revisit only if a device proof confirms keyboard-service use works.

## User flow

1. User installs the app and enables/selects its keyboard in Android settings.
2. In a chat composer, user types Vietnamese in normal mode.
3. User taps the translate-mode control and selects English or Russian.
4. User taps Translate; the keyboard requests the current draft from the focused text field and runs the local model.
5. User reviews the preview, applies it to the composer, and sends from the chat app.

## Failure behavior

- Model missing: offer a one-time model download; do not silently call a remote translation service.
- Unsupported device or model load/inference failure: preserve the original draft and show a clear error.
- Draft unavailable from the host app: preserve the original and explain that the app must expose editable text to the keyboard.
- Translation output is empty or exceeds the supported draft limit: do not replace the original.

## Assumptions to confirm

- Translate acts on the entire current plain-text chat draft, and reads it only after the user taps Translate. If an app does not expose it, the original remains untouched.
- Vietnamese input starts with Telex; VNI can be added if preferred.
- Qwen3-0.6B is a benchmark candidate only. Quality and response time must be measured on the target phone before locking it in.
- Real latency, memory use, thermals, and backend support still need confirmation on supported devices.

## MVP acceptance checks

- Normal mode can enter Vietnamese text and handle space, backspace, shift, symbols, and enter in ordinary text fields.
- Mode switch works without closing the keyboard; target can be set to English or Russian.
- Translate preview is generated locally, can be applied or cancelled, and never sends the message.
- Password/secure fields never expose text to translation.
- After downloading the model, translation succeeds with network disabled; app logs and persistent storage contain no message text.
- Measure cold/warm response time, memory use, and battery on a real device before claiming it is fast enough.

## Not in this MVP

iOS, cloud/API translation, Gemini web automation, user accounts, server-side storage, speech input, autocorrect/prediction, and app-store release.

## Technical references

- Android IME: https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method
- LiteRT-LM Android Kotlin API and supported models: https://developers.google.com/edge/litert-lm/android and https://developers.google.com/edge/litert-lm/overview
- Gemini Nano foreground-use constraint: https://developers.google.com/ml-kit/genai
- Qwen3-0.6B model card: https://huggingface.co/Qwen/Qwen3-0.6B
