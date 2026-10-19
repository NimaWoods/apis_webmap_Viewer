[en](README.md)
# WebmapView 模组

这是一个为Minecraft设计的Fabric模组，允许玩家通过游戏内的浏览器界面查看网页地图服务（squaremap），并支持自定义URL。

## 功能特性

- **自动恢复系统**：如果打开浏览器时显示黑屏，请稍作等待。模组会自动刷新资源包来修复此问题。您也可以手动按下F3+T来刷新资源包。
- **自定义URL管理**：添加、删除和管理多个地图服务URL
- **实时玩家定位**：自动将地图中心定位到您当前的位置
- **多世界支持**：支持主世界、下界和末地维度
- **引擎预加载**：游戏启动时就在后台下载并解压 Chromium 引擎（MCEF Modern），
  首次使用浏览器时无需再等待约 34 MB 的下载

## 使用方法

按下 **H** 键即可打开内置浏览器界面，开始浏览地图

## 配置方式

地图URL **不再** 通过聊天命令设置，而是保存在

```
.minecraft/config/webmapview.json
```

```json
{
  "mapUrl": "squaremap-demo.jpenilla.xyz",
  "urls": [
    "squaremap-demo.jpenilla.xyz"
  ],
  "enabled": true,
  "preloadEngine": true
}
```

- `mapUrl` – 当前打开的地图地址（不含 `https://`）
- `urls` – 所有已保存的地址，会显示在配置界面中供选择
- `enabled` – 设为 `false` 可停用模组
- `preloadEngine` – 设为 `true`（默认）时在游戏启动阶段下载并解压 Chromium 引擎；
  设为 `false` 则推迟到第一次打开浏览器时

可以手动编辑该文件（每次打开浏览器都会重新读取），也可以使用配置界面：

- **ModMenu**（推荐）：模组列表 → WebmapView → 齿轮图标
- `/webmapview` 在游戏内打开同一个配置界面

在配置界面中可以修改地图URL、选择或删除已保存的URL、添加新URL，以及开关模组。
旧版本的 `urls.txt` / `default_url.txt` 会在首次启动时自动迁移。
## 故障排除

- **黑屏问题**：如果浏览器显示黑屏，模组会自动尝试通过刷新资源包来修复此问题（最多尝试2次）。您也可以手动按下F3+T来刷新资源包。
- **初始化失败**：请检查您的MCEF配置，确保所有依赖项都已正确安装。

## 许可证

MIT – 详见 [LICENSE](LICENSE)。运行时依赖 [MCEF Modern](https://modrinth.com/project/mcef-modern) 为 LGPL-2.1，且未打包进本模组。
基于 CinemaMod 的 [mcef-fabric-example-mod](https://github.com/CinemaMod/mcef-fabric-example-mod)（CC0-1.0）。

👍->🌟
