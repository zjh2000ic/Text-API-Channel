# 第三方组件声明

本项目的 `app/src/main/assets/www/` 目录包含 [DeepSeek chat-demo](https://github.com/deepseek-ai/chat-demo)
的构建产物（并做了少量修改，改动清单见主 README「前端改动清单」）。

| 组件 | 版权 | 许可证 | 许可原文 |
| --- | --- | --- | --- |
| chat-demo | Copyright (c) 2026 DeepSeek | MIT | [LICENSE](chat-demo/LICENSE) |

MIT 许可证要求在所有副本或实质部分中保留版权声明与许可声明，因此：

- 本仓库的 `LICENSE`（MIT，Copyright (c) 2026 zjh2000ic）适用于 Android 外壳部分
  （`app/src/main/java/`、`scripts/`、`app/src/main/res/` 等自有代码与资源）；
- 上游 chat-demo 的 MIT 许可原文完整保留在 [`third_party/chat-demo/LICENSE`](chat-demo/LICENSE)，
  适用于 `assets/www/` 内的前端产物；
- 再分发本项目（含 APK）时，请同时保留上述两份许可声明。

前端产物中依赖的第三方 npm 包（React、KaTeX、Mermaid、Prism 等）各自遵循其原有许可证，
完整依赖树见上游 `package.json` 与 `pnpm-lock.yaml`。
