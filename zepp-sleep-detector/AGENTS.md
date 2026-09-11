# Repository Guidelines

## Project Structure & Module Organization

This directory contains the Zepp OS Mini Program for sleep detection. `app.json` defines metadata, permissions, targets, pages, services, and the Active Max runtime. `app.js` is the Device App entry point. UI code lives in `page/home/` and must use `index.page.js` plus `index.page.layout.js`. Background logic lives in `app-service/`, phone-side networking in `app-side/`, and shared message helpers in `shared/`. Assets are split between target assets such as `assets/active-max.r/icon.png` and reusable button images in `assets/raw/`. Build artifacts are generated in `dist/` and dependencies in `node_modules/`.

## Build, Test, and Development Commands

Use Node v20; Zepp tooling is not compatible with Node v22.

```bash
node -v              # verify v20.x
npm install          # install @zeppos/zml and lockfile dependencies
zeus build           # one-shot package build; writes dist/*.zab
zeus preview         # build and show QR for device testing
zeus dev -t "Amazfit Active Max"  # simulator/dev mode; known asset rebuild loop
```

Prefer `zeus build` for repeatable local verification because `zeus dev` can loop when icon assets change.

## Coding Style & Naming Conventions

Use JavaScript ES modules with 2-space indentation and no semicolons, matching the existing files. Built-in Zepp modules should use namespace imports, for example `import * as hmUI from "@zos/ui"`. ZML packages use named imports, for example `import { BasePage } from "@zeppos/zml/base-page"`. Page files must keep the `.page.js` suffix, layout files must keep `.page.layout.js`, and `app.json` page paths should omit only the final `.js` extension.

## Testing Guidelines

There is no automated test suite configured; `npm test` is a placeholder and currently fails. Validate changes with `zeus build`, then use `zeus preview` and the Zepp developer workflow on an Amazfit Active Max when behavior touches sensors, permissions, background services, or phone-side networking. Check logs through Zepp Developer Mode when diagnosing black screens or service startup failures.

## Commit & Pull Request Guidelines

Recent commits use Conventional Commit prefixes such as `feat:`, `fix:`, and `chore:`. Keep subjects imperative and scoped to the change, for example `fix: request background service permission before start`. Pull requests should describe user-visible behavior, list verification commands, mention target device testing, and include screenshots or short screen recordings for UI changes.

## Agent-Specific Instructions

Before changing Zepp OS code, read `../ZEPP_OS_FINDINGS.md` and follow its device-tested constraints. Keep it updated when discovering new Zepp OS issues or solutions. App Service startup requires dynamic `requestPermission` for `device:os.bg_service`, and `appService.start()` must use `url`, not `file`.
