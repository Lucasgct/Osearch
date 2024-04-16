# CHANGELOG
All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). See the [CONTRIBUTING guide](./CONTRIBUTING.md#Changelog) for instructions on how to add changelog entries.

## [Unreleased 2.x]
### Added
- Add support for dependencies in plugin descriptor properties with semver range ([#11441](https://github.com/opensearch-project/OpenSearch/pull/11441))
- Add community_id ingest processor ([#12121](https://github.com/opensearch-project/OpenSearch/pull/12121))
- Introduce query level setting `index.query.max_nested_depth` limiting nested queries ([#3268](https://github.com/opensearch-project/OpenSearch/issues/3268)
- Add cluster setting to dynamically configure the buckets for filter rewrite optimization. ([#13179](https://github.com/opensearch-project/OpenSearch/pull/13179))

### Dependencies

### Changed

### Deprecated

### Removed

### Fixed
- Add a system property to configure YamlParser codepoint limits ([#12298](https://github.com/opensearch-project/OpenSearch/pull/12298))

### Security

[Unreleased 2.x]: https://github.com/opensearch-project/OpenSearch/compare/2.12...2.x
