# DNV Corresponding Source / 对应源码

The GPL-3.0-only source covers Doggie's New Vehicle, the bundled Libertad and Yue Naval Aviation modules, and the Gradle and platform projection scripts used to build both releases. See the root `LICENSE` and `doggiesnewvehicle/src/main/resources/META-INF/DNV-LICENSES.md`. Original DNV models are separately licensed and distributed with the playable mod.

本源码包包含 GPL-3.0-only 的 DNV、同包自由号与岳级航空模块的源码，以及双平台的 Gradle 构建和平台投影脚本。见根目录 `LICENSE` 和 DNV 的 `META-INF/DNV-LICENSES.md`。DNV 原创模型单独授权，仅随可玩的模组发行。

The Superb Warfare team provides the matching upstream sources. 卓越前线团队提供同版上游源码。[Forge 65d985a64](https://www.curseforge.com/minecraft/mc-mods/superb-warfare/files/9019882/additional-files) · [NeoForge e0c2b27f](https://www.curseforge.com/minecraft/mc-mods/superb-warfare/files/9470559/additional-files)

## Source layout / 源码布局

| Path | Role / 职责 |
| --- | --- |
| `doggiesnewvehicle/src/main` | Base mod, shared vehicle contracts, player guides / 主模组、通用载具接口和玩法手册 |
| `doggiesnewvehicle/src/artillery` | Defensive turrets, heavy guns, armored vehicles and silos / 防御炮塔、重炮、装甲载具及发射井 |
| `doggiesnewvehicle/src/boss`, `src/platform` | Large combat units and route platforms / 大型战斗单位与航线平台 |
| `doggiesnewvehicle/route-core` | Shared route rules and contracts / 航线规则和接口 |
| `doggiesnewvehicle/src/aviation`, `src/navalCommon`, `libertad/src` | Yue and Feiying, shared ship wreck handling, and the Libertad battleship / 岳级和飞鹰、舰船共用损毁逻辑及自由号 |
| `doggiesnewvehicle/src/neoforge1211`, `gradle/neoforge1211` | NeoForge platform adapters and source projection / NeoForge 平台适配及源码投影 |
| `doggiesnewvehicle/src/test`, `architecture-test-support` | DNV unit tests and shared test helpers / DNV 单元测试与共用测试工具 |

Forge compiles from the authored trees. NeoForge generates its shared implementation under `doggiesnewvehicle/build/neoforge1211/generated`. Edit the authored files or explicit platform adapters when contributing a change. Vehicle registry IDs are documented in [DNV entities](public/entities.md).

Forge 直接编译源目录。NeoForge 将共用代码生成到 `doggiesnewvehicle/build/neoforge1211/generated`。修改请落在原始源码或平台适配文件中。载具注册 ID 见[DNV 实体表](public/entities.md)。

## Compile the code / 编译源码

Run the wrapper from the root of this source archive. On Windows use `doggiesnewvehicle\gradlew.bat` in place of `./doggiesnewvehicle/gradlew`. Allow Gradle to fetch uncached toolchain dependencies on the first build.

在源码包根目录运行 Gradle Wrapper。Windows 下使用 `doggiesnewvehicle\gradlew.bat` 代替 `./doggiesnewvehicle/gradlew`。首次构建需要下载未缓存的依赖。

### Forge 1.20.1

- Set `JAVA_HOME` to Java 17. Put the [Forge Superb Warfare build 65d985a64](https://www.curseforge.com/minecraft/mc-mods/superb-warfare/files/9019882) at `doggiesnewvehicle/libs/superbwarfare-0.8.9.2-mc1.20.1-65d985a64-all.jar`. SHA-256 `559930003f638080de648a8621821d785158768c3f69a762094ef4c8d3c4cc12`.
- Copy [Kotlin for Forge 4.11.0](https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge/files/5402061) to `doggiesnewvehicle/build/ap-runtime-inputs/kotlinforforge-exact.jar` for aviation compilation. SHA-256 `2ba4b35536ef2a08a8585d5a7384e637fb538d7d2a99a5e4d81e9b5660a850`.
- 以 Java 17 运行。按上述路径放入指定哈希的卓越前线与 Kotlin for Forge，再执行以下命令编译各模块。

```sh
./doggiesnewvehicle/gradlew -p doggiesnewvehicle compileJava compileArtilleryJava compilePlatformJava compileBossJava compileAviationJava compileNavalJava
```

### NeoForge 1.21.1

- Set `JAVA_HOME` to Java 21 and ensure `python` resolves to Python 3.10 or newer. Put the [NeoForge Superb Warfare build e0c2b27f](https://www.curseforge.com/minecraft/mc-mods/superb-warfare/files/9470559) at `doggiesnewvehicle/libs/neoforge1211/superbwarfare-0.8.9.2-hotfix-mc1.21.1-e0c2b27f.jar`. SHA-256 `f1003f5e6b9d8a6c5e27592aa33df9b88c87b71ff3b5e623f4224222ae140535`.
- 以 Java 21 运行，确保 `python` 指向 Python 3.10 或更高版本。按上述路径放入指定卓越前线，再执行以下命令编译各模块。

```sh
./doggiesnewvehicle/gradlew -p doggiesnewvehicle '-PdnvMinecraft=1.21.1' compileJava compileArtilleryJava compilePlatformJava compileBossJava compileNavalJava
```

## Release builds and tests / 发行构建与测试

Playable release builds use the matching runtime art from the maintainers' local asset set. The public checkout lists the required paths in `doggiesnewvehicle/gradle/runtime-art-inputs.txt`, relative to the DNV module. With authorized assets in place, use `assemble` on the matching loader and Java version. The full integration suite also needs additional pinned dependencies and asset test fixtures, documented by the Gradle task configuration.

制作可玩的发行版需要维护者本地的对应运行资产。公开源码中的 `doggiesnewvehicle/gradle/runtime-art-inputs.txt` 按 DNV 模块的相对路径列出所需文件。授权资产归位后，用对应加载器和 Java 版本运行 `assemble`。完整集成测试还需要指定版本的依赖和资产测试夹具，详见 Gradle 任务配置。
