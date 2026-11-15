# 接入说明

## 启动

`BurdenFabric` 在模组初始化时读取 `config/meridian-burden.toml`，构造 `BurdenLoad` 并调用 Human 的
`HumanMovementLoads.register` 注册一次。Human 只允许一个行走负荷提供者，另一附属若也注册会在初始化时抛出
`IllegalStateException`，提示两个附属争用同一规则。

本附属不注册指令。负重说明经 `HumanMovementLoad.status` 交给 Human，由 Human 的 `/meridian body status`
附加在总览末尾；指令写法见 [Framework 指令页](https://github.com/Kizunad/Kizuna-Meridian-Mod-Framework/wiki/Commands#body)。

## 读取时机

Human 在逻辑服务器主线程上每个运动结算区间（最多每秒一次，动作切换时也会结算）读一次倍率。
本附属每次读取都向 Weights 的 `Burden.effectiveGrams` 与 `Burden.totalGrams` 查询；
Weights 每 5 个服务端 tick 最多重算一次，同一 tick 的两个数来自同一份结果。

| 情况 | 倍率 | `/meridian body status` |
|---|---|---|
| Weights 可算 | `1 + 有效 kg / 参考 kg` | `负重 xx.x kg，行走负荷 ×x.xx` |
| 库存损坏或重量溢出 | 1 | `负重暂不可用，行走负荷 ×1.00` |

库存不可算时 Weights 记一次告警；本附属按无负荷结算，不让 Human 暂停身体。
倍率由公式保证不小于 1；Human 对非有限或小于 1 的倍率按接线错误抛出并暂停该身体，本附属不会产生这种值。

## 发给客户端的读数

`BurdenSync` 把每名玩家的读数发给他自己的客户端，通道 `meridian_burden:reading`，内容是 `BurdenView`：

| 字段 | 含义 |
|---|---|
| `available` | Weights 这次算不算得出 |
| `totalGrams`、`effectiveGrams` | 随身实重与有效负重，克；算不出时为 0 |
| `multiplier` | 行走负荷倍率；算不出时是 `unavailable_multiplier` |
| `referenceKilograms`、`overloadMultiplier` | 这名玩家的参考负重（设置值乘出身的负重天赋倍数）与超载线，客户端据此写四档从多少千克算起 |

- 进服发一次；之后每 10 个服务端 tick 读一次（读的是 Weights 的共用读数，不另外遍历库存），和上次发给这名玩家的不一样才发。
  一份约 30 到 45 字节。断线时丢掉这名玩家的记录。
- 客户端入口 `BurdenClient` 只留最新的一份，退出世界清掉；界面经 `BurdenClient.reading()` 读，没收到时为空。
- 文字在 `BurdenReadout`：`line` 是摘要里一行，`hover` 是悬浮说明的几行。倍率由服务端算好带在包里，客户端只按它和超载线查档位、换算千克。
- 显示在 Human-UI：检视摘要的负重行。Human-UI 硬依赖本附属（`meridian_burden`）；本附属不依赖任何界面模组，
  专用服务器上照常加载。

## 对其他附属的读数

`BurdenLoad.read(ServerPlayer)` 返回 `Optional<BurdenReading>`：

| 字段 | 含义 |
|---|---|
| `totalGrams` | 随身实重，克，与 Weights 背包页总览同一算法 |
| `effectiveGrams` | 按来源系数加权的有效负重，克 |
| `multiplier` | 当前行走负荷倍率 |

库存不可算时为空。只能在逻辑服务器主线程调用；模组尚未初始化时抛出 `IllegalStateException`。
服务端附属要随身实重走这个入口；玩家在检视的负重行里看得到实重。


## 装备负重口径（2026-11-03）

Weights 现在将 BS3D 非容器穿搭、原版人体四甲槽以及 26.2 的身体甲／鞍槽全部计入
`armor` 来源（默认系数仍为 1）；容器继续使用 `worn_containers`。本附属继续只读
`Burden` API，不复制遍历代码，也不改 70 kg 参考负重或倍率公式。
WarSuit、WarLegs 都是单个实际 ItemStack，其甲层和模型段不增加计重次数。
重量来源和待定克数由 Weights Wiki 的「穿搭与容器重量来源」维护。

运行 GameTest 时用新的 `-PburdenTestRun=<名称>` 和
`-PburdenTestEula=<已有接受文件>`，保留历史世界；已有世界的运行目录会被拒绝。
