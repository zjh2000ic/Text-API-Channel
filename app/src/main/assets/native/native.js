/*!
 * native.js —— 只在 Android 外壳里注入（由 MainActivity 的 assets 路径提供）。
 * 作用：把 WebView 做不到的三件事补上
 *   1. blob:/data: 下载（导出对话、下载附件）→ 原生保存到「下载」目录
 *   2. blob: 新窗口预览（附件预览）→ 原生用系统应用打开
 *   3. 主题色变化 → 同步状态栏 / 导航栏颜色
 */
(function () {
  'use strict';

  var bridge = window.ChatDemo;
  var isShell = !!(bridge && typeof bridge.saveFile === 'function');
  window.__CHAT_DEMO_SHELL__ = isShell;
  if (!isShell) {
    return;
  }

  var log = function () {
    try {
      console.log.apply(console, ['[native]'].concat(Array.prototype.slice.call(arguments)));
    } catch (e) {
      /* ignore */
    }
  };

  function toast(message) {
    try {
      bridge.toast(String(message));
    } catch (e) {
      /* ignore */
    }
  }

  /* ---------------------------------------------------------------- blob 工具 */

  function blobToBase64(blob) {
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onload = function () { resolve(String(reader.result || '')); };
      reader.onerror = function () { reject(reader.error || new Error('read blob failed')); };
      reader.readAsDataURL(blob);
    });
  }

  function urlToBlob(url) {
    return fetch(url).then(function (response) {
      if (!response.ok) {
        throw new Error('fetch ' + url + ' -> ' + response.status);
      }
      return response.blob();
    });
  }

  function guessName(url, fallback) {
    if (fallback) {
      return fallback;
    }
    try {
      var withoutQuery = String(url).split('?')[0].split('#')[0];
      var last = withoutQuery.substring(withoutQuery.lastIndexOf('/') + 1);
      return last || 'download';
    } catch (e) {
      return 'download';
    }
  }

  function saveUrl(url, name, mime) {
    return urlToBlob(url)
      .then(function (blob) {
        return blobToBase64(blob).then(function (base64) {
          var saved = bridge.saveFile(base64, guessName(url, name), mime || blob.type || '');
          log('save', name, saved);
          if (!saved) {
            toast('保存失败');
          }
        });
      })
      .catch(function (error) {
        log('save failed', error);
        toast('保存失败：' + (error && error.message ? error.message : error));
      });
  }

  function previewUrl(url, name, mime) {
    return urlToBlob(url)
      .then(function (blob) {
        return blobToBase64(blob).then(function (base64) {
          var ok = bridge.previewFile(base64, guessName(url, name), mime || blob.type || '');
          if (!ok) {
            toast('无法预览该附件');
          }
        });
      })
      .catch(function (error) {
        log('preview failed', error);
        toast('无法预览该附件');
      });
  }

  /* ---------------------------------------------------------------- 下载拦截 */

  // 页面的「导出对话」用的是 document.createElement('a') + a.click()，
  // 元素不进 DOM，所以只能改原型上的 click。
  var nativeAnchorClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    var href = this.getAttribute('href') || '';
    var shouldIntercept = href.indexOf('blob:') === 0 || href.indexOf('data:') === 0;
    if (!shouldIntercept) {
      return nativeAnchorClick.apply(this, arguments);
    }
    log('anchor click intercepted', href.slice(0, 32), this.download);
    saveUrl(href, this.download || '', '');
    return undefined;
  };

  // 附件预览：window.open(objectUrl, '_blank', 'noreferrer')
  var nativeOpen = window.open;
  window.open = function (url) {
    var target = url == null ? '' : String(url);
    if (target.indexOf('blob:') === 0 || target.indexOf('data:') === 0) {
      previewUrl(target, '', '');
      return null;
    }
    if (!target) {
      return nativeOpen.apply(window, arguments);
    }
    if (/^https?:\/\//i.test(target)) {
      bridge.openExternal(target);
      return null;
    }
    return nativeOpen.apply(window, arguments);
  };

  /* ---------------------------------------------------------------- 状态栏配色 */

  var lastColor = '';
  function syncSystemBars() {
    var meta = document.querySelector('meta[name="theme-color"]');
    var color = meta && meta.getAttribute('content');
    if (!color) {
      var bg = getComputedStyle(document.body).backgroundColor;
      color = bg && bg !== 'rgba(0, 0, 0, 0)' ? bg : '';
    }
    if (!color || color === lastColor) {
      return;
    }
    lastColor = color;
    try {
      bridge.setSystemBars(color);
    } catch (e) {
      /* ignore */
    }
  }

  function watchSystemBars() {
    if (typeof MutationObserver === 'undefined') {
      return;
    }
    var observer = new MutationObserver(syncSystemBars);
    observer.observe(document.documentElement, {
      subtree: true,
      childList: true,
      attributes: true,
      attributeFilter: ['content', 'class', 'data-ds-dark-theme', 'style']
    });
    // 首次晚一点同步，避免和首屏配色打架
    setTimeout(syncSystemBars, 0);
    setTimeout(syncSystemBars, 300);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', watchSystemBars, { once: true });
  } else {
    watchSystemBars();
  }

  /* ---------------------------------------------------------------- 返回键 */

  // MainActivity 按返回键时会派发 app:back：先关掉页面里的浮层
  window.addEventListener('app:back', function () {
    try {
      var dialogs = document.querySelectorAll('[role="dialog"], .modal, [data-modal]');
      if (dialogs.length > 0) {
        var event = new KeyboardEvent('keydown', {
          key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true
        });
        document.dispatchEvent(event);
      }
    } catch (e) {
      /* ignore */
    }
  });

  log('ready, version', bridge.getVersion());
})();
