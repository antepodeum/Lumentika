# Contributing

## Local setup

Use JDK 25. The Gradle wrapper and Foojay resolver can provision the configured toolchain.

```bash
./gradlew build
```

## Required verification

Run the same gate as CI before committing:

```bash
./gradlew clean build spotlessCheck
```

Apply formatting with:

```bash
./gradlew spotlessApply
```

Tests live with their owning module. `lumentika-core` contains runtime and integration tests;
`lumentika-ksp` compiles generated DSL fixtures. CI uploads HTML test reports when the gate fails.

## Repository modules

| Module | Role | Published |
| --- | --- | --- |
| `lumentika-core` | Runtime and public UI APIs | Yes |
| `lumentika-ksp` | `@UIComponent` DSL generator | Yes |
| `app` | Executable integration example | No |
| `utils` | Local sample support | No |
| `buildSrc` | Shared Gradle conventions | No |

## Change guidelines

- Keep platform and game types out of core APIs.
- Preserve retained element identity and owner-scoped cleanup.
- Add tests for public behavior, invalidation, lifecycle, and failure cases.
- Update user documentation when public APIs or adapter contracts change.
- Treat API changes as potentially breaking while releases remain `0.x`.

The project uses conventional commit messages and the Apache-2.0 license.
