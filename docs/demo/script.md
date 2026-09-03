# Intro video — narration script

Source of truth for both outputs of the intro explainer:

- **A. Interactive page** — [`index.html`](index.html) in this directory (primary).
- **B. Narrated MP4** — the same scenes on a fixed timeline, narration synthesized from the text
  below (secondary; see [README.md](README.md) §"Rendering the MP4").

The plan behind it is `docs/intro-vidio-plan.md` in the development repository. This file is the
artifact its §8.1 asks for: full narration text, per scene, reviewed against the numbers policy
(§5) *before* animation. **Every number spoken here is traceable to a file in this repository**
— the "Source" line of each scene says which, and §"Numbers" at the bottom lists them all with
the caveat each one must carry.

Voice: one synthesized voice, kept across this video and the follow-up. Pace ~150 wpm; the word
counts below are sized to the scene durations, which are the durations authored in `index.html`
(`SCENES[].dur`). Total **5:20** at seven scenes.

| # | Scene | Runs | Interactive |
|---|---|---|---|
| 1 | What is in the request path | 0:00–0:35 | |
| 2 | A page is fragments, not a page | 0:35–1:25 | yes |
| 3 | 1st hit, 2nd hit, N hits | 1:25–2:20 | yes |
| 4 | Invalidation | 2:20–3:00 | yes |
| 6 | Guard: refuse it before it costs anything | 3:00–3:45 | |
| 7 | One host, then the world | 3:45–4:35 | |
| 8 | What it costs the origin | 4:35–5:20 | |

**Scene 5 (combine & reduce) is deliberately absent and keeps its number**, so that when its
measurement lands it drops in between scenes 4 and 6 without renumbering, re-narrating or
re-cutting anything around it. Plan §7.3.

---

## Scene 1 — What is in the request path (0:00–0:35)

> A browser asks your app for a page. Your app renders it: controllers, templates, a database, a
> search index, maybe a third-party call. Forty-one milliseconds, median.
>
> Now put Frontcache in front of it. Same request, same bytes back — one-point-six milliseconds,
> because your app never ran.
>
> That is the whole product, and the rest of this video is how. Six lanes, left to right: the
> client, the guard, the cache — memory then disk, the include processor, your origin, and
> whatever your origin talks to. Every diagram after this is these same lanes in these same
> places, and the colours are the words Frontcache writes in its own logs: green from cache,
> amber dynamic, red error or fallback, grey refused.

- **On screen:** 41.3 ms → 1.6 ms; caption "median · measured A/B on 100,000 replayed production requests"; the six lanes named once; the colour legend.
- **Source:** [value.md §3](../value.md) (41.3 ms → 1.6 ms p50), [concept.md §1](../concept.md).
- **Caveat, on screen for the whole beat:** *median*, and *100,000 replayed production requests, 8 concurrent, one app*.

---

## Scene 2 — A page is fragments, not a page (0:35–1:25) · interactive

> Here is the objection every team has: our pages are personalized, so they cannot be cached.
>
> A page is not one thing. This storefront is a header, a nav, a category body, a product grid,
> a recommendations rail, a cart badge and a footer — and they do not expire together. Your
> origin says so per fragment, in response headers: how long this piece lives, what tags it
> carries, whether bots get a different copy.
>
> The cart badge is the personalized part. It is marked never-cache, and it stays amber for the
> rest of this video.
>
> So the origin returns the outer document with include markers in it, and the edge fills them
> in — fetching the missing ones concurrently, not one after another. A fragment marked async is
> fired and forgotten: nothing is waited for and nothing is inserted, which is how a visit
> counter survives on a page served entirely from cache.
>
> One dynamic fragment does not make the page uncacheable. It makes one tile amber.

- **On screen:** the wireframe splitting along `fc:include` seams; label cards `maxage`, `tags`, `client=bot|browser`, `call=async`; the cart badge amber with `maxage=0` + `dynamic-urls.conf`.
- **Interactive:** the viewer toggles any tile between cached and dynamic and watches the per-request origin-render count respond.
- **Source:** [concept.md §5–§6](../concept.md), [http-headers.md](../http-headers.md), [jsp-tags.md](../jsp-tags.md).
- **Markup is generic on purpose** (`example.com`, a storefront). Plan §5, §7.4.

---

## Scene 3 — 1st hit, 2nd hit, N hits (1:25–2:20) · interactive

> The same page, three times. These are three different physical events.
>
> First hit. Nothing is cached. Every fragment goes to the origin, the origin renders, the
> database is asked, and the include processor fans out to fill the page. Every response is
> written to both cache tiers on the way back. This is the expensive request, and you can watch
> it cost.
>
> Second hit. Every cacheable fragment is answered from L1 — memory, in the same process. The
> origin lane is silent except for the cart badge, which was never cached and never will be.
> Median collapses.
>
> Now the thousandth hit, which is where real traffic lives. A fragment pushed out of memory is
> still on disk, in the Lucene index — slower than L1, and nowhere near the cost of your origin.
> And when a fragment's time-to-live runs out, soft refresh serves the stale copy immediately and
> revalidates behind it, so no visitor pays for the refill.
>
> These states are not narrative. Every one of them is a header on the response and a line in
> the log.

- **On screen:** three passes over the identical lane diagram; the cache-state panel; the console realtime-monitor cutaway (2 s, redacted).
- **Interactive:** "Send request", plus "expire a fragment" and "drop L1" — the viewer chooses which lane answers.
- **Source:** [`docs/diagrams/02-request-lifecycle.svg`](../diagrams/02-request-lifecycle.svg), [concept.md §3, §5](../concept.md) (`x-frontcache-component-cache-level`, `x-frontcache-component-refresh: soft`).

---

## Scene 4 — Invalidation (2:20–3:00) · interactive

> A price changes. Cached pages are only useful if you can clear exactly what changed.
>
> Two ways. By URL pattern: one call from your application, a regular expression, and the pages
> that match are gone. Or by tag: every fragment your origin stored with that product's tag goes,
> on every page that includes it — the category listing, the search results, the recommendations
> rail. That is what tags are for. URLs cut down the site; tags cut across it.
>
> One call fans out to every node in a cluster, which is the next scene.
>
> And the honest bit: invalidate with a filter of star, and you have emptied the whole node.

- **On screen:** `agent.removeFromCache(siteKey, "/store/product-details-42.*")` beside `x-frontcache-component-tags: product-42`; three pages sharing one fragment; the cluster fan-out; the `filter=*` blast radius.
- **Interactive:** the viewer picks URL vs tag and sees which tiles go red.
- **Source:** [concept.md §9](../concept.md), [deployment-usecases.md §2.2 / §3.2](../deployment-usecases.md), [security.md](../security.md) (blast radius).

---

## Scene 6 — Guard: refuse it before it costs anything (3:00–3:45)

> Some traffic should not reach your cache, let alone your app.
>
> The guard runs before both. A scanner walking your server by IP address is not a visitor, and
> every request it makes is a cache miss and an origin render — send it away with a redirect. A
> logged-out client on an account-only page would render at your origin only to be told to log
> in — the edge can read that cookie and make the decision itself. And a client asking faster
> than your limit gets a 429 with a Retry-After, which is what a well-behaved crawler acts on.
>
> Two things this file will teach you the hard way, so learn them here. Exemptions go first —
> your health check, the management API and the dashboard stream are all addressed by IP, and a
> redirect-by-IP rule that catches them makes a healthy node look dead. And behind nginx or a
> CDN, rate limiting counts nothing until you tell Frontcache which proxies to believe;
> otherwise your whole site is one client.
>
> Ship every rule with dry-run first: it counts what it would have caught and touches nothing.

- **On screen:** the guard lane turning three dots away, cache and origin greyed out behind it; the rules file with its exemptions on top; the 429 with `Retry-After` and `Cache-Control: no-store`; the same rule with `| dry-run` and a hit counter.
- **Source:** [guard-getting-started.md](../guard-getting-started.md) §3, §4, §5, §5.1, §5.3, §6.
- **The rules on screen are the shipped documentation's own examples** (`ip-flood | rate:100/10s`, `search-flood | uri~^/search\.htm ; rate:20/10s`), genericised hostnames, no invented thresholds. Plan §5.

---

## Scene 7 — One host, then the world (3:45–4:35)

> Where does this go in your infrastructure? Three answers, and they are the same engine.
>
> One: a servlet filter inside your app's own JVM. One JAR, one filter registration, response
> headers. No new tier, no new host, nothing to operate.
>
> Two: a standalone reverse proxy in front of an origin written in anything — PHP, Node, Python,
> Java. Your app emits the same headers; it does not know what is in front of it.
>
> Three: geographic routing to regional edges, each a standalone node, in front of an origin
> that runs the filter itself. Two cache tiers, and the second one matters: when several edges
> miss at once, the origin's own filter is what stops that arriving at your app as a stampede.
> One invalidation call reaches every region.
>
> You start at one and you arrive at three without redesigning anything.

- **On screen:** the three topologies morphing rather than cutting, built on [`03-filter-topology.svg`](../diagrams/03-filter-topology.svg), [`04-standalone-topology.svg`](../diagrams/04-standalone-topology.svg), [`06-gslb-topology.svg`](../diagrams/06-gslb-topology.svg) and [`07-multiregion-sequence.svg`](../diagrams/07-multiregion-sequence.svg).
- **Source:** [deployment-usecases.md](../deployment-usecases.md).
- **Not named here:** the production deployment that runs topology 3. Plan §7.4 moves it, with its numbers, to the follow-up video.

---

## Scene 8 — What it costs the origin (4:35–5:20)

> One hundred thousand requests, replayed from a real request log in recorded order, against one
> application. Twice: cache off, then cache on. Same eight concurrent workers, same bytes out —
> seven point two six gigabytes both runs.
>
> Requests that reached the origin: seventy-nine thousand nine hundred, against one thousand and
> three. Ninety-eight point seven percent fewer. Throughput six point one times higher, median
> forty-one point three milliseconds down to one point six, and the application was not changed
> — the cache was switched off and on.
>
> Two honesty notes, because they are the reason to trust the rest. The worst case got worse:
> maximum latency went from two hundred ninety-five milliseconds to seven hundred seventy-five,
> because six times more traffic now queues on the small uncached remainder. And that ninety-nine
> percent hit ratio is a hot working set — over the full six hundred eighty-one thousand request
> log, the same harness measures about sixty-seven percent. Expect a number between them.
>
> Below the origin, the app tier served one point three percent of what it served, and the
> database, the search index and the paid APIs behind it saw the same cut.
>
> Do not take the number. Replay your own access log against your own app, with the harness in
> this repository, and get your own.

- **On screen:** two counters racing to 100,000 (79,900 vs 1,003); the results table; the two caveats as their own beat; the second panel (app tier at 1.3%, and what that means below it); the closing CTA.
- **Source:** [value.md §3, §4, §6](../value.md), [`benchmark/`](../../benchmark).
- **Closing CTA, in this order:** run the Docker one-liner · replay your own log with `benchmark/` · read [value.md](../value.md) · [licensing](https://www.eternita.co/frontcache.html).

---

## Numbers

Every figure spoken or shown, its source, and the caveat it must carry (plan §5).

| Number | Where it is | Source | Required caveat |
|---|---|---|---|
| p50 41.3 ms → 1.6 ms | scenes 1, 3, 8 | [value.md §3](../value.md) | *median*; 100,000 replayed production requests, 8 concurrent, one app |
| 79,900 → 1,003 origin renders (−98.7%) | scene 8 | value.md §3 | same run |
| 6.1× throughput (172.4 → 1054.0 req/s) | scene 8 | value.md §3 | at *fixed* concurrency — a floor on headroom, not a ceiling (value.md §6) |
| 7.26 GB transferred by both runs | scene 8 | value.md §2 | it is what "byte-identical content" means here |
| max 295.2 ms → 775.4 ms (worse) | scene 8 | value.md §3, §6 | keep it — it buys the rest their credibility |
| 98.74% hit ratio / ~66.7% over the full 681k log | scene 8 | value.md §3, §6 | always shown as a *range*, never 98.74% alone as a promise |
| app tier serves 1.3% | scene 8 | value.md §3 (1,003 / 79,899) | arithmetic on the row above, same caveats |

**Not used anywhere:** any "typical customer sees X" figure (there isn't one — value.md §6), any
volume from the live deployment (plan §7.4), and any modelled arithmetic — which is why scene 5
is held (plan §7.3).

**One inconsistency in the source, and how this script handles it.** [value.md §6](../value.md)
says in prose "the p99 and max got worse", but its own §3 table shows p99 improving 208.1 ms →
120.3 ms (−42%) and only *max* getting worse (295.2 → 775.4 ms). The script says **max**, which
is what the table supports. Worth fixing in value.md's prose either way.
