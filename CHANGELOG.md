# CHANGELOG
All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). See the [CONTRIBUTING guide](./CONTRIBUTING.md#Changelog) for instructions on how to add changelog entries.

## [Unreleased 2.x]
### Added
- Add explicit dependency to validatePom and generatePom tasks ([#12909](https://github.com/opensearch-project/OpenSearch/pull/12909))
- [Concurrent Segment Search] Perform buildAggregation concurrently and support Composite Aggregations ([#12697](https://github.com/opensearch-project/OpenSearch/pull/12697))
- Convert ingest processor supports ip type ([#12818](https://github.com/opensearch-project/OpenSearch/pull/12818))
- Allow setting KEYSTORE_PASSWORD through env variable ([#12865](https://github.com/opensearch-project/OpenSearch/pull/12865))

### Dependencies
- Bump `org.apache.commons:commons-configuration2` from 2.10.0 to 2.10.1 ([#12896](https://github.com/opensearch-project/OpenSearch/pull/12896))
- Bump `asm` from 9.6 to 9.7 ([#12908](https://github.com/opensearch-project/OpenSearch/pull/12908))
- Bump `net.minidev:json-smart` from 2.5.0 to 2.5.1 ([#12893](https://github.com/opensearch-project/OpenSearch/pull/12893))
- Bump `netty` from 4.1.107.Final to 4.1.108.Final ([#12924](https://github.com/opensearch-project/OpenSearch/pull/12924))

### Changed
- [BWC and API enforcement] Enforcing the presence of API annotations at build time ([#12872](https://github.com/opensearch-project/OpenSearch/pull/12872))
- Improve built-in secure transports support ([#12907](https://github.com/opensearch-project/OpenSearch/pull/12907))

### Deprecated

### Removed

### Fixed
- Fix issue with feature flags where default value may not be honored ([#12849](https://github.com/opensearch-project/OpenSearch/pull/12849))

### Security

[Unreleased 2.x]: https://github.com/opensearch-project/OpenSearch/compare/2.13...2.x
