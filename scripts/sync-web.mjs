// 把 chat-demo 的构建产物同步进 Android 工程，并给 index.html 打上外壳需要的补丁。
// 用法: node scripts/sync-web.mjs [distDir]
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(here, '..');
const workspaceRoot = path.resolve(projectRoot, '..');

const distDir = path.resolve(
  process.argv[2] || path.join(workspaceRoot, 'chat-demo-src', 'chat-demo-0.1.1', 'dist')
);
const targetDir = path.join(projectRoot, 'app', 'src', 'main', 'assets', 'www');
const shellScript = path.join(here, 'web-shell.js');

if (!fs.existsSync(path.join(distDir, 'index.html'))) {
  console.error(`找不到前端产物: ${distDir}\n先构建前端：cd chat-demo-src/chat-demo-0.1.1 && rspack build -c rspack.config.ts`);
  process.exit(1);
}
if (!fs.existsSync(shellScript)) {
  console.error(`缺少 web-shell.js: ${shellScript}`);
  process.exit(1);
}

const VIEWPORT_FROM = /content="width=device-width,initial-scale=1"/;
const VIEWPORT_TO = 'content="width=device-width,initial-scale=1,viewport-fit=cover"';

// native.js 必须先于 deferred 的 main.js 执行：放在 head 末尾、作为普通脚本
function patchIndex(file, { forApp }) {
  let html = fs.readFileSync(file, 'utf8');
  const before = html;

  html = html.replace(VIEWPORT_FROM, VIEWPORT_TO);

  // 先注入环境自检脚本（网页版和 APK 都用得上），再注入原生桥脚本
  const scripts = ['<script src="web-shell.js"></script>'];
  if (forApp && !html.includes('native/native.js')) {
    scripts.push('<script src="/assets/native/native.js"></script>');
  }
  for (const tag of scripts) {
    if (!html.includes(tag)) {
      html = html.replace('</head>', `${tag}</head>`);
    }
  }

  if (html !== before) {
    fs.writeFileSync(file, html);
  }
  return html !== before;
}

// 1) dist 也带上自检脚本：用户可能直接双击 dist/index.html
fs.copyFileSync(shellScript, path.join(distDir, 'web-shell.js'));
patchIndex(path.join(distDir, 'index.html'), { forApp: false });

// 2) 整体复制到 APK 资源目录，再补上原生桥
fs.rmSync(targetDir, { recursive: true, force: true });
fs.mkdirSync(targetDir, { recursive: true });
fs.cpSync(distDir, targetDir, { recursive: true });
patchIndex(path.join(targetDir, 'index.html'), { forApp: true });

function dirSize(dir) {
  let total = 0;
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    total += entry.isDirectory() ? dirSize(full) : fs.statSync(full).size;
  }
  return total;
}

console.log(`已同步 ${distDir}`);
console.log(`     -> ${targetDir}  (${(dirSize(targetDir) / 1048576).toFixed(2)} MB)`);
console.log(`index.html 补丁: viewport-fit=cover + web-shell.js + native.js`);
