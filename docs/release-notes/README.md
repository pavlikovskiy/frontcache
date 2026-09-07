# Frontcache — release notes

Docs index: [../doc-index.md](../doc-index.md) · Downloads: [../downloads.md](../downloads.md) ·
Install: [../install-guide.md](../install-guide.md)

Newest first. Each note is written to be read **before** you upgrade: what breaks, what you have to
do, and what deliberately kept its old name so you do not have to do anything.

| Release | What is in it | Do you have to do anything? |
| --- | --- | --- |
| **[2.8.0](2.8.0.md)** | Apache HttpClient 5; **per-IP rate limiting** as a guard rule; **combining `<fc:include>` fragments into one origin call** | Drop-in for a stock deployment. Two behaviour changes apply to everyone (guard rejections now send `Cache-Control: no-store`; a bypassed response in filter mode keeps its status). One source edit per method if you implement `CacheProcessor`, `IncludeProcessor` or `FallbackResolver` |
| **[2.7.0](2.7.0.md)** | The Hystrix *names* are gone — package, config file and endpoint; cache metrics in the export; the console's realtime monitor is Frontcache's own code; the dashboard stream no longer costs a thread per viewer | One config file to edit by hand (`conf/fc-logback.xml`), two lines if you maintain your own `web.xml`, and one method signature if you wrote your own `FallbackResolver` |
| **[2.6.0](2.6.0.md)** | The server image is Frontcache alone — **no nginx**, plain HTTP on 9080; **multi-domain configuration retired** (one node serves one site); Netflix Hystrix replaced by Resilience4j | **Two breaking changes.** Read it before upgrading if you run the container on 80/443, or if `front-cache.domains` is set anywhere — a node with those keys refuses to start |

## Upgrading across several releases

Upgrade in order and read each note; nothing here supports skipping a step blind, because each
release's grace periods are written against the one before it. Two in particular:

- **2.6.x → 2.7.0** keeps `conf/hystrix.properties` working for **one release** (with a `WARN`).
  Rename it to `resilience.properties` only once you no longer intend to roll back.
- **Mixed fleets are fine** across 2.6, 2.7 and 2.8 — nothing on the wire changed, and the console
  reads a mixed fleet on purpose.

What kept its pre-2.7 name indefinitely — `/hystrix.stream`, the `hystrix.command.*` /
`hystrix.threadpool.*` property prefixes, and the JSON type literals the dashboard dispatches on —
is listed in [2.7.0 § What kept its old name](2.7.0.md#what-kept-its-old-name-and-for-how-long).

2.6.1 followed 2.6.0 and has no separate note. Older releases (2.5.x and earlier) have none
either.
