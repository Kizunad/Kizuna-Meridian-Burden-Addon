# 接入说明

## 启动

`BurdenFabric` 在模组初始化时读取 `config/meridian-burden.toml`，构造 `BurdenLoad` 并调用 Human 的
`HumanMovementLoads.register` 注册一次。Human 只允许一个行走负荷提供者，另一附属若也注册会在初始化时抛出
`IllegalStateException`，提示两个附属争用同一规则。

同一入口经 Fabric 命令注册事件把 `burden` 分支挂到 `/meridian` 根下。Brigadier 按名字合并根节点，
本附属不给根节点加权限，也不改 Human 或其他附属已有的分支；权限 2 只加在 `burden` 节点上。

## 读取时机

Human 在逻辑服务器主线程上每个运动结算区间（最多每秒一次，动作切换时也会结算）读一次倍率。
本附属每次读取都向 Weights 的 `Burden.effectiveGrams` 与 `Burden.totalGrams` 查询；
Weights 每 5 个服务端 tick 最多重算一次，同一 tick 的两个数来自同一份结果。

| 情况 | 倍率 | `/meridian status` |
|---|---|---|
| Weights 可算 | `1 + 有效 kg / 参考 kg` | `负重 xx.x kg，行走负荷 ×x.xx` |
| 库存损坏或重量溢出 | 1 | `负重暂不可用，行走负荷 ×1.00` |

库存不可算时 Weights 记一次告警；本附属按无负荷结算，不让 Human 暂停身体。
倍率由公式保证不小于 1；Human 对非有限或小于 1 的倍率按接线错误抛出并暂停该身体，本附属不会产生这种值。

## 对其他附属的读数

`BurdenLoad.read(ServerPlayerEntity)` 返回 `Optional<BurdenReading>`：

| 字段 | 含义 |
|---|---|
| `totalGrams` | 随身实重，克，与 Weights 背包页总览同一算法 |
| `effectiveGrams` | 按来源系数加权的有效负重，克 |
| `multiplier` | 当前行走负荷倍率 |

库存不可算时为空。只能在逻辑服务器主线程调用；模组尚未初始化时抛出 `IllegalStateException`。
本附属的 `/meridian burden` 命令也通过这个入口读取。
