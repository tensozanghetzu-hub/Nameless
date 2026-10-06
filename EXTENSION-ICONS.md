# Extension avatar/icon repair

Nameless release v57 (`2.5.3-nameless.8`), prepared 2026-10-06.

## Observed upstream failure

Fetched the live official `extensions/-/raw/dev/index.json` from the current extension repository and checked its English entries. At the time of inspection it listed 41 English sources; **14 advertised image URLs use the retired `shosetsuorg.gitlab.io/extensions/icons/...` GitLab Pages hostname**. A representative request for NovelFull redirected to GitLab sign-in and returned HTTP 403 HTML, not an image. The same current filename at `https://gitlab.com/shosetsuorg/extensions/-/raw/dev/icons/NovelFull.png` returned HTTP 200 PNG. This establishes a concrete source of blank avatars; other independently hosted site/CDN links can also fail or serve HTML/challenge pages.

## Changes

- Browse avatar loading retries with the official GitLab raw icon location for the known legacy GitLab Pages path. Existing, working icon URLs are still tried first.
- Former GitHub-raw icon links from this official repository are also retried at the current GitLab raw location.
- For otherwise broken direct-site logos, the loader tries same-origin HTTPS `favicon.ico` and `apple-touch-icon.png`. It never substitutes GitLab's favicon for an extension-specific image, and it skips favicon requests to the retired Pages host that redirects to sign-in.
- Image requests include only the image host's origin as a minimal Referer, for source sites that reject hotlinked image requests. No third-party image proxy, account token, downloaded-image bundle or new host is introduced.
- A refreshed installed extension now prefers the current icon URL from its same repository before an old icon URL cached in the installed-extension row. If that repository has no icon, its prior installed icon is preserved; other repositories are last-resort image metadata. No extension data is deleted.
- Non-http/https URLs, embedded URL credentials, blank/relative paths, and malformed links do not cause arbitrary local/file/data network requests; they retain Nameless's existing visual placeholder.
- English-only catalog visibility, installed sources, extension definitions, novels, reading status/history, bookmarks and downloads are unchanged. No Room schema migration or extension reinstallation is required.

## Regression tests and limitations

16 policy tests cover the retired GitLab Pages host, old GitHub host, candidate ordering, safe URL acceptance/rejection, public-host-only favicon fallback, privacy of URL queries, and current-vs-cached icon metadata selection. I also checked all 14 legacy GitLab Pages image entries currently advertised for English sources: each old URL redirects/returns 403 HTML, while the corresponding GitLab raw icon currently returns HTTP 200 `image/png`.

This fixes **known catalog links and common hotlink/favicons**, not every third-party website. Dead domains, Cloudflare/captcha pages that return HTTP 200 HTML, server-side anti-hotlink rules, or absent artwork can still use the placeholder. There is no physical-phone visual verification until the user installs and checks v57. The release CI validates code/tests and the GitHub signed release process.
