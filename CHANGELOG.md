# Changelog 1.0 (1.21.x server rewrite)
- Removed client-only metadata and client entrypoint.
- Replaced command-feedback/reflection score retrieval with vanilla per-world death statistics.
- Added reliable actionbar fallback via ServerPlayer.displayClientMessage.
- TextDisplay labels created and followed from logical server; red skull and 2.30 block offset retained.
- Branch-specific 1.21.1 / 1.21.11 Loom builds and build workflow.
- Prior icon preserved (AI-generated; not Modrinth ready).
