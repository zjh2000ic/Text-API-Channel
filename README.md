# Text API Channel（Android 外壳版 chat-demo）

> **非官方项目**：本项目与 DeepSeek（杭州深度求索）**没有任何隶属、合作或背书关系**，
> 仅调用用户自己填写的 API Key 与兼容接口通信，不提供任何 API 额度或代理服务。
> 使用前请务必阅读 [**免责声明 DISCLAIMER.md**](DISCLAIMER.md) —— 使用本项目即视为同意其全部条款。
>
> 许可证：[MIT](LICENSE)（外壳部分）｜前端产物来自 [DeepSeek chat-demo](https://github.com/deepseek-ai/chat-demo)（MIT，见 [third_party/](third_party/README.md)）

把 DeepSeek 开源的 [`chat-demo`](https://github.com/deepseek-ai/chat-demo)（React 18 + rspack 写的 Web 版
API 对话 demo，MIT 许可）装进一个 Android 外壳里，做成能直接装到手机上的 APK。

业务逻辑保留上游原样，仅在上游前端源码做了小幅度改动。

## 下载

**[下载最新版 APK](https://github.com/zjh2000ic/Text-API-Channel/releases/latest)**（当前 v0.1.6）

- 要求 **Android 7.0（API 24）及以上**
- 安装包为 **debug 签名**，只能与同签名的版本覆盖升级
- 首次使用需在「设置」中填入**你自己申请的** DeepSeek API Key，本项目不提供任何额度

## 关于本项目

**这是一个「想给自己手机上装个调用Api的AI聊天App」而顺带做出来的项目，而非一个专业软件工程作品。**

- 项目的前端改造与 Android 外壳，是在 **DeepSeek Harness（AI 辅助开发环境）** 的帮助下完成的：
  需求拆解、代码编写、排错大多由 AI 协同产出，人工负责判断、试错和最终取舍。
- 作者并非计算机专业。因此你在代码里可能会看到
  不够优雅的写法、偏长的方法、命名不统一，以及一处问题多种解法并存的痕迹。
- 维护者**没有义务提供技术支持**，也不承诺持续更新 —— 这一点与
  [免责声明](DISCLAIMER.md) 第 4.2 条一致。
- 如果你是内行，看到明显错误、更地道的写法或安全隐患，**非常欢迎开 issue 或 PR 指正**。
  对「AI 写的代码」提出质疑完全合理，能被指出问题是这个项目最大的收益。

  登录方式为key，相同的key登上不同设备会保留相同的key的账号的记录。


## 许可证与免责

| 文件 | 用途 |
| --- | --- |
| [LICENSE](LICENSE) | 本仓库 Android 外壳部分（`app/src/main/java/`、`scripts/`、`res/` 等）—— MIT，Copyright (c) 2026 zjh2000ic |
| [third_party/chat-demo/LICENSE](third_party/chat-demo/LICENSE) | 上游 [DeepSeek chat-demo](https://github.com/deepseek-ai/chat-demo) 的 MIT 原文，适用于 `assets/www/` 内的前端产物 |
| `assets/www/LICENSE` | 同上，随 APK 一起分发（MIT 要求声明随副本走） |
| [DISCLAIMER.md](DISCLAIMER.md) | 免责声明 —— **使用本项目即视为同意其全部条款** |

前端来自 DeepSeek 的 chat-demo，MIT 许可；本仓库的 Android 外壳部分同样按 MIT 使用。
再分发（含打包成 APK 分享）时，请同时保留上述两份版权与许可声明，以及 `DISCLAIMER.md`。

本项目为**非官方**第三方客户端，与 DeepSeek 无隶属关系，详见 [DISCLAIMER.md](DISCLAIMER.md)。
