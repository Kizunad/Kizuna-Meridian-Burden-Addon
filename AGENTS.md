# 仓库协作约定

- 本仓库只收 Minecraft Java 版 1.20.1 的负重附属：把 BS3D Weights 的有效负重换算为 Human 行走与疾跑的负荷倍率。计重规则归 Weights，做功、爆发与耐力截断归 Human，权威身体状态归 Framework。
- Weights 与 Human 都是硬依赖；不得添加缺少依赖时的替代路径、降级或可选依赖判断。
- 不复制计重或做功代码：有效负重只经 Weights 公开的 `Burden` API 读取，倍率只经 Human 的 `HumanMovementLoads` 注册点提供。
- `fabric/` 保存配置、提供者、`/meridian burden` 查询命令与初始化入口；`fabric/src/gametest` 只放 GameTest 与测试玩家夹具。
- 简体中文沟通、文档与函数内意图注释；公开标识符用英文。代码按读取、校验、计算、结果展开，命名、换行和注释密度与相邻代码保持一致。
- 文档唯一编辑源为 `docs/wiki/`，README 仅作入口。交付执行 `test build`、`:fabric:runGametest`、文档引用检查和 `git diff --check`。
- 未获授权不提交、推送或远端建仓。许可为 PolyForm Noncommercial 1.0.0，保留第三方许可。
