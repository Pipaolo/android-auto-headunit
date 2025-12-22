# Code Style and Conventions

## General

- **Primary Language**: Kotlin (with some Java for compatibility)
- **No formal linting tools**: No ktlint, detekt, or editorconfig configured
- **Java Target**: JVM 1.8

## Kotlin Style

- Use Kotlin idioms (data classes, null safety, extension functions)
- Properties with backing fields for lazy initialization (e.g., `_transport` pattern in AppComponent)
- Companion objects for constants (not top-level constants)
- Use `let`, `apply`, `also` etc. idiomatically

## Naming Conventions

- **Classes**: PascalCase (e.g., `AapTransport`, `VideoDecoderController`)
- **Functions/Properties**: camelCase (e.g., `sendMessage`, `isAlive`)
- **Constants**: SCREAMING_SNAKE_CASE (e.g., `MSG_POLL`, `CONNECT_TIMEOUT`)
- **Packages**: lowercase (e.g., `info.anodsplace.headunit.aap`)
- **Prefix pattern**: Use `Aap` prefix for Android Auto Protocol related classes

## File Organization

- One public class per file (Kotlin style)
- Related data classes can be in same file
- Protocol messages grouped in `protocol/messages/` package

## Architecture Patterns

- **Service Locator**: AppComponent provides dependencies
- **Handler/Message pattern**: For thread communication (AapTransport)
- **Interface-based design**: AccessoryConnection with USB/Socket implementations
- **Foreground Service**: For long-running connection handling

## Documentation

- Minimal inline comments (code is self-documenting)
- No KDoc/Javadoc requirements
- CLAUDE.md and README.md for high-level documentation
