# 第三方声明

本仓库通过相邻仓库已构建的 JAR 使用 BS3D Weights（MIT）、Meridian Human 与 Meridian Framework；GameTest 运行时另由 Meridian 完整装配提供 BS3D、Kizuna Inventory UI Framework 与其他附属。各依赖保留其自身许可和声明文件。

正式 JAR 嵌套以下库：

- tomlj 1.1.1（Apache-2.0），读取 `meridian-burden.toml`。
- ANTLR 4 Java 运行库 4.11.1（BSD-3-Clause），tomlj 的解析依赖。
