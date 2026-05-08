# THETWO Backend

## Development

1. Copy `.env.example` to `.env` if you want to override defaults.
2. Install dependencies:

```bash
npm install
```

3. Start the backend:

```bash
npm run dev
```

The default server URL is `http://127.0.0.1:8787`.

## Real chat configuration

`/chat/send` now expects an OpenAI-compatible `chat/completions` service.

Required environment variables:

```bash
LLM_BASE_URL=https://your-provider.example/v1
LLM_API_KEY=your_api_key
LLM_MODEL=your_model_name
```

Optional:

```bash
LLM_TIMEOUT_MS=12000
```

If these values are missing, `/chat/send` will return `503 LLM_NOT_CONFIGURED`.

For a physical Android device, use:

```bash
adb reverse tcp:8787 tcp:8787
```

For the Android emulator, the client will use `http://10.0.2.2:8787`.
