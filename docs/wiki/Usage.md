# 配置与使用

## 参考负重

首次启动时把默认配置复制到 `config/meridian-burden.toml`：

```toml
# 有效负重达到这个值（千克）时，行走与疾跑的目标功率翻倍。
reference_kg = 70
```

- 只认 `reference_kg` 一个键，缺键、多余键、TOML 语法错误都会让模组初始化失败，不会回退默认值。
- 值可以写整数或小数，必须是大于 0 的有限数；`0`、负数、`nan`、`inf`、字符串都拒绝。
- 配置只在启动时读取一次，修改后需重启服务器。

有效负重的来源系数在 Weights 的 `config/bs3d_weights/burden.toml`，同样启动时读取。

## 查看负荷

`/meridian body status` 的总览末尾多一行：

```
负重 70.0 kg，行走负荷 ×2.00
```

有效负重按千克保留一位小数，倍率保留两位。Weights 判定库存不可算（损坏或重量溢出）时显示
「负重暂不可用，行走负荷 ×1.00」，此时运动按无负荷结算。查看单个组织（`/meridian body tissue <id>`）不附加这一行。
动作栏和 Human-UI 不新增常驻显示。

管理员查看他人身体时，总览末行是目标玩家的负重。本附属不注册指令，指令写法只在
[Framework 指令页](https://github.com/Kizunad/Kizuna-Meridian-Mod-Framework/wiki/Commands#body)记录。
游戏内只显示有效负重与倍率，不显示随身实重；需要实重的附属经 `BurdenLoad.read` 读取（见[接入说明](Integration.md)）。
