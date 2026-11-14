package dev.kizuna.meridian.burden.fabric.client;

import dev.kizuna.meridian.burden.fabric.BurdenView;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** 客户端入口：收服务端发来的负重读数，留最新的一份给界面读。显示由 Human-UI 的检视摘要负责。 */
public final class BurdenClient implements ClientModInitializer {
    private static volatile BurdenView latest;

    @Override
    public void onInitializeClient() {
        // 只留最新的一份；退出世界就清掉，不把上一局的读数带到下一局。
        ClientPlayNetworking.registerGlobalReceiver(BurdenView.TYPE, (view, context) -> latest = view);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> latest = null);
    }

    /** 服务端最近发来的读数；进服后第一份还没到时为空。 */
    public static Optional<BurdenView> reading() {
        return Optional.ofNullable(latest);
    }
}
