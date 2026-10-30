package dev.kizuna.meridian.burden.test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import dev.kizuna.meridian.burden.fabric.BurdenLoad;
import dev.kizuna.meridian.burden.fabric.BurdenReading;
import dev.kizuna.meridian.human.fabric.HumanHealthHost;
import dev.kizuna.meridian.protocol.HealthHostProtobuf;
import dev.kizuna.meridian.protocol.pb.HealthHostMessages.HealthHostRequest;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/** 正式 Human 运动观察、Weights 计重与负重提供者串联：主库存的石头加重行走，快捷栏的不计。 */
public final class BurdenGameTest implements FabricGameTest {
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
    private static final Vec3d LOADED_START = new Vec3d(1.5, 2, 1.5);
    private static final Vec3d EMPTY_START = new Vec3d(1.5, 2, 3.5);
    private static final String MUSCLE = "left_rectus_femoris";
    // 两具身体在相同 tick 起步、停步，只有负荷倍率不同；Human 的结算边界还会带来少量浮点误差。
    private static final double FATIGUE_RATIO_TOLERANCE = 0.05;
    private static final String SEVENTY_KILOGRAM_STATUS = "负重 70.0 kg，行走负荷 ×2.00";
    // 负重命令要求权限 2；倍率按两位小数输出，1 + 128/70 显示为 2.83。
    private static final int BURDEN_PERMISSION_LEVEL = 2;
    private static final String FULL_STACK_IN_MAIN_LINE = "：实重 64.0 kg，有效负重 128.0 kg，行走负荷 ×2.83";

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = WALK_TEST_TICK_LIMIT)
    public void seventyKilogramsDoubleLegFatigueOfTenSecondsWalking(TestContext context) {
        // 两名玩家并排走同样 10 秒，背 70 kg 的一侧股直肌疲劳约为空身两倍。
        var loaded = new BurdenTestPlayer(context);
        var empty = new BurdenTestPlayer(context);
        loaded.player.getInventory().setStack(FIRST_MAIN_SLOT, new ItemStack(Items.STONE, SEVENTY_KILOGRAM_STONES));
        Vec3d loadedStart = context.getAbsolute(LOADED_START);
        Vec3d emptyStart = context.getAbsolute(EMPTY_START);
        context.runAtEveryTick(() -> {
            long walkedTicks = Math.max(0, Math.min(context.getTick(), WALK_END_TICK) - WALK_START_TICK);
            step(loaded, loadedStart, walkedTicks);
            step(empty, emptyStart, walkedTicks);
        });
        context.runAtTick(WALK_START_TICK, () -> {
            verifyReading(context, loaded, SEVENTY_KILOGRAMS, DOUBLE_LOAD);
            verifyReading(context, empty, 0, UNLOADED);
        });
        context.runAtTick(WALK_END_TICK, () -> {
            try (loaded; empty) {
                verifyFatigue(context, loaded, empty);
                verifyStatus(context, loaded);
            }
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void hotbarStonesAreFreeAndMainInventoryStonesCountTwice(TestContext context) {
        // 同样一组 64 块石头：放快捷栏实重 64 kg、不计负荷；放主库存按 2 倍计 128 kg。
        var hotbar = new BurdenTestPlayer(context);
        var main = new BurdenTestPlayer(context);
        hotbar.player.getInventory().setStack(HOTBAR_SLOT, new ItemStack(Items.STONE, FULL_STACK));
        main.player.getInventory().setStack(FIRST_MAIN_SLOT, new ItemStack(Items.STONE, FULL_STACK));
        context.runAtTick(READING_REFRESH_TICKS, () -> {
            try (hotbar; main) {
                verifyReading(context, hotbar, 0, UNLOADED);
                verifyTotal(context, hotbar, FULL_STACK_GRAMS);
                verifyReading(context, main, FULL_STACK_IN_MAIN_GRAMS, FULL_STACK_IN_MAIN_LOAD);
                verifyTotal(context, main, FULL_STACK_GRAMS);
            }
            context.complete();
        });
    }

    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE)
    public void burdenCommandNeedsPermissionAndPrintsBothWeightsAndMultiplier(TestContext context) {
        // 经正式命令分发查看另一名玩家：权限 2 的读数与负重提供者一致，普通玩家无权执行。
        var admin = new BurdenTestPlayer(context);
        var target = new BurdenTestPlayer(context);
        target.player.getInventory().setStack(FIRST_MAIN_SLOT, new ItemStack(Items.STONE, FULL_STACK));
        context.runAtTick(READING_REFRESH_TICKS, () -> {
            try (admin; target) {
                String name = target.player.getName().getString();
                String expected = name + FULL_STACK_IN_MAIN_LINE;
                var operator = admin.player.getCommandSource().withLevel(BURDEN_PERMISSION_LEVEL);
                var commands = admin.player.getServer().getCommandManager();
                commands.executeWithPrefix(operator, "meridian burden " + name);
                List<String> adminLines = messages(admin);
                context.assertTrue(adminLines.contains(expected), "负重命令输出错误：" + adminLines);
                commands.executeWithPrefix(target.player.getCommandSource(), "meridian burden");
                List<String> targetLines = messages(target);
                context.assertTrue(!targetLines.contains(expected), "普通玩家执行了负重命令：" + targetLines);
                verifyCompletions(context, commands.getDispatcher(), operator, name);
            }
            context.complete();
        });
    }

    private static void step(BurdenTestPlayer fixture, Vec3d start, long walkedTicks) {
        // 服务端玩家不自行模拟移动；每 tick 在 Human 采样前写入位置和着地状态。
        Vec3d position = start.add(walkedTicks * STEP_BLOCKS, 0, 0);
        fixture.player.setPosition(position);
        fixture.player.setOnGround(true /* onGround */);
    }

    private static void verifyReading(TestContext context, BurdenTestPlayer fixture, long effective, double load) {
        BurdenReading reading = BurdenLoad.read(fixture.player).orElseThrow();
        context.assertTrue(reading.effectiveGrams() == effective, "有效负重错误：" + reading);
        context.assertTrue(Math.abs(reading.multiplier() - load) < MULTIPLIER_TOLERANCE, "负荷倍率错误：" + reading);
    }

    private static void verifyTotal(TestContext context, BurdenTestPlayer fixture, long total) {
        BurdenReading reading = BurdenLoad.read(fixture.player).orElseThrow();
        context.assertTrue(reading.totalGrams() == total, "随身实重错误：" + reading);
    }

    private static void verifyFatigue(TestContext context, BurdenTestPlayer loaded, BurdenTestPlayer empty) {
        double loadedFatigue = fatigue(loaded);
        double emptyFatigue = fatigue(empty);
        context.assertTrue(emptyFatigue > 0, "空身行走 10 秒没有产生腿肌疲劳");
        double ratio = loadedFatigue / emptyFatigue;
        LOGGER.info("左股直肌疲劳：负重 {}，空身 {}，比值 {}", loadedFatigue, emptyFatigue, ratio);
        context.assertTrue(Math.abs(ratio - DOUBLE_LOAD) < DOUBLE_LOAD * FATIGUE_RATIO_TOLERANCE,
                "负重与空身的腿肌疲劳比错误：" + loadedFatigue + " / " + emptyFatigue);
    }

    private static void verifyStatus(TestContext context, BurdenTestPlayer fixture) {
        // 经正式命令分发执行 /meridian status，总览末尾应附加负重说明。
        var server = fixture.player.getServer();
        server.getCommandManager().executeWithPrefix(fixture.player.getCommandSource(), "meridian status");
        List<String> lines = messages(fixture);
        context.assertTrue(lines.contains(SEVENTY_KILOGRAM_STATUS), "/meridian status 未附加负重说明：" + lines);
    }

    private static void verifyCompletions(TestContext context, CommandDispatcher<ServerCommandSource> dispatcher,
            ServerCommandSource operator, String name) {
        // 子命令名由字面节点补全，目标由原版玩家选择器补全在线玩家名。
        List<String> subcommands = suggestions(dispatcher, operator, "meridian bur");
        context.assertTrue(subcommands.contains("burden"), "负重命令名不能补全：" + subcommands);
        List<String> players = suggestions(dispatcher, operator, "meridian burden ");
        context.assertTrue(players.contains(name), "负重命令目标不能补全：" + players);
    }

    private static List<String> suggestions(CommandDispatcher<ServerCommandSource> dispatcher,
            ServerCommandSource source, String input) {
        // 与客户端 Tab 请求相同，先按执行者解析输入，再取光标处的补全。
        var parsed = dispatcher.parse(input, source);
        return dispatcher.getCompletionSuggestions(parsed).join().getList().stream()
                .map(Suggestion::getText)
                .toList();
    }

    private static List<String> messages(BurdenTestPlayer fixture) {
        // 聊天与命令反馈都以系统消息包发给玩家。
        return fixture.connection.packets.stream()
                .filter(GameMessageS2CPacket.class::isInstance)
                .map(packet -> ((GameMessageS2CPacket) packet).content().getString())
                .toList();
    }

    private static double fatigue(BurdenTestPlayer fixture) {
        // 与治疗附属相同的公开健康宿主查询，读取股直肌的实时疲劳。
        HealthHostRequest request = HealthHostRequest.newBuilder()
                .setProtocolVersion(1)
                .setRequestId(UUID.randomUUID().toString())
                .setSubjectId(fixture.player.getUuidAsString())
                .setInspect(true /* inspect */)
                .build();
        var response = HealthHostProtobuf.decodeResponse(new HumanHealthHost().exchange(
                HealthHostProtobuf.encodeRequest(request)));
        return response.getBody().getTissueStatesList().stream()
                .filter(tissue -> tissue.getId().equals(MUSCLE))
                .findFirst().orElseThrow().getFatigue();
    }
}
