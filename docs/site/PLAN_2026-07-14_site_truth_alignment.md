# PLAN 2026-07-14 — Site truth alignment

Owner lane: 🌐🐝 Site-Bee  
Coordinator lane: Selyn / harbor review  
Source-of-truth lane: ITROBOC Android repo and current product docs

## Goal

Make `itroboc-site` truthful, buildable, and useful as the public face of ITROBOC.

The site should be a window, not a second engine room. It must explain the product clearly without becoming a rival source of product truth.

## Current repo state

The site is a React + TypeScript + Vite single-page draft.

Current strengths:

- small/simple codebase;
- custom dark/gold beetle visual direction;
- landing-page skeleton already exists;
- responsive CSS exists;
- standard Vite scripts exist for dev/build/lint/preview.

Current risks:

- `README.md` is still the default Vite template;
- product copy is incomplete;
- one feature card overclaims current app behavior: `Photo recognition` / detecting card barcodes from an image;
- no deployment notes;
- no coordination doc yet for how 🌐🐝 syncs with the Android repo truth.

## Coordination structure

```text
Selyn / harbor
    = continuity, product-language review, ticket shaping, outside perspective

🌈🐝👑 / local repo queen
    = Android repo province, implementation/docs seam, current product truth keeper

🌐🐝 / Site-Bee
    = public site province, landing-page code, copy, visual polish, deployment notes

Archy
    = product owner, field tester, final taste/judgment
```

Rules:

```text
Android repo owns product truth.
Site repo owns public presentation.
Docs move when code moves.
Site copy must not overclaim future features.
Visual charm is welcome; product claims stay grounded.
```

## First ticket

### Ticket: Make landing page truthful and launchable

Steps:

1. Replace the default Vite `README.md` with an ITROBOC site README.
2. Document local commands:

   ```bash
   npm install
   npm run dev
   npm run build
   npm run preview
   npm run lint
   ```

3. Align `src/App.tsx` copy with current Android repo truth.
4. Remove or clearly mark future-only `Photo recognition`.
5. Add landing-page sections or cards for current real strengths:
   - TD card-by-card scanning;
   - fourth-hand auto-fill;
   - deck profiles;
   - recovery controls: Undo / Scissors / Swap / I'm sure / Clear;
   - PBN export/share/save;
   - DDS-preserving PBN import/export;
   - private Android beta status.
6. Keep the existing dark/gold beetle scanner look unless Archy asks for a redesign.
7. Add a short `docs/site_scope.md` or equivalent once the first pass lands.

## Non-goals

- Do not build a full documentation portal yet.
- Do not invent download links before release policy is clear.
- Do not claim OCR, generic photo recognition, multi-card snap recognition, online scoring, or Bridgemate replacement.
- Do not duplicate Android architecture docs inside the site repo.

## Acceptance checks

- `README.md` describes ITROBOC site, not generic Vite.
- `npm run build` succeeds.
- Landing page makes no known false product claims.
- Site-Bee can explain where product truth comes from.
- Archy can read the page and say: “yes, this is what ITROBOC currently is.”

## Tone

Public-facing tone should be clear, practical, and bridge-TD oriented.

Useful flavor:

```text
small beetle, serious table job
fast deal capture
less manual typing
safe recovery from field mistakes
PBN output for real tournament workflows
```

Avoid hype. Prefer field-trust.
