# Official extension-catalog health check

- **Checked:** 2026-10-06
- **Catalog:** `https://gitlab.com/shosetsuorg/extensions/-/raw/dev/index.json`
- **Scope:** the official catalog shown as available in Nameless (41 English entries). This is not a dump of the sources installed on the user's phone; those are not available in the source workspace.

## What was tested

- The repository index was fetched and inspected: 59 total source entries, including 41 English entries. The English entries had the required fields and unique IDs/file names; every literal `Require("...")` dependency referenced an indexed library.
- All 41 English Lua scripts and all 11 indexed libraries were requested from the repository's actual download paths. **All 52 files returned HTTP 200.** All 11 library hashes and 40 of 41 extension-script hashes matched the index.
- The Lua sources were compiled for syntax only with LuaJ 3.0.1, the same Lua runtime version used by the app. **All 41 extensions and 11 libraries parsed successfully.** Remote code was not executed.
- A light homepage GET was made to each source's configured base site; Wildbow/Parahumans has six active serial sites, so this is 46 site-host probes for 41 entries. A couple of synthetic route probes were used only to check reported domain migrations (they returned 404/503). No source search results or chapter text were fetched, and no account/login, captcha, or paywall was accessed.
- The image URLs were checked using the v57 recovery candidates, plus the v58 Sky-mtl → NovelRare mapping described below.

A homepage response and valid Lua syntax do **not** prove that search results, novel metadata, chapters, or reading work in Nameless. Those flows require running each extension in the Android app against live sites. I did not execute the catalog scripts or attempt to bypass site protections.

## Results

- **Repository delivery:** 41/41 extension scripts and 11/11 libraries downloaded successfully.
- **Lua syntax:** 52/52 scripts/libraries parsed successfully.
- **Checksums:** 51/52 matched; the exception is WTR-LAB, detailed below.
- **Website homepages:** among 46 host probes, 26 returned a normal 2xx/206 response, 12 presented a 403/200 challenge page, 2 returned a plain 403, and 6 failed at DNS/TLS/timeout. The 26 responses include six Wildbow/Parahumans sites; at the *extension* level 21 have a reachable homepage. Blocked or failed probes can be transient or depend on device/network, so they are marked **unverified**, not permanently dead.
- **Icons after v58:** 35/41 catalog icon candidates returned image data. Six still have no usable image candidate from this environment; see below. v57 had already repaired the 14 official GitLab Pages icon URLs, and each corresponding raw GitLab PNG was verified.

## Per-extension homepage probe

`Reachable` means only that a small GET to the configured homepage returned content. `Challenge` means the probe received a captcha/anti-bot page or challenge response. Neither result is a full extension runtime test.

| English catalog entry | Repository script | Homepage probe |
|---|---|---|
| NovelFull | 200; hash OK; Lua OK | Reachable — novelfull.com |
| WuxiaWorld.Site | 200; hash OK; Lua OK | Reachable — wuxiaworld.site |
| Sky-mtl(novel-rare) | 200; hash OK; Lua OK | Reachable — novelrare.com |
| Wildbow (Parahumans) | 200; hash OK; Lua OK | 6/6 active serial sites reachable |
| Foxaholic | 200; hash OK; Lua OK | Challenge — 403 |
| Light Novel Bastion | 200; hash OK; Lua OK | Reachable — HTTP 206 |
| Creative Novels | 200; hash OK; Lua OK | Timed out; retry needed |
| Asian Hobbyist | 200; hash OK; Lua OK | Challenge page — HTTP 200 |
| Novel Nb | 200; hash OK; Lua OK | Host had no address in DNS probe |
| Light Novel Heaven | 200; hash OK; Lua OK | Reachable — HTTP 206 |
| Sleepy Translations | 200; hash OK; Lua OK | Reachable — HTTP 200 |
| Rain of Snow Translations | 200; hash OK; Lua OK | TLS handshake failed |
| Travis Translations | 200; hash OK; Lua OK | Old host failed certificate validation |
| Readhive | 200; hash OK; Lua OK | Reachable — HTTP 200 |
| NeoSekai Translations | 200; hash OK; Lua OK | TLS handshake failed |
| Novel Nice (Novlove) | 200; hash OK; Lua OK | Reachable — HTTP 200 |
| RoyalRoad | 200; hash OK; Lua OK | Reachable — HTTP 200 |
| Webnovel | 200; hash OK; Lua OK | Challenge — 403 |
| ScribbleHub | 200; hash OK; Lua OK | Challenge — 403 |
| Wattpad | 200; hash OK; Lua OK | Reachable — HTTP 200 |
| PawRead | 200; hash OK; Lua OK | Challenge page — HTTP 200 |
| Read From Net | 200; hash OK; Lua OK | Blocked — HTTP 403 |
| NovelArrow | 200; hash OK; Lua OK | Reachable after redirect to novelping.com; parser not verified |
| Honeyfeed | 200; hash OK; Lua OK | Challenge — 403 |
| NovelBuddy | 200; hash OK; Lua OK | Reachable — HTTP 200 |
| NovelHall | 200; hash OK; Lua OK | Challenge — 403 |
| KnoxT | 200; hash OK; Lua OK | Blocked — HTTP 403 |
| Shanghai Fantasy | 200; hash OK; Lua OK | Reachable — HTTP 200 |
| Inkitt | 200; hash OK; Lua OK | Reachable — HTTP 200 |
| WTR-LAB | 200; **index hash/version mismatch**; Lua OK | Challenge page — HTTP 200 |
| Novgo (NET) | 200; hash OK; Lua OK | Reachable — HTTP 200 |
| Read Novel Full | 200; hash OK; Lua OK | Reachable — HTTP 200 |
| Light Novel Plus | 200; hash OK; Lua OK | Initial probe: DNS failure. Follow-up alternate-host checks were inconsistent and one path redirected to an unrelated `.vip` news URL; not used as a workaround |
| Free Web Novel | 200; hash OK; Lua OK | Reachable — HTTP 200 |
| Zetro Translations | 200; hash OK; Lua OK | HTTP 206/HTML response; page availability does not validate the image/parser |
| Fimfiction | 200; hash OK; Lua OK | Challenge — 403 |
| NovelFull (NET) | 200; hash OK; Lua OK | Reachable — HTTP 200 |
| Sufficient Velocity | 200; hash OK; Lua OK | Challenge — 403 |
| AltHistory | 200; hash OK; Lua OK | Challenge — 403 |
| SpaceBattles | 200; hash OK; Lua OK | Challenge — 403 |
| Questionable Questing | 200; hash OK; Lua OK | Reachable — HTTP 200 |

## Repairs made / repairable now

1. **Retired official icon host (v57):** the official index still advertises 14 English icons on `shosetsuorg.gitlab.io`; all 14 old links return GitLab sign-in HTML. Nameless retries the matching raw GitLab assets, all of which returned PNGs. Existing source and installed rows were left intact.
2. **Sky-mtl icon (v58):** the extension script already uses `novelrare.com`, but its catalog image URL still pointed to an unresolved `sky-mtl.com` image. The current NovelRare homepage exposes a valid 300×300 WebP brand icon (HTTP 200); v58 adds a narrowly matched fallback to that checked asset. This fixes the icon without changing the extension script or its installed metadata.

## Not safely fixable from Nameless alone at this check

These are the six catalog icons that still had no usable original/retry image candidate:

- **Novel Nb** — `novel35.com` did not resolve in the local DNS probe; its logo/favicon could not be fetched.
- **NeoSekai Translations** — the site/icon endpoint failed the TLS handshake; no official raw-repository icon exists.
- **Honeyfeed** — logo and same-origin favicons returned 403; no official raw-repository icon exists.
- **KnoxT** — logo and same-origin favicon returned 403; no official raw-repository icon exists.
- **Zetro Translations** — advertised logo and favicon paths returned HTML rather than an image; no official raw-repository icon exists.
- **AltHistory** — advertised logo and favicon retries returned 403/challenge HTML; no official raw-repository icon exists.

Website/runtime issues needing an upstream site or extension-author change:

- **Novel Nb, Rain of Snow, NeoSekai, Creative Novels** — respectively DNS, TLS, TLS, or timeout failures in this limited probe. Do not infer permanent shutdown from a single network environment.
- **Light Novel Plus** — the initial probe hit a DNS error; follow-up `www`/`en` routes were inconsistent (server-busy/503), and one path redirected to an unrelated `.vip` news URL. Nameless will not redirect users to that unrelated host.
- **Travis Translations** — search result [4](https://storyseedling.com/about-us/) describes Story Seedling as Travis Translations. Its homepage responds, but the old extension's `/all-series/page/...` route returned 404 and its detail-page selectors were absent. Replacing only the hostname would break parsing; this needs a newly authored/maintained extension, not a blind URL rewrite.
- **The 12 challenge/403 sources** (Foxaholic, Asian Hobbyist, Webnovel, ScribbleHub, PawRead, Honeyfeed, NovelHall, Fimfiction, Sufficient Velocity, AltHistory, SpaceBattles, WTR-LAB) and the two plain-403 sources (Read From Net, KnoxT) may be blocking this probe. Nameless does not bypass captchas, login walls, or access controls; the sources may still behave differently on a phone or network.

**WTR-LAB catalog metadata:** the index says version 1.0.8 and provides a SHA-256 value that does not match the downloaded Lua file. The file's own header says 1.0.9; it is syntactically valid but its index entry needs correction by the extension repository maintainer. The source also contains an embedded third-party translation API key; its value is deliberately omitted here and was not invoked. Its owner should confirm that the key is restricted/rotated appropriately.

## Safety and limits

No source was uninstalled or removed, and no reading/library/download data was accessed or changed. I did not execute the 41 remote scripts, scrape search results or chapters, log into any source, bypass challenges, or change third-party repositories. Fixing parser changes or upstream index errors requires maintainer-approved source updates. Device-installed sources of other languages were outside the selected scope and remain untouched.
