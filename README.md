# reforma-tributaria-classifier

[![CI](https://github.com/ewerttonbravo/reforma-tributaria-classifier/actions/workflows/ci.yml/badge.svg)](https://github.com/ewerttonbravo/reforma-tributaria-classifier/actions/workflows/ci.yml)

Suggests the CST and `cClassTrib` of each product in a catalog for Brazil's consumption tax reform (CBS, IBS) and checks them against the official tax calculator of the Receita Federal. A suggestion is never a final classification: an accountant confirms it.

## Status

Skeleton: the build, the layer rules and the quality gates. See the [roadmap](docs/roadmap.md).

## Build and test

Requires JDK 25. The Maven Wrapper downloads Maven on first use.

```sh
./mvnw verify                             # build, tests, coverage and mutation gates
./mvnw test                               # tests only
./mvnw -Dtest=ArchitectureTest test       # a single test class
```

`verify` fails below 100% line, branch and mutation coverage in `domain` and `application` ([design](docs/design.md#decisions)).
