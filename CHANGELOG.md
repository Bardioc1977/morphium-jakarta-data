# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased] - 1.0.0-SNAPSHOT

### Added
- Framework-agnostic Jakarta Data 1.0 runtime for Morphium ODM
- `AbstractMorphiumRepository` base class with full CRUD implementation
- `MorphiumRepository` extended interface (distinct, direct Morphium/Query access)
- Query derivation from method names: `findBy*`, `countBy*`, `existsBy*`, `deleteBy*`
  - Supported operators: equals, greaterThan, lessThan, like, in, between, not, and, or
- JDQL parsing via `@Query` annotation
- `@Find` / `@Delete` with `@By` parameter binding
- Pagination support: `Page<T>`, `CursoredPage<T>`, `PageRequest`
- Sorting: `Sort<T>`, `Order<T>`, `@OrderBy`
- Stream and async return types: `Stream<T>`, `CompletionStage<T>`
- `RepositoryMetadata` for entity type, ID type, and collection name resolution
