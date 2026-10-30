package dev.kizuna.meridian.burden.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;

/** 模组入口：读取参考负重，把行走负荷提供者注册到 Human，并挂上查询命令。 */
public final class BurdenFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        // 设置错误直接拒绝启动，不以默认参考值静默运行。
        Path path = FabricLoader.getInstance().getConfigDir().resolve(BurdenSettings.FILE_NAME);
        BurdenLoad.install(BurdenSettings.load(path));
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
                BurdenCommand.register(dispatcher));
    }
}
