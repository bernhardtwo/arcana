# Contributing

PRs are welcome. Three rules and that is it:

1. One ability per PR. Implement `Ability`, register it in `ArcanaPlugin` and
   document its `config.yml` keys in the README.
2. No runtime dependencies. If you need to talk to another plugin, do it by
   reflection or through `softdepend`, like `ClaimGuard`.
3. Test on the `dev/` server before opening the PR and describe what you
   tested.

CI compiles with JDK 21 against `paper-api` 1.21.11. If your PR does not
compile, it does not get reviewed.
