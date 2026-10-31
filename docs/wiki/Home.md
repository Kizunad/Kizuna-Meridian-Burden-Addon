# 经脉负重

经脉负重（模组 ID `meridian_burden`）把 BS3D Weights 算出的**有效负重**换算成 Meridian Human 的**行走负荷倍率**：

```
倍率 = 1 + 有效负重 kg / 参考负重 kg
```

参考负重默认 70 kg。空身倍率为 1，背着 70 kg 有效负重时倍率为 2，行走与疾跑的每块下肢肌肉按双倍目标功率做功，
再照旧受爆发与剩余耐力截断。倍率不写进身体，只影响每次运动结算。

有效负重的来源系数（快捷栏 0、主库存 2、盔甲/副手/光标 1、穿戴容器 1）由 Weights 决定，见 Weights Wiki 的「有效负重系数」。
本附属不改系数、不加上限、不减速，只把 Weights 的数交给 Human。

- [配置与使用](Usage.md)：参考负重配置、`/meridian body status` 的负重行。
- [接入说明](Integration.md)：依赖、读取时机、不可算时的回退和对其他附属的公开读数。
- [验证记录](Verification.md)：JUnit 与 GameTest 覆盖。

## 依赖

Weights 与 Human 都是硬依赖，缺任何一个都不能加载；不提供缺依赖时的替代路径。
Weights 的服务端 JAR 为纯服务端产物，单机内置服务器使用 Weights 客户端 JAR 中同一份服务端代码。
