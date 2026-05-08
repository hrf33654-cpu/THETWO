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

For a physical Android device, use:

```bash
adb reverse tcp:8787 tcp:8787
```

For the Android emulator, the client will use `http://10.0.2.2:8787`.
