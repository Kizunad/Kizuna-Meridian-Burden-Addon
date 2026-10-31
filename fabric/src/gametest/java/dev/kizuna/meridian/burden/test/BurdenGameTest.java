package dev.kizuna.meridian.burden.test;

import dev.kizuna.meridian.burden.fabric.BurdenLoad;
import dev.kizuna.meridian.burden.fabric.BurdenReading;
import dev.kizuna.meridian.human.fabric.HumanHealthHost;
import dev.kizuna.meridian.protocol.HealthHostProtobuf;
import dev.kizuna.meridian.protocol.pb.HealthHostMessages.HealthHostRequest;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import static dev.kizuna.meridian.burden.test.BurdenGameTestTicks.DEFAULT_TIMEOUT;

/** 正式 Human 运动观察、Weights 计重与负重提供者串联：主库存的石头加重行走，快捷栏的不计。 */
public final class BurdenGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("meridian_burden_test");
    private static final int HOTBAR_SLOT = 0;
    private static final int FIRST_MAIN_SLOT = 9;
    private static final int FULL_STACK = 64;
    // Weights 内置石头 1000 克，主库存系数 2：35 块即 70 kg 有效负重，等于默认参考负重。
    private static final int SEVENTY_KILOGRAM_STONES = 35;
    private static final long SEVENTY_KILOGRAMS = 70_000;
    private static final long FULL_STACK_GRAMS = 64_000;
    private static final long FULL_STACK_IN_MAIN_GRAMS = 128_000;
    // 默认参考负重同为 70 kg：倍率 = 1 + 有效负重 / 70 kg。
    private static final double UNLOADED = 1;
    private static final double DOUBLE_LOAD = 2;
    private static final double FULL_STACK_IN_MAIN_LOAD =
            UNLOADED + (double) FULL_STACK_IN_MAIN_GRAMS / SEVENTY_KILOGRAMS;
    private static final double MULTIPLIER_TOLERANCE = 1e-9;
    // Weights 每 5 个服务端 tick 最多重算一次，放入石头后多等一 tick 再读。
    private static final long READING_REFRESH_TICKS = 6;
    private static final long WALK_START_TICK = READING_REFRESH_TICKS;
    private static final long WALK_TICKS = 200;
    private static final long WALK_END_TICK = WALK_START_TICK + WALK_TICKS;
    private static final int WALK_TEST_TICK_LIMIT = 300;
    // 每 tick 0.05 格：远小于 Human 的瞬移阈值，又明显大于静止噪声。
    private static final double STEP_BLOCKS = 0.05;
    private static final Vec3 LOADED_START = new Vec3(1.5, 2, 1.5);
    private static final Vec3 EMPTY_START = new Vec3(1.5, 2, 3.5);
    private static final String MUSCLE = "left_rectus_femoris";
    // 两具身体在相同 tick 起步、停步，只有负荷倍率不同；Human 的结算边界还会带来少量浮点误差。
    private static final double FATIGUE_RATIO_TOLERANCE = 0.05;
    private static final String SEVENTY_KILOGRAM_STATUS = "负重 70.0 kg，行走负荷 ×2.00";
    // 查看他人身体要求权限 2；倍率按两位小数输出，1 + 128/70 显示为 2.83。
    private static final PermissionSet OTHER_BODY_PERMISSIONS = LevelBasedPermissionSet.GAMEMASTER;
    private static final String FULL_STACK_IN_MAIN_STATUS = "负重 128.0 kg，行走负荷 ×2.83";
    private static final String UNLOADED_STATUS = "负重 0.0 kg，行走负荷 ×1.00";

    @GameTest(maxTicks = WALK_TEST_TICK_LIMIT)
    public void seventyKilogramsDoubleLegFatigueOfTenSecondsWalking(GameTestHelper context) {
        // 两名玩家并排走同样 10 秒，背 70 kg 的一侧股直肌疲劳约为空身两倍。
        var loaded = new BurdenTestPlayer(context);
        var empty = new BurdenTestPlayer(context);
        loaded.player.getInventory().setItem(FIRST_MAIN_SLOT, new ItemStack(Items.STONE, SEVENTY_KILOGRAM_STONES));
        Vec3 loadedStart = context.absoluteVec(LOADED_START);
        Vec3 emptyStart = context.absoluteVec(EMPTY_START);
        context.failIfEver(() -> {
            long walkedTicks = Math.max(0, Math.min(context.getTick(), WALK_END_TICK) - WALK_START_TICK);
            step(loaded, loadedStart, walkedTicks);
            step(empty, emptyStart, walkedTicks);
        });
        context.runAtTickTime(WALK_START_TICK, () -> {
            verifyReading(context, loaded, SEVENTY_KILOGRAMS, DOUBLE_LOAD);
            verifyReading(context, empty, 0, UNLOADED);
        });
        context.runAtTickTime(WALK_END_TICK, () -> {
            try (loaded; empty) {
                verifyFatigue(context, loaded, empty);
                verifyStatus(context, loaded);
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = DEFAULT_TIMEOUT)
    public void hotbarStonesAreFreeAndMainInventoryStonesCountTwice(GameTestHelper context) {
        // 同样一组 64 块石头：放快捷栏实重 64 kg、不计负荷；放主库存按 2 倍计 128 kg。
        var hotbar = new BurdenTestPlayer(context);
        var main = new BurdenTestPlayer(context);
        hotbar.player.getInventory().setItem(HOTBAR_SLOT, new ItemStack(Items.STONE, FULL_STACK));
        main.player.getInventory().setItem(FIRST_MAIN_SLOT, new ItemStack(Items.STONE, FULL_STACK));
        context.runAtTickTime(READING_REFRESH_TICKS, () -> {
            try (hotbar; main) {
                verifyReading(context, hotbar, 0, UNLOADED);
                verifyTotal(context, hotbar, FULL_STACK_GRAMS);
                verifyReading(context, main, FULL_STACK_IN_MAIN_GRAMS, FULL_STACK_IN_MAIN_LOAD);
                verifyTotal(context, main, FULL_STACK_GRAMS);
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = DEFAULT_TIMEOUT)
    public void bodyStatusOfAnotherPlayerEndsWithTargetBurden(GameTestHelper context) {
        // 负重查询命令已删除：权限 2 经正式命令分发查看另一名玩家的身体，末行是目标而不是执行者的负重。
        var admin = new BurdenTestPlayer(context);
        var target = new BurdenTestPlayer(context);
        target.player.getInventory().setItem(FIRST_MAIN_SLOT, new ItemStack(Items.STONE, FULL_STACK));
        context.runAtTickTime(READING_REFRESH_TICKS, () -> {
            try (admin; target) {
                String name = target.player.getName().getString();
                var operator = admin.player.createCommandSourceStack().withPermission(OTHER_BODY_PERMISSIONS);
                var commands = admin.player.level().getServer().getCommands();
                commands.performPrefixedCommand(operator, "meridian body status " + name);
                List<String> adminLines = messages(admin);
                context.assertTrue(adminLines.contains(FULL_STACK_IN_MAIN_STATUS), "他人身体总览未附加其负重：" + adminLines);
                context.assertTrue(!adminLines.contains(UNLOADED_STATUS), "他人身体总览附加了执行者的负重：" + adminLines);
                var meridian = commands.getDispatcher().getRoot().getChild("meridian");
                context.assertTrue(meridian.getChild("burden") == null, "/meridian burden 仍然存在");
            }
            context.succeed();
        });
    }

    private static void step(BurdenTestPlayer fixture, Vec3 start, long walkedTicks) {
        // 服务端玩家不自行模拟移动；每 tick 在 Human 采样前写入位置和着地状态。
        Vec3 position = start.add(walkedTicks * STEP_BLOCKS, 0, 0);
        fixture.player.setPos(position);
        fixture.player.setOnGround(true /* onGround */);
    }

    private static void verifyReading(GameTestHelper context, BurdenTestPlayer fixture, long effective, double load) {
        BurdenReading reading = BurdenLoad.read(fixture.player).orElseThrow();
        context.assertTrue(reading.effectiveGrams() == effective, "有效负重错误：" + reading);
        context.assertTrue(Math.abs(reading.multiplier() - load) < MULTIPLIER_TOLERANCE, "负荷倍率错误：" + reading);
    }

    private static void verifyTotal(GameTestHelper context, BurdenTestPlayer fixture, long total) {
        BurdenReading reading = BurdenLoad.read(fixture.player).orElseThrow();
        context.assertTrue(reading.totalGrams() == total, "随身实重错误：" + reading);
    }

    private static void verifyFatigue(GameTestHelper context, BurdenTestPlayer loaded, BurdenTestPlayer empty) {
        double loadedFatigue = fatigue(loaded);
        double emptyFatigue = fatigue(empty);
        context.assertTrue(emptyFatigue > 0, "空身行走 10 秒没有产生腿肌疲劳");
        double ratio = loadedFatigue / emptyFatigue;
        LOGGER.info("左股直肌疲劳：负重 {}，空身 {}，比值 {}", loadedFatigue, emptyFatigue, ratio);
        context.assertTrue(Math.abs(ratio - DOUBLE_LOAD) < DOUBLE_LOAD * FATIGUE_RATIO_TOLERANCE,
                "负重与空身的腿肌疲劳比错误：" + loadedFatigue + " / " + emptyFatigue);
    }

    private static void verifyStatus(GameTestHelper context, BurdenTestPlayer fixture) {
        // 经正式命令分发执行 /meridian body status，总览末尾应附加负重说明。
        var server = fixture.player.level().getServer();
        server.getCommands().performPrefixedCommand(fixture.player.createCommandSourceStack(), "meridian body status");
        List<String> lines = messages(fixture);
        context.assertTrue(lines.contains(SEVENTY_KILOGRAM_STATUS), "/meridian body status 未附加负重说明：" + lines);
    }

    private static List<String> messages(BurdenTestPlayer fixture) {
        // 聊天与命令反馈都以系统消息包发给玩家。
        return fixture.connection.packets().stream()
                .filter(ClientboundSystemChatPacket.class::isInstance)
                .map(packet -> ((ClientboundSystemChatPacket) packet).content().getString())
                .toList();
    }

    private static double fatigue(BurdenTestPlayer fixture) {
        // 与治疗附属相同的公开健康宿主查询，读取股直肌的实时疲劳。
        HealthHostRequest request = HealthHostRequest.newBuilder()
                .setProtocolVersion(1)
                .setRequestId(UUID.randomUUID().toString())
                .setSubjectId(fixture.player.getStringUUID())
                .setInspect(true /* inspect */)
                .build();
        var response = HealthHostProtobuf.decodeResponse(new HumanHealthHost().exchange(
                HealthHostProtobuf.encodeRequest(request)));
        return response.getBody().getTissueStatesList().stream()
                .filter(tissue -> tissue.getId().equals(MUSCLE))
                .findFirst().orElseThrow().getFatigue();
    }
}
