# Contributing to Morphium Jakarta Data

Thank you for your interest in contributing! This document provides guidelines and
information for contributors.

## How to Contribute

### Reporting Bugs

- Use [GitHub Issues](https://github.com/Bardioc1977/morphium-jakarta-data/issues) to report bugs
- Include the Morphium version, Java version, and Jakarta Data API version
- Provide a minimal reproducer if possible
- Describe the expected and actual behavior

### Suggesting Features

- Open a [GitHub Issue](https://github.com/Bardioc1977/morphium-jakarta-data/issues) describing the feature
- Explain the motivation and use case
- Discuss the proposed approach before starting implementation

### Submitting Pull Requests

1. Fork the repository and create a feature branch from `main`
2. Make your changes following the code conventions below
3. Add or update tests as appropriate
4. Update documentation if your change affects user-facing behavior
5. Ensure `mvn verify` passes locally
6. Open a pull request against `main`

## Development Setup

### Prerequisites

- JDK 21+
- Maven 3.9+

### Building from Source

```bash
# Build morphium core first (SNAPSHOT dependency)
git clone https://github.com/Bardioc1977/morphium.git
cd morphium
mvn install -DskipTests -pl morphium-core -am

# Then build this project
git clone https://github.com/Bardioc1977/morphium-jakarta-data.git
cd morphium-jakarta-data
mvn verify
```

### Running Tests

```bash
# All tests
mvn test

# A specific test class
mvn test -Dtest=JdqlParserTest
```

## Code Conventions

- Java 21+ features are welcome (records, sealed classes, pattern matching, etc.)
- Follow existing code style (4-space indentation, no tabs)
- No `sun.*` or `jdk.internal.*` imports
- This module must remain **framework-agnostic** — no Quarkus, Spring, or other framework dependencies

## Architecture Notes

This module is the shared Jakarta Data runtime used by both the Quarkus and Spring Boot
integrations. Any changes here affect both frameworks. Key constraints:

- `AbstractMorphiumRepository.setMorphium()` must remain `protected` — framework adapters override visibility
- No framework-specific annotations or APIs
- Dependencies are limited to: morphium-core, jakarta.data-api, slf4j-api

## License

By contributing to this project, you agree that your contributions will be licensed
under the [Apache License 2.0](LICENSE).
