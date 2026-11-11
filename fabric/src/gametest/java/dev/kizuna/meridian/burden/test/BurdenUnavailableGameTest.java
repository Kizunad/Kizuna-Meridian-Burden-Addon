package dev.kizuna.meridian.burden.test;

import dev.kizuna.meridian.burden.fabric.BurdenLoad;
import dev.kizuna.meridian.burden.fabric.BurdenReading;
import dev.kizuna.meridian.human.fabric.HumanHealthHost;
import dev.kizuna.meridian.protocol.HealthHostProtobuf;
import dev.kizuna.meridian.protocol.pb.HealthHostMessages.HealthHostRequest;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static dev.kizuna.meridian.burden.test.BurdenGameTestTicks.DEFAULT_TIMEOUT;

/** Weights 算不出负重时按设置里的重负荷结算：超出随身容器上限、恢复、先空身再装满三种情形。 */
public final class BurdenUnavailableGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("meridian_burden_test");
    // Weights 随身容器上限 32 个（BS3D ContainerItems.MAX_CONTAINERS），第 33 个灵草囊让它判为算不出。
    private static final Identifier POUCH = Identifier.fromNamespaceAndPath("bs3d", "herb_pouch");
    private static final int CONTAINER_LIMIT = 32;
    // 生存里凑得出的摆法：快捷栏第 0 格一个装满灵草囊的潜影盒，第 1 格起再放几个，共 33 个。
    private static final int SHULKER_SLOT = 0;
    private static final int SHULKER_POUCHES = 27;
    private static final int FIRST_LOOSE_POUCH_SLOT = 1;
    private static final int LOOSE_POUCHES = CONTAINER_LIMIT + 1 - SHULKER_POUCHES;
    private static final int LAST_LOOSE_POUCH_SLOT = FIRST_LOOSE_POUCH_SLOT + LOOSE_POUCHES - 1;
    // 整合包装了 Pocket：主背包只剩 9–17 格可用，18–35 格的物品会被旧槽整理收走。
    private static final int FIRST_MAIN_SLOT = 9;
    private static final int FULL_STACK = 64;
    // 随 JAR 默认表：reference_kg = 70，unavailable_multiplier = 4.2（疾跑封顶那档，占位待试玩）。
    private static final double UNAVAILABLE_MULTIPLIER = 4.2;
    private static final double UNLOADED = 1;
    private static final double DOUBLE_LOAD = 2;
    private static final double MULTIPLIER_TOLERANCE = 1e-9;
    // Weights 内置石头 1000 克、主库存系数 2：112 块即 224 kg 有效负重，倍率 1 + 224 / 70 = 4.2，与重负荷同档。
    // 112 块占第 9、10 格（1 组加 48 块）。
    private static final int EQUAL_LOAD_STONES = 112;
    private static final long EQUAL_LOAD_GRAMS = 224_000;
    // 35 块石头放主库存即 70 kg 有效负重，倍率 2；快捷栏系数 0，那里的潜影盒和灵草囊不计有效负重。
    private static final int SEVENTY_KILOGRAM_STONES = 35;
    private static final long SEVENTY_KILOGRAMS = 70_000;
    // Weights 每 5 个服务端 tick 最多重算一次，改动库存后多等一 tick 再读。
    private static final long READING_REFRESH_TICKS = 6;
    private static final long CHANGED_READING_TICK = READING_REFRESH_TICKS * 2;
    private static final long WALK_START_TICK = READING_REFRESH_TICKS;
    private static final long WALK_TICKS = 200;
    private static final long WALK_END_TICK = WALK_START_TICK + WALK_TICKS;
    private static final int WALK_TEST_TICK_LIMIT = 300;
    // 每 tick 0.05 格：远小于 Human 的瞬移阈值，又明显大于静止噪声。
    private static final double STEP_BLOCKS = 0.05;
    private static final Vec3 OVER_LIMIT_START = new Vec3(1.5, 2, 1.5);
    private static final Vec3 EQUAL_LOAD_START = new Vec3(1.5, 2, 3.5);
    private static final String MUSCLE = "left_rectus_femoris";
    private static final int HEALTH_HOST_PROTOCOL_VERSION = 1;
    // 两具身体在相同 tick 起步、停步、倍率相同，只留 Human 结算边界的少量浮点误差。
    private static final double FATIGUE_RATIO_TOLERANCE = 0.05;
    private static final String UNAVAILABLE_STATUS =
            "负重算不出（随身容器或物品太多、套得太深或物品数据异常），按重负荷计，行走负荷 ×4.20";
    private static final String SEVENTY_KILOGRAM_STATUS = "负重 70.0 kg，行走负荷 ×2.00";

    @GameTest(maxTicks = WALK_TEST_TICK_LIMIT)
    public void tooManyContainersWalkLikeAnEqualLegalLoad(GameTestHelper context) {
        // 一人带 33 个灵草囊、一人背 224 kg 有效负重的石头（倍率同为 4.2），并排走同样 10 秒，股直肌疲劳应当一样。
        var overLimit = new BurdenTestPlayer(context);
        var equalLoad = new BurdenTestPlayer(context);
        carryTooManyContainers(context, overLimit);
        carryStones(equalLoad, EQUAL_LOAD_STONES);
        Vec3 overLimitStart = context.absoluteVec(OVER_LIMIT_START);
        Vec3 equalLoadStart = context.absoluteVec(EQUAL_LOAD_START);
        context.failIfEver(() -> {
            long walkedTicks = Math.max(0, Math.min(context.getTick(), WALK_END_TICK) - WALK_START_TICK);
            step(overLimit, overLimitStart, walkedTicks);
            step(equalLoad, equalLoadStart, walkedTicks);
        });
        context.runAtTickTime(WALK_START_TICK, () -> {
            verifyUnavailable(context, overLimit);
            verifyReading(context, equalLoad, EQUAL_LOAD_GRAMS, UNAVAILABLE_MULTIPLIER);
        });
        context.runAtTickTime(WALK_END_TICK, () -> {
            try (overLimit; equalLoad) {
                verifySameFatigue(context, overLimit, equalLoad);
                verifyStatus(context, overLimit, UNAVAILABLE_STATUS);
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = DEFAULT_TIMEOUT)
    public void droppingTheExtraContainersRestoresTheNormalReading(GameTestHelper context) {
        // 33 个灵草囊加 70 kg 石头时按重负荷；拿走一个、正好 32 个时，下一次重算就回到 70 kg、×2。
        var fixture = new BurdenTestPlayer(context);
        carryTooManyContainers(context, fixture);
        carryStones(fixture, SEVENTY_KILOGRAM_STONES);
        context.runAtTickTime(READING_REFRESH_TICKS, () -> {
            verifyUnavailable(context, fixture);
            verifyStatus(context, fixture, UNAVAILABLE_STATUS);
            fixture.player.getInventory().setItem(LAST_LOOSE_POUCH_SLOT, ItemStack.EMPTY);
        });
        context.runAtTickTime(CHANGED_READING_TICK, () -> {
            try (fixture) {
                verifyReading(context, fixture, SEVENTY_KILOGRAMS, DOUBLE_LOAD);
                verifyStatus(context, fixture, SEVENTY_KILOGRAM_STATUS);
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = DEFAULT_TIMEOUT)
    public void emptyReadingFirstDoesNotCarryOverToAnOverfullInventory(GameTestHelper context) {
        // 先空身读出 ×1，再一次塞进 33 个灵草囊和一组石头：不能沿用空身读数，必须是重负荷。
        var fixture = new BurdenTestPlayer(context);
        context.runAtTickTime(READING_REFRESH_TICKS, () -> {
            verifyReading(context, fixture, 0, UNLOADED);
            carryTooManyContainers(context, fixture);
            carryStones(fixture, FULL_STACK);
        });
        context.runAtTickTime(CHANGED_READING_TICK, () -> {
            try (fixture) {
                verifyUnavailable(context, fixture);
                verifyStatus(context, fixture, UNAVAILABLE_STATUS);
            }
            context.succeed();
        });
    }

    private static void carryTooManyContainers(GameTestHelper context, BurdenTestPlayer fixture) {
        // Weights 会展开潜影盒，盒里的灵草囊和快捷栏上的一样计入容器数。
        var inventory = fixture.player.getInventory();
        var shulker = new ItemStack(Items.SHULKER_BOX);
        var boxed = ItemContainerContents.fromItems(pouches(context, fixture, SHULKER_POUCHES));
        shulker.set(DataComponents.CONTAINER, boxed);
        inventory.setItem(SHULKER_SLOT, shulker);
        List<ItemStack> loose = pouches(context, fixture, LOOSE_POUCHES);
        for (int index = 0; index < loose.size(); index++) {
            inventory.setItem(FIRST_LOOSE_POUCH_SLOT + index, loose.get(index));
        }
    }

    private static List<ItemStack> pouches(GameTestHelper context, BurdenTestPlayer fixture, int count) {
        // GameTest 编译不到 BS3D，按注册表 id 造灵草囊。容器身份由 BS3D 在原版库存 tick 里分配，
        // 测试夹具的玩家不跑这一步（实测一直报"容器物品缺少有效身份"），这里替每个灵草囊跑一次。
        context.assertTrue(BuiltInRegistries.ITEM.containsKey(POUCH), "未注册 " + POUCH);
        var item = BuiltInRegistries.ITEM.getValue(POUCH);
        List<ItemStack> pouches = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            var pouch = new ItemStack(item);
            pouch.inventoryTick(fixture.player.level(), fixture.player, null /* 不在装备格 */);
            pouches.add(pouch);
        }
        return pouches;
    }

    private static void carryStones(BurdenTestPlayer fixture, int stones) {
        // 从第 9 格起逐格放满整组，最后一格放余数。
        int slot = FIRST_MAIN_SLOT;
        for (int left = stones; left > 0; left -= FULL_STACK) {
            fixture.player.getInventory().setItem(slot, new ItemStack(Items.STONE, Math.min(left, FULL_STACK)));
            slot++;
        }
    }

    private static void step(BurdenTestPlayer fixture, Vec3 start, long walkedTicks) {
        // 服务端玩家不自行模拟移动；每 tick 在 Human 采样前写入位置和着地状态。
        Vec3 position = start.add(walkedTicks * STEP_BLOCKS, 0, 0);
        fixture.player.setPos(position);
        fixture.player.setOnGround(true /* onGround */);
    }

    private static void verifyUnavailable(GameTestHelper context, BurdenTestPlayer fixture) {
        // 读数为空说明 Weights 判为算不出；Human 拿到的倍率必须是重负荷，而不是无负荷或上一次读数。
        var reading = BurdenLoad.read(fixture.player);
        context.assertTrue(reading.isEmpty(), "超出随身容器上限仍有读数：" + reading);
        double multiplier = BurdenLoad.currentMultiplier(fixture.player);
        context.assertTrue(Math.abs(multiplier - UNAVAILABLE_MULTIPLIER) < MULTIPLIER_TOLERANCE,
                "算不出时的行走负荷倍率错误：" + multiplier);
    }

    private static void verifyReading(GameTestHelper context, BurdenTestPlayer fixture, long effective, double load) {
        BurdenReading reading = BurdenLoad.read(fixture.player).orElseThrow();
        context.assertTrue(reading.effectiveGrams() == effective, "有效负重错误：" + reading);
        context.assertTrue(Math.abs(reading.multiplier() - load) < MULTIPLIER_TOLERANCE, "负荷倍率错误：" + reading);
        double multiplier = BurdenLoad.currentMultiplier(fixture.player);
        context.assertTrue(multiplier == reading.multiplier(), "Human 拿到的倍率与读数不符：" + multiplier);
    }

    private static void verifyStatus(GameTestHelper context, BurdenTestPlayer fixture, String expected) {
        // 经正式命令分发执行 /meridian body status，只看这一次命令新发出的消息。
        int before = messages(fixture).size();
        var server = fixture.player.level().getServer();
        server.getCommands().performPrefixedCommand(fixture.player.createCommandSourceStack(), "meridian body status");
        List<String> all = messages(fixture);
        List<String> lines = all.subList(before, all.size());
        context.assertTrue(lines.contains(expected), "/meridian body status 的负重说明错误：" + lines);
    }

    private static void verifySameFatigue(GameTestHelper context, BurdenTestPlayer overLimit,
            BurdenTestPlayer equalLoad) {
        double overLimitFatigue = fatigue(overLimit);
        double equalLoadFatigue = fatigue(equalLoad);
        context.assertTrue(equalLoadFatigue > 0, "背重物行走 10 秒没有产生腿肌疲劳");
        double ratio = overLimitFatigue / equalLoadFatigue;
        LOGGER.info("左股直肌疲劳：超容器上限 {}，224 kg {}，比值 {}", overLimitFatigue, equalLoadFatigue, ratio);
        context.assertTrue(Math.abs(ratio - UNLOADED) < FATIGUE_RATIO_TOLERANCE,
                "超容器上限与同倍率合法负重的腿肌疲劳不同：" + overLimitFatigue + " / " + equalLoadFatigue);
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
                .setProtocolVersion(HEALTH_HOST_PROTOCOL_VERSION)
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
