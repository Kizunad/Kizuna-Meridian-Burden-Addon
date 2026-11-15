package dev.kizuna.meridian.burden.fabric;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** 把每名玩家的负重读数发给他自己的客户端：进服发一次，之后读数变了才发。 */
final class BurdenSync {
    /** 每隔这么多 tick 看一次读数变没变；Weights 自己最多每 5 tick 重算一次。 */
    static final int CHECK_INTERVAL_TICKS = 10;
    private final BurdenSettings settings;
    private final Map<UUID, BurdenView> sent = new HashMap<>();

    private BurdenSync(BurdenSettings settings) {
        this.settings = settings;
    }

    static void install(BurdenSettings settings) {
        // 通道两端都登记；服务端另挂进服、断线和 tick 三个事件。
        PayloadTypeRegistry.clientboundPlay().register(BurdenView.TYPE, BurdenView.CODEC);
        var sync = new BurdenSync(settings);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync.send(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> sync.sent.remove(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(sync::tick);
    }

    private void tick(MinecraftServer server) {
        // 没到检查间隔不算；读数和上次发的一样不发。
        if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!view(player).equals(sent.get(player.getUUID()))) {
                send(player);
            }
        }
    }

    private void send(ServerPlayer player) {
        // 记下发出去的这一份，下次只和它比。
        BurdenView view = view(player);
        sent.put(player.getUUID(), view);
        ServerPlayNetworking.send(player, view);
    }

    private BurdenView view(ServerPlayer player) {
        // 和 Human 结算走跑时读的是同一个读数；参考负重和负重上限按这名玩家的出身算。
        BurdenSettings own = BurdenOrigins.settingsFor(player, settings);
        return BurdenLoad.read(player).map(reading -> BurdenView.of(reading, own))
                .orElseGet(() -> BurdenView.unavailable(own));
    }
}
