// Generates the README cover image as a self-contained HTML document, rendered to PNG by
// headless Chromium elsewhere. No npm dependencies — Node/Bun built-ins only.
//
// The phone mockup and notification card embed REAL screenshots captured from a running
// Pixel 7 Pro emulator (docs/cover/assets/*.png) — not hand-drawn UI. Only the surrounding
// cover chrome (background blobs, hero text, device bezel) is generated here.
//
// Usage (preview without a browser):
//   node docs/cover/cover.mjs
// prints two temp .html file paths (light + dark) you can open directly.

import { readFileSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';

const REPO_ROOT = new URL('../../', import.meta.url);

function readRepoFile(relPath) {
  return readFileSync(new URL(relPath, REPO_ROOT), 'utf8');
}

/** Reads a binary asset and returns it as a `data:` URI (base64), for self-contained HTML. */
function readImageDataUri(relPath) {
  const buf = readFileSync(new URL(relPath, REPO_ROOT));
  const ext = relPath.split('.').pop();
  return `data:image/${ext};base64,${buf.toString('base64')}`;
}

/**
 * Reads a Material Symbols vector drawable and returns a function that renders it as an
 * inline SVG string at the given pixel size, using `fill="currentColor"` so callers control
 * color via CSS.
 */
function readIcon(name) {
  const xml = readRepoFile(`app/src/main/res/drawable/${name}.xml`);
  const pathMatches = [...xml.matchAll(/android:pathData="([^"]+)"/g)].map((m) => m[1]);
  if (pathMatches.length === 0) {
    throw new Error(`No pathData in ${name}`);
  }
  const hasGroupTranslate = /android:translateY="960"/.test(xml);
  const viewBox = hasGroupTranslate ? '0 -960 960 960' : '0 0 960 960';
  const paths = pathMatches.map((d) => `<path fill="currentColor" d="${d}"/>`).join('');
  return (size) => `<svg viewBox="${viewBox}" width="${size}" height="${size}" style="display:block">${paths}</svg>`;
}

/** Returns the real app icon SVG (docs/icon.svg) sized via inline CSS, fixed brand red fill. */
function appIconSvg(size) {
  const raw = readRepoFile('docs/icon.svg').replace(' width="108" height="108"', '');
  return raw.replace('<svg ', `<svg style="display:block;width:${size}px;height:${size}px" `);
}

/**
 * Samples an Expressive "polar bloom" shape: r(theta) = R * (1 - depth + depth * (1 + cos(lobes * theta)) / 2),
 * theta offset by rotationDeg. Returns an SVG path `d` string (M...L...Z, 2-decimal coords).
 */
function polarShapePath(cx, cy, R, lobes, depth, rotationDeg = 0) {
  const rot = (rotationDeg * Math.PI) / 180;
  const steps = 720;
  const points = [];
  for (let i = 0; i <= steps; i++) {
    const theta = (i / steps) * 2 * Math.PI + rot;
    const r = R * (1 - depth + (depth * (1 + Math.cos(lobes * theta))) / 2);
    const x = cx + r * Math.cos(theta);
    const y = cy + r * Math.sin(theta);
    points.push(`${i === 0 ? 'M' : 'L'}${x.toFixed(2)} ${y.toFixed(2)}`);
  }
  return `${points.join('')}Z`;
}

const SHAPE_PRESETS = {
  cookie9: { lobes: 9, depth: 0.1 },
  cookie12: { lobes: 12, depth: 0.08 },
  clover4: { lobes: 4, depth: 0.35 },
  softBurst: { lobes: 10, depth: 0.14 },
};

function shapePath(preset, cx, cy, R, rotationDeg = 0) {
  const { lobes, depth } = SHAPE_PRESETS[preset];
  return polarShapePath(cx, cy, R, lobes, depth, rotationDeg);
}

// Seed color = brand red #B3261E (app/src/main/res/drawable/ic_launcher_background.xml, docs/icon.svg).
const TOKENS = {
  light: {
    surface: '#FFF8F7',
    surfaceContainerLow: '#FFF0EE',
    surfaceContainer: '#FCEAE7',
    surfaceContainerHigh: '#F6E4E2',
    surfaceContainerHighest: '#F1DEDC',
    onSurface: '#231918',
    onSurfaceVariant: '#534341',
    outlineVariant: '#D8C2BF',
    primary: '#B3261E',
    onPrimary: '#FFFFFF',
    primaryContainer: '#FFDAD6',
    onPrimaryContainer: '#8C1D18',
    secondaryContainer: '#FFDAD5',
    onSecondaryContainer: '#5D3F3B',
    tertiaryContainer: '#FDDFA6',
    onTertiaryContainer: '#574419',
    error: '#B3261E',
  },
  dark: {
    surface: '#1A1111',
    surfaceContainerLow: '#231919',
    surfaceContainer: '#271D1C',
    surfaceContainerHigh: '#322827',
    surfaceContainerHighest: '#3D3231',
    onSurface: '#F1DEDC',
    onSurfaceVariant: '#D8C2BF',
    outlineVariant: '#534341',
    primary: '#FFB4AB',
    onPrimary: '#690005',
    primaryContainer: '#8C1D18',
    onPrimaryContainer: '#FFDAD6',
    secondaryContainer: '#5D3F3B',
    onSecondaryContainer: '#FFDAD5',
    tertiaryContainer: '#574419',
    onTertiaryContainer: '#FDDFA6',
    error: '#FFB4AB',
  },
};

function kebab(key) {
  return key.replace(/[A-Z]/g, (c) => `-${c.toLowerCase()}`);
}

function cssVars(tokens) {
  return Object.entries(tokens)
    .map(([k, v]) => `--${kebab(k)}:${v};`)
    .join('');
}

/**
 * Builds a complete standalone HTML document (1280x720 CSS px @ the caller's device scale)
 * for the README cover image. The hero text and background blobs are generated cover chrome;
 * the phone screen and notification card are REAL screenshots captured from a Pixel 7 Pro
 * emulator running the app (docs/cover/assets/*.png), embedded verbatim.
 *
 * @param {'light'|'dark'} mode
 */
export function buildCoverHtml(mode) {
  const t = TOKENS[mode];

  const icRelease = readIcon('ic_release_alert');

  const appScreenshot = readImageDataUri(mode === 'dark' ? 'docs/cover/assets/app-dark.png' : 'docs/cover/assets/app-light.png');
  // Only a light-theme notification capture is available (see docs/cover/README.md); reused
  // for both modes as an authentic floating card rather than a hand-drawn substitute.
  const notifScreenshot = readImageDataUri('docs/cover/assets/notification.png');

  const html = `<!doctype html>
<html>
<head>
<meta charset="utf-8">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Roboto+Flex:opsz,wdth,wght@8..144,25..151,100..1000&display=block" rel="stylesheet">
<style>
  :root { ${cssVars(t)} }
  * { box-sizing: border-box; }
  html, body { margin: 0; padding: 0; }
  body {
    width: 1280px;
    height: 720px;
    overflow: hidden;
    background: var(--surface);
    font-family: 'Roboto Flex', Roboto, sans-serif;
  }
  #cover { position: relative; width: 1280px; height: 720px; }

  #bg-shapes { position: absolute; inset: 0; z-index: 0; }

  #hero {
    position: absolute;
    left: 88px;
    top: 110px;
    width: 520px;
    z-index: 2;
  }
  #hero h1 {
    margin: 28px 0 0;
    font-size: 112px;
    font-weight: 800;
    font-variation-settings: 'wdth' 120, 'opsz' 144;
    letter-spacing: -3px;
    line-height: 1;
    color: var(--on-surface);
  }
  #hero p.tagline {
    margin: 16px 0 0;
    font-size: 28px;
    font-weight: 400;
    line-height: 1.3;
    color: var(--on-surface-variant);
  }
  .chip-row {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    margin-top: 32px;
  }
  .chip {
    height: 44px;
    padding: 0 20px;
    border-radius: 22px;
    font-size: 17px;
    font-weight: 600;
    display: flex;
    align-items: center;
    gap: 8px;
    white-space: nowrap;
  }

  /* Device bezel is generic chrome around a REAL screenshot (docs/cover/assets/app-*.png),
     sized to that screenshot's exact aspect ratio (1440x3120) so it fits with no cropping. */
  #phone-wrap {
    position: absolute;
    left: 800px;
    top: 24px;
    transform: scale(0.88);
    transform-origin: top left;
    z-index: 1;
  }
  #phone-frame {
    width: 360px;
    height: 757px;
    border-radius: 44px;
    background: #111;
    padding: 10px;
    box-shadow: 0 24px 60px rgba(0,0,0,.28);
  }
  #phone-screen {
    width: 340px;
    height: 737px;
    border-radius: 34px;
    overflow: hidden;
  }
  #phone-screen img { display: block; width: 100%; height: 100%; object-fit: cover; }

  /* Real notification-shade capture (docs/cover/assets/notification.png), cropped to just the
     card, floated as a tilted overlay. */
  #notif-card {
    position: absolute;
    left: 620px;
    top: 470px;
    width: 330px;
    height: ${(330 * 463) / 1322}px;
    z-index: 5;
    border-radius: 24px;
    overflow: hidden;
    box-shadow: 0 16px 40px rgba(0,0,0,.25);
    transform: rotate(-3deg);
  }
  #notif-card img { display: block; width: 100%; height: 100%; }
</style>
</head>
<body>
<div id="cover">
  <svg id="bg-shapes" viewBox="0 0 1280 720" width="1280" height="720">
    <path d="${shapePath('cookie12', 170, 700, 300)}" fill="var(--primary-container)"/>
    <path d="${shapePath('clover4', 1000, 330, 330, 22)}" fill="var(--tertiary-container)" opacity="0.9"/>
    <path d="${shapePath('softBurst', 640, 90, 70)}" fill="var(--secondary-container)"/>
    <rect x="560" y="600" width="180" height="64" rx="32" fill="var(--secondary-container)" transform="rotate(-18 650 632)"/>
    <circle cx="80" cy="90" r="18" fill="var(--primary)" opacity="0.85"/>
  </svg>

  <div id="hero">
    ${appIconSvg(104)}
    <h1>Recalls</h1>
    <p class="tagline">EU and Italian product-recall alerts, aggregated on your phone</p>
    <div class="chip-row">
      <div class="chip" style="background:var(--primary);color:var(--on-primary)">EU Safety Gate</div>
      <div class="chip" style="background:var(--secondary-container);color:var(--on-secondary-container)">Ministero della Salute</div>
      <div class="chip" style="background:var(--surface-container-highest);color:var(--on-surface)">
        <span style="color:var(--primary);display:flex">${icRelease(20)}</span>
        Per-source alerts
      </div>
    </div>
  </div>

  <div id="phone-wrap">
    <div id="phone-frame">
      <div id="phone-screen">
        <img src="${appScreenshot}" alt="" />
      </div>
    </div>
  </div>

  <div id="notif-card">
    <img src="${notifScreenshot}" alt="" />
  </div>
</div>
</body>
</html>`;

  return html;
}

const isMain = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMain) {
  for (const mode of ['light', 'dark']) {
    const outPath = join(tmpdir(), `recalls-cover-${mode}.html`);
    writeFileSync(outPath, buildCoverHtml(mode), 'utf8');
    console.log(outPath);
  }
}
