package dev.kizuna.meridian.burden.test;

import com.mojang.authlib.GameProfile;
import dev.kizuna.meridian.technique.fabric.testing.RecordingConnection;
import dev.kizuna.meridian.technique.fabric.testing.TestPlayers;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import java.util.List;
import java.util.UUID;

/** 经过真实登录与频道协商的测试玩家，网络输出记录在内存中；与 Human 自带夹具同一做法。 */
final class BurdenTestPlayer implements AutoCloseable {
    private static final int NAME_ID_LENGTH = 8;
    final ServerPlayer player;
    final RecordingConnection connection;

    BurdenTestPlayer(GameTestHelper context) {
        // 只替换传输层，玩家列表、Human 与 BS3D 的登录事件都使用正式实现。
        var world = context.getLevel();
        UUID playerId = UUID.randomUUID();
        var profile = new GameProfile(playerId, "load_" + playerId.toString().substring(0, NAME_ID_LENGTH));
        player = new ServerPlayer(world.getServer(), world, profile, ClientInformation.createDefault());
        // Human 每 tick 检查必装 UI 接收通道，缺少时会暂停身体。
        connection = new RecordingConnection(List.of(Identifier.parse("meridian:body_view"),
                Identifier.parse("meridian:gathering_response"), Identifier.parse("meridian:opening_response"),
                Identifier.parse("meridian_progression:event"), Identifier.parse("kizuna_players_animation:play"),
                Identifier.parse("kizuna_players_animation:stop")));
        TestPlayers.logIn(player, connection);
        // 创造模式不受伤，受击会让 Human 过滤这一 tick 的位移。
        player.setGameMode(GameType.CREATIVE);
        player.setNoGravity(true /* noGravity */);
    }

    @Override
    public void close() {
        // 真实断线入口让 Human 撤销认领，并从在线列表移除玩家；已被服务端断开时不再重复处理。
        connection.disconnectFromClient();
    }
}
