// 网页版环境自检：在页面最早阶段检查运行环境，必要时在顶部显示一条醒目提示。
// 只做提示，不改动应用本身的任何逻辑；无依赖、无外部资源，注入到 index.html 的 </head> 之前。
(function () {
  'use strict';
  if (window.__CHAT_DEMO_ENV_WARN__) {
    return;
  }
  window.__CHAT_DEMO_ENV_WARN__ = true;

  var messages = [];

  if (location.protocol === 'file:') {
    messages.push(
      '当前是本地文件（file://）模式：浏览器不会为 file:// 页面保留聊天记录和 API Key，关掉页面就没了。' +
        '请改用「打开页面预览.cmd」打开 http://127.0.0.1:8099，或安装 APK。'
    );
  }

  var storageOk = true;
  try {
    var probeKey = '__chat_demo_probe__';
    localStorage.setItem(probeKey, '1');
    storageOk = localStorage.getItem(probeKey) === '1';
    localStorage.removeItem(probeKey);
  } catch (error) {
    storageOk = false;
  }
  if (!storageOk) {
    messages.push(
      '浏览器拒绝了本地存储：API Key 和聊天记录都不会被保留（无痕模式或站点数据被禁用时常见）。' +
        '请换一个普通窗口打开，或安装 APK。'
    );
  }

  if (messages.length === 0) {
    return;
  }

  var dismissedKey = 'chat-demo:env-warn-dismissed';
  try {
    if (sessionStorage.getItem(dismissedKey) === '1') {
      return;
    }
  } catch (error) {
    /* sessionStorage 也不可用时照常显示 */
  }

  function show() {
    if (document.getElementById('chat-demo-env-warn')) {
      return;
    }
    var bar = document.createElement('div');
    bar.id = 'chat-demo-env-warn';
    bar.setAttribute('role', 'alert');
    bar.style.cssText = [
      'position:fixed',
      'left:0',
      'right:0',
      'top:0',
      'z-index:2147483000',
      'box-sizing:border-box',
      'padding:10px 44px 10px 14px',
      'background:#b3261e',
      'color:#fff',
      'font:13px/1.5 -apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif',
      'box-shadow:0 2px 10px rgba(0,0,0,.28)',
      'white-space:normal',
      'word-break:break-word'
    ].join(';');

    var text = document.createElement('div');
    text.textContent = messages.join(' ');
    bar.appendChild(text);

    var close = document.createElement('button');
    close.type = 'button';
    close.textContent = '\u00d7';
    close.setAttribute('aria-label', '关闭提示');
    close.style.cssText = [
      'position:absolute',
      'top:2px',
      'right:6px',
      'width:32px',
      'height:32px',
      'border:0',
      'background:transparent',
      'color:#fff',
      'font-size:20px',
      'line-height:1',
      'cursor:pointer'
    ].join(';');
    close.addEventListener('click', function () {
      try {
        sessionStorage.setItem(dismissedKey, '1');
      } catch (error) {
        /* ignore */
      }
      bar.remove();
    });
    bar.appendChild(close);

    var host = document.body || document.documentElement;
    host.appendChild(bar);
    document.documentElement.style.setProperty('--app-env-warn-height', bar.offsetHeight + 'px');
  }

  if (document.body) {
    show();
  } else {
    document.addEventListener('DOMContentLoaded', show, { once: true });
  }
})();
