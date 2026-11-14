package dev.kizuna.meridian.burden.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;

/** 模组入口：读取负重设置，把行走负荷提供者注册到 Human，再把读数同步给客户端。负重行另经 Human 的 /meridian body status 显示。 */
public final class BurdenFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // 设置错误直接拒绝启动，不以默认参考值静默运行；旧文件只缺后加的键时按默认表运行并告警。
        Path path = FabricLoader.getInstance().getConfigDir().resolve(BurdenSettings.FILE_NAME);
        BurdenSettings settings = BurdenSettings.load(path);
        BurdenLoad.install(settings);
        // 读数同步给玩家自己的客户端，由 Human-UI 的检视摘要显示。
        BurdenSync.install(settings);
    }
}
