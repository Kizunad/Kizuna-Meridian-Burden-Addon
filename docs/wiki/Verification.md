# 验证记录

## 2026-10-30

命令：`bash gradlew --offline test build :fabric:runGametest`（Java 17）。

Gradle 8.14.6 wrapper、Loom 1.13.6，版本取自 Framework 的版本目录。Framework、Human、bs3d 与 Weights 在 `settings.gradle` 中按
Framework 统一装配清单以源码组合，编译与开发运行使用其当前输出，不必先构建这几个仓库的 JAR；开发运行所需的 Human 客户端库按清单读已构建的 JAR。

### JUnit

- `BurdenSettingsTest`：随 JAR 发布的默认参考负重为 70；整数和小数都接受；`0`、负数、`nan`、`inf`、字符串、空文件、多余键、语法错误都拒绝。
- `BurdenReadingTest`：参考 70 kg 时，有效 70 kg 为 ×2，有效 0（实重 64 kg 全在快捷栏）为 ×1，有效 128 kg 为 ×(1 + 128/70)；实重原样带出。

### GameTest

三条用例都经过真实登录的测试玩家、正式 Human 运动观察与 Weights 计重，结果 3/3 通过：

- `seventyKilogramsDoubleLegFatigueOfTenSecondsWalking`：主库存放 35 块石头（有效 70 kg，×2）的玩家与空身玩家在同一 tick 起步，
  并排行走 200 tick（10 秒）。负重一侧左股直肌疲劳与空身之比在 2 ± 5% 内；随后经正式命令分发执行 `/meridian status`，
  末行为「负重 70.0 kg，行走负荷 ×2.00」。实测疲劳：负重 6.6333，空身 3.3167，比值 2.0。
- `hotbarStonesAreFreeAndMainInventoryStonesCountTwice`：快捷栏 64 块石头实重 64 kg、有效 0、×1；
  主库存 64 块石头实重 64 kg、有效 128 kg、×(1 + 128/70)。
- `burdenCommandNeedsPermissionAndPrintsBothWeightsAndMultiplier`：以权限 2 经正式命令分发执行 `/meridian burden <玩家>`，
  主库存 64 块石头的玩家输出「<玩家名>：实重 64.0 kg，有效负重 128.0 kg，行走负荷 ×2.83」；
  权限 0 的玩家执行 `/meridian burden` 不产生读数；`meridian bur` 补全出 `burden`，`meridian burden ` 补全出目标玩家名。

JUnit 6/6 通过（`BurdenSettingsTest` 3、`BurdenReadingTest` 3）。

未做客户端界面测试：本附属没有客户端代码，状态行只经服务端命令输出。

## 2026-10-31：S2 指令整理

`/meridian burden` 按 Framework 指令对照表删除，负重只经 Human 的 `/meridian body status` 总览末行显示。
游戏内不再显示随身实重，只有有效负重与倍率；实重仍由 `BurdenLoad.read` 带出，`hotbarStonesAreFreeAndMainInventoryStonesCountTwice` 照旧断言。

命令：删除 `.gradle/loom-cache/remapped_mods` 后 `./gradlew test build :fabric:runGametest`（Gradle 守护进程 Java 21，开发运行 Java 17）；
`python3 scripts/check_docs.py`；`git diff --check`。

- `seventyKilogramsDoubleLegFatigueOfTenSecondsWalking`：末尾改为执行 `/meridian body status`，末行仍为「负重 70.0 kg，行走负荷 ×2.00」。
  实测疲劳：负重 6.6333，空身 3.3167，比值 2.0。
- `bodyStatusOfAnotherPlayerEndsWithTargetBurden`（替换 `burdenCommandNeedsPermissionAndPrintsBothWeightsAndMultiplier`）：
  以权限 2 执行 `/meridian body status <玩家>`，执行者收到的总览末行是目标的「负重 128.0 kg，行走负荷 ×2.83」，
  不含执行者自己的「负重 0.0 kg，行走负荷 ×1.00」；`/meridian` 下已没有 `burden` 节点。
  查看他人所需的权限与目标补全归 Human 的注册节点，不在本仓断言。

结果：JUnit 6/6；GameTest 3/3，加载 68 个模组（含 S2 新增的 `meridian_framework`）；文档检查通过（5 个页面）。

## 2026-11-01：S3 迁到 Minecraft 26.2

Minecraft 26.2、Java 25、Gradle 9.8.0 wrapper、Loom 1.19.3（26.x 不混淆，不重映射），依赖改为普通 `implementation`/`compileOnly`；正式代码不变。
测试玩家改用功法库 testFixtures 的登录夹具（`RecordingConnection` + `TestPlayers.logIn`），客户端频道名单不变。
GameTest 改用 Fabric `@GameTest`：行走用例沿用 300 tick，其余两例显式写 1.20.1 默认的 100 tick（26.2 默认只有 20 tick）。

命令：`./gradlew test build :fabric:compileGametestJava`；`python3 scripts/check_docs.py`；`git diff --check`。

结果：JUnit 6/6；文档检查通过（5 个页面）。GameTest 尚未运行：完整装配里还有附属没迁完，迁完后用正式完整装配统一跑。
