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
