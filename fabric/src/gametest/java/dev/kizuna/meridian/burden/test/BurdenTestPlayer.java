package dev.kizuna.meridian.burden.test;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.impl.networking.ChannelInfoHolder;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 经过真实登录与频道协商的测试玩家，网络输出记录在内存中；与 Human 自带夹具同一做法。 */
final class BurdenTestPlayer implements AutoCloseable {
    private static final int NAME_ID_LENGTH = 8;
    final ServerPlayerEntity player;
    final RecordingConnection connection;

    BurdenTestPlayer(TestContext context) {
        // 只替换传输层，玩家列表、Human 与 BS3D 的登录事件都使用正式实现。
        var world = context.getWorld();
        var server = world.getServer();
        UUID playerId = UUID.randomUUID();
        var profile = new GameProfile(playerId, "load_" + playerId.toString().substring(0, NAME_ID_LENGTH));
        player = new ServerPlayerEntity(server, world, profile);
        connection = new RecordingConnection();
        // Human 每 tick 检查必装 UI 接收通道，缺少时会暂停身体。
        var channels = ((ChannelInfoHolder) (ClientConnection) connection).getPendingChannelsNames();
        channels.addAll(List.of(new Identifier("meridian:body_view"),
                new Identifier("meridian:gathering_response"), new Identifier("meridian:opening_response"),
                new Identifier("meridian_progression:event"), new Identifier("kizuna_players_animation:play"),
                new Identifier("kizuna_players_animation:stop")));
        server.getPlayerManager().onPlayerConnect(connection, player);
        // 创造模式不受伤，受击会让 Human 过滤这一 tick 的位移。
        player.changeGameMode(GameMode.CREATIVE);
        player.setNoGravity(true /* noGravity */);
    }

    @Override
    public void close() {
        // 真实断线入口让 Human 撤销认领，并从在线列表移除玩家。
        if (player.getServer().getPlayerManager().getPlayer(player.getUuid()) != null) {
            player.networkHandler.onDisconnected(Text.literal("GameTest 完成"));
        }
        connection.disconnect(Text.literal("GameTest 完成"));
    }

    static final class RecordingConnection extends ClientConnection {
        final List<Packet<?>> packets = new ArrayList<>();
        private boolean open = true;

        RecordingConnection() {
            // 接收端仍为服务端，Fabric 的网络附加状态与真实连接一致。
            super(NetworkSide.SERVERBOUND);
        }

        @Override
        public void send(Packet<?> packet) {
            // 保留发送的原始包供聊天断言，不复制任何业务协议。
            packets.add(packet);
        }

        @Override
        public void send(Packet<?> packet, PacketCallbacks callbacks) {
            // 传输成功回调按正常连接执行，业务层无需特殊测试分支。
            send(packet);
            if (callbacks != null) {
                callbacks.onSuccess();
            }
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public boolean isLocal() {
            // 本地测试不经过压缩和加密握手。
            return true;
        }

        @Override
        public SocketAddress getAddress() {
            // 登录日志和玩家管理器需要可打印的连接地址。
            return new InetSocketAddress("127.0.0.1", 0);
        }

        @Override
        public void disconnect(Text reason) {
            // 连接关闭与玩家移除分别由真实网络处理器和测试传输层负责。
            open = false;
        }
    }
}
