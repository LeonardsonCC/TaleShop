# Changelog

## 2.1.0

- Target Hytale 0.7.0, the final Update 7 / Chapter I release.
- Generate the server version constraint from the selected compile target by default.
- Use the same server dependency for compilation and tests in local builds and CI.
- Configure the per-shop trade limit with `MaxTradesPerShop` (default 20, minimum 1).
  Lowering the limit preserves existing trades and still allows editing and deletion.
- Bundle the SQLite JDBC driver in the distributed mod and initialize it through
  the plugin classloader, fixing SQLite startup failures in standalone servers.
- Add configuration and JSON/SQLite trade-limit regression tests.
