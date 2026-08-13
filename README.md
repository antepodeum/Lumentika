# Lumentika Core — Implementation Plan

This archive contains only the implementation design for the platform-independent Lumentika core.

Published artifacts:

```text
com.antepod:lumentika-core
com.antepod:lumentika-ksp
```

Public/runtime packages live under `com.antepod.lumentika.*`.

`lumentika-core` contains the hard UI runtime, Taffy4J as the sole layout implementation, retained rendering/hit testing, styles/themes/animation, text editing, gestures, semantics, platform contracts, and the universal component set.

Universal components:

```text
block / flex / row / column / grid / stack
scroll / list
text / image
button / checkbox / slider / textField / tooltip
```

No concrete platform implementation is part of this archive. The core implementation finishes and is accepted independently through `docs/IMPLEMENTATION_PLAN.md`, `docs/CHECKLIST.md`, and `docs/INTEGRATION_PROOF_SPEC.md`.

Platform libraries implement scheduling, renderer replay, text shaping/input, image metadata, and
native services around `UiRoot`. See [`docs/PLATFORM_ADAPTER_GUIDE.md`](docs/PLATFORM_ADAPTER_GUIDE.md)
for the complete adapter contract, Minecraft mapping, and acceptance tests.
