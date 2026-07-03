# Camel 2.x/3.x → 4.x Component Name Mapping

This guide contains **name mappings only**. All component options, URI syntax, and configuration details MUST be retrieved from the MCP catalog at runtime using `camel_catalog_component_doc`.

## How to Use This Guide

1. Extract the component scheme from each URI in the source route
2. If the source version is 2.x, apply the **2.x → 3.x** table first
3. Then apply the **3.x → 4.x** table
4. If the component is not in either table, it may be unchanged — verify with `camel_catalog_components`
5. After finding the 4.x name, call `camel_catalog_component_doc` to verify all options

## Camel 2.x → 3.x Component Renames

These renames happened in Camel 3.0. The old names were removed.

| 2.x Component | 3.x Component | Notes |
|---------------|---------------|-------|
| `http4` | `http` | Uses Apache HttpClient 4.x/5.x. Old `http` (Commons HTTP 3.x) was removed. |
| `https4` | `https` | Same — old `https` removed |
| `netty4` | `netty` | Uses Netty 4.x. Old `netty` (Netty 3.x) was removed. |
| `netty4-http` | `netty-http` | HTTP over Netty 4.x |
| `mina2` | `mina` | Uses Apache MINA 2.x. Old `mina` (MINA 1.x) was removed. |
| `quartz2` | `quartz` | Uses Quartz 2.x. Old `quartz` (Quartz 1.x) was removed. |
| `mongodb3` | `mongodb` | Uses MongoDB Java Driver 3.x/4.x. Old `mongodb` was removed. |
| `hdfs2` | `hdfs` | Old `hdfs` removed |
| `rxjava2` | `rxjava` | |
| `swagger-java` | `openapi-java` | OpenAPI replaces Swagger |
| `ibatis` | `mybatis` | iBATIS superseded by MyBatis |
| `mqtt` | `paho` | Eclipse Paho MQTT client |

## Camel 3.x → 4.x Component Renames

These renames happened in Camel 4.0.

| 3.x Component | 4.x Component | Notes |
|---------------|---------------|-------|
| `direct-vm` | `direct` | Cross-CamelContext `direct-vm` removed. Use `direct` (single context). |
| `vm` | `seda` | Cross-CamelContext `vm` removed. Use `seda` (single context). |
| `dozer` | *(removed)* | Dozer component removed. Use DataMapper/XSLT via `camel-xslt-saxon`. |

## Removed Components — No Direct Replacement

These components were removed entirely. When encountered, **STOP and ask the user** for guidance.

| Component | Removed In | Suggested Alternatives |
|-----------|-----------|----------------------|
| `http` (Commons HTTP 3.x) | 3.0 | Already replaced by `http` (was `http4`) — if source uses old `http`, map to new `http` |
| `netty` (Netty 3.x) | 3.0 | Already replaced by `netty` (was `netty4`) |
| `mina` (MINA 1.x) | 3.0 | Already replaced by `mina` (was `mina2`) |
| `boon-json` | 3.0 | Use `jackson` data format |
| `linkedin` | 3.0 | No direct replacement — ask user |
| `ironmq` | 4.0 | No direct replacement — consider `aws2-sqs` or `jms` |
| `etcd` (v2 API) | 4.0 | No direct replacement — consider `consul` or direct HTTP |
| `ganglia` | 4.0 | Use `micrometer` for metrics |
| `jibx` | 3.0 | Use `jaxb` or `jackson-xml` |
| `boon` | 3.0 | Use `jackson` |
| `rx-netty` | 3.0 | Use `netty-http` |
| `chronicle-engine` | 3.0 | No direct replacement |
| `spark-rest` | 4.0 | Use `platform-http` |

## Default Migration Rules for HTTP Consumers and REST Services

### CXF-RS → Camel REST DSL + OpenAPI 3

When migrating `cxf-rs` (or `cxfrs`) consumer endpoints, the default target is **Camel REST DSL** with automatic **OpenAPI 3.x specification generation**. Do NOT migrate to `platform-http` directly — use the REST DSL to preserve the structured API contract.

| Source Component | Target | Notes |
|-----------------|--------|-------|
| `cxf-rs` / `cxfrs` (consumer) | REST DSL (`rest()`) + `platform-http` engine | Define REST endpoints using Camel REST DSL. Generate an OpenAPI 3.x spec (`openapi.json` or `openapi.yaml`) from the REST definition. Use `camel-openapi-java` for spec generation. |

### HTTP Components as Consumer → `platform-http`

When any HTTP-based component is used as a **consumer** (in the `from:` URI), the default migration target is `platform-http`. This applies to all of the following source components when used in `from:`:

| Source Component (in `from:`) | Target | Notes |
|-------------------------------|--------|-------|
| `jetty` | `platform-http` | Jetty consumer → `platform-http` (runtime provides the HTTP server) |
| `netty-http` / `netty4-http` | `platform-http` | Netty HTTP consumer → `platform-http` |
| `undertow` | `platform-http` | Undertow consumer → `platform-http` |
| `servlet` | `platform-http` | Servlet consumer → `platform-http` |
| `restlet` | `platform-http` | Restlet consumer → `platform-http` (Restlet was removed in 3.0) |
| `spark-rest` | `platform-http` | Spark REST → `platform-http` (already in removed list) |
| `cxf` (SOAP consumer) | `cxf` | **Keep `cxf`** — SOAP services must continue using `cxf` for WSDL contract-first support, WS-Security, MTOM, etc. Do NOT migrate to `platform-http`. |

**Important:** This rule only applies to **consumer** usage (`from:`). When these components are used as **producers** (`to:`), keep the original component or migrate to `http` / `https` as appropriate — `platform-http` is consumer-only.

---

## AWS Components — 1.x to 2.x Migration

All AWS components were renamed from `aws-*` to `aws2-*` in Camel 3.x, and the old `aws-*` versions were removed in 4.x.

| 2.x/3.x Component | 4.x Component | Notes |
|-------------------|---------------|-------|
| `aws-s3` | `aws2-s3` | AWS SDK v2 |
| `aws-sqs` | `aws2-sqs` | AWS SDK v2 |
| `aws-sns` | `aws2-sns` | AWS SDK v2 |
| `aws-kinesis` | `aws2-kinesis` | AWS SDK v2 |
| `aws-lambda` | `aws2-lambda` | AWS SDK v2 |
| `aws-ddb` | `aws2-ddb` | AWS SDK v2 |
| `aws-ddbstream` | `aws2-ddbstream` | AWS SDK v2 |
| `aws-ec2` | `aws2-ec2` | AWS SDK v2 |
| `aws-ecs` | `aws2-ecs` | AWS SDK v2 |
| `aws-eks` | `aws2-eks` | AWS SDK v2 |
| `aws-iam` | `aws2-iam` | AWS SDK v2 |
| `aws-kms` | `aws2-kms` | AWS SDK v2 |
| `aws-msk` | `aws2-msk` | AWS SDK v2 |
| `aws-ses` | `aws2-ses` | AWS SDK v2 |
| `aws-sts` | `aws2-sts` | AWS SDK v2 |
| `aws-translate` | `aws2-translate` | AWS SDK v2 |
| `aws-cw` | `aws2-cw` | AWS SDK v2 (CloudWatch) |
| `aws-mq` | `aws2-mq` | AWS SDK v2 (Amazon MQ) |
| `aws-swf` | *(removed)* | SWF deprecated by AWS — use `aws2-step-functions` or Step Functions directly |
