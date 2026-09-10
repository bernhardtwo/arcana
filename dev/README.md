# Test server

Throwaway Paper server for testing the plugin without touching production. It
listens on port 25566, so it can live alongside the real stack.

```bash
cd ..
./gradlew build
rm -f dev/data/plugins/Arcana-*.jar
cp build/libs/Arcana-*.jar dev/data/plugins/
cd dev
docker compose up -d
```

If you have no JDK, build with the Docker command from the main README instead
of `./gradlew build`.

To iterate after a change:

```bash
cd .. && ./gradlew build && rm -f dev/data/plugins/Arcana-*.jar && cp build/libs/Arcana-*.jar dev/data/plugins/ && cd dev && docker compose restart mc
```

The `rm -f` matters: Paper refuses to load two jars of the same plugin, and a
version bump would otherwise leave the old one next to the new one.

Do not use `/reload confirm`. It corrupts the state of other plugins and
produces errors that do not exist outside of it.
