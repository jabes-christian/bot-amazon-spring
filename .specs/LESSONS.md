# LESSONS - auto-maintained by scripts/lessons.py

> Machine-owned. Do NOT hand-edit. Changes are overwritten on the next `lessons.py` write.
> Canonical state lives in `.specs/lessons.json`. Edit lessons only via the script.
> promote_threshold=2 distinct features · window_days=45 · quarantine_threshold=2

## Confirmed (load these at Specify/Design)

Corroborated across multiple features. Safe to apply as guidance.

_none_

## Candidates (under observation - do NOT load as guidance yet)

Seen once or not yet corroborated. Tracked, not trusted.

### L-001 - When a test verifies a composed/rendered visual artifact (image overlay, drawn text or badge), assert the drawn content itself via pixel or region sampling, not just container properties like dimensions, format, or presence.
- signal: `ac_gap` · recurrence: 1 feature(s) · scope: `image-composition` · harmful: 0
- features: enriquecimento-conteudo
- evidence: ENRICH-08 / src/test/java/com/jchristian/bot_amazon_spring/service/BannerImageServiceIT.java:99-116 (image-composition)
- last seen: 2026-09-09T00:14:02Z

### L-002 - When pixel-sampling a drawn overlay to prove content was rendered, sample a point inside the specific content (e.g. text glyphs), not a point that is also darkened by an unconditionally-drawn background fill, and confirm by mutating away the content-drawing call that the assertion actually fails.
- signal: `surviving_mutant` · recurrence: 1 feature(s) · scope: `image-composition` · harmful: 0
- features: enriquecimento-conteudo
- evidence: Sensor#1 / src/test/java/com/jchristian/bot_amazon_spring/service/BannerImageServiceIT.java:114-122 (image-composition)
- last seen: 2026-09-09T00:35:09Z

### L-003 - When an acceptance criterion's parenthetical names a specific visual-style detail (e.g. a literal strikethrough), treat it as a testable precise outcome and record any decision to implement it as plain text instead, rather than letting it narrow silently through Design/Tasks.
- signal: `spec_precision_gap` · recurrence: 1 feature(s) · scope: `spec-precision` · harmful: 0
- features: enriquecimento-conteudo
- evidence: ENRICH-08 (spec-precision)
- last seen: 2026-09-10T01:45:12Z

## Quarantined (failed when applied - ignore)

A confirmed lesson that recurred alongside failure. Kept for the maintainer to review.

_none_
