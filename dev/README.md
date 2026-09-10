# Test server

Throwaway Paper server for testing the plugin without touching production. It
listens on port 25566, so it can live alongside the real stack.

```bash
cd ..
gradle build
cp build/libs/Arcana-0.1.0.jar dev/data/plugins/
cd dev
docker compose up -d
```

To iterate after a change:

```bash
cd .. && gradle build && cp build/libs/Arcana-0.1.0.jar dev/data/plugins/ && cd dev && docker compose restart mc
```

Do not use `/reload confirm`. It corrupts the state of other plugins and
produces errors that do not exist outside of it.
