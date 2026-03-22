# Morphium Jakarta Data

[![Build](https://github.com/Bardioc1977/morphium-jakarta-data/actions/workflows/build.yml/badge.svg)](https://github.com/Bardioc1977/morphium-jakarta-data/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

Framework-agnostic [Jakarta Data 1.0](https://jakarta.ee/specifications/data/1.0/) runtime for the [Morphium](https://github.com/sboesebeck/morphium) MongoDB ODM.

This module provides the core repository implementation, query derivation, JDQL parsing, pagination, and sorting — **with zero framework dependencies**. It depends only on Morphium core and Jakarta Data API.

Framework integrations (Quarkus, Spring Boot) add their own thin adapter layer on top.

## Features

- `CrudRepository<T, K>` and `MorphiumRepository<T, K>` base interfaces
- Query derivation from method names: `findBy*`, `countBy*`, `existsBy*`, `deleteBy*`
- JDQL support via `@Query` annotation
- `@Find` / `@Delete` with `@By` parameter binding
- Pagination: `Page<T>`, `CursoredPage<T>`, `PageRequest`
- Sorting: `Sort<T>`, `Order<T>`, `@OrderBy`
- Stream and async return types: `Stream<T>`, `CompletionStage<T>`

## Usage

This module is not used directly by application code. Instead, use one of the framework integrations:

| Framework | Module | Repository |
|-----------|--------|------------|
| Quarkus | [quarkus-morphium](https://github.com/Bardioc1977/quarkus-morphium) | Gizmo bytecode generation (build-time) |
| Spring Boot | [spring-boot-morphium](https://github.com/Bardioc1977/spring-boot-morphium) | JDK dynamic proxies (runtime) |

### Maven Dependency

If you are building a framework integration, add this dependency:

```xml
<dependency>
  <groupId>de.caluga</groupId>
  <artifactId>morphium-jakarta-data</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### Building from Source

Requires morphium core to be installed in the local Maven repository first:

```bash
# 1. Build morphium core
git clone https://github.com/Bardioc1977/morphium.git
cd morphium
mvn install -DskipTests -pl morphium-core -am

# 2. Build morphium-jakarta-data
git clone https://github.com/Bardioc1977/morphium-jakarta-data.git
cd morphium-jakarta-data
mvn verify
```

## Architecture

```
morphium-jakarta-data
  de.caluga.morphium.data
    AbstractMorphiumRepository   Core CRUD implementation (protected setMorphium)
    MorphiumRepository           Extended repository interface (distinct, query access)
    RepositoryMetadata           Entity type, ID type, collection name metadata
    QueryDescriptor              Parsed query representation (field, operator, value)
    MethodNameParser             Parses findByXxx method names into QueryDescriptors
    JdqlParser / JdqlQuery       JDQL (Jakarta Data Query Language) parsing
    QueryMethodBridge            Executes derived queries (findBy*, countBy*, deleteBy*)
    JdqlMethodBridge             Executes @Query JDQL methods
    FindMethodBridge             Executes @Find / @Delete annotated methods
    QueryExecutor                Low-level Morphium query execution
    QueryResultHelper            Result type adaptation (List, Stream, Page, Optional)
    CursorHelper                 Cursor-based pagination support
    SortMapper                   Maps Jakarta Data Sort/Order to Morphium sort
    MorphiumPage                 Page/CursoredPage implementation
```

The key design point is `AbstractMorphiumRepository.setMorphium(Morphium)` being `protected` — framework subclasses override it to bridge their injection mechanism:
- Quarkus: `@Inject` + `@PostConstruct`
- Spring Boot: public setter called by `FactoryBean`

## Requirements

- Java 21+
- Morphium 6.2.2+
- Jakarta Data API 1.0.0

## License

[Apache License 2.0](LICENSE)
