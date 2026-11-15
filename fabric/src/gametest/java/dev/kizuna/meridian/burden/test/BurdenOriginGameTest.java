package dev.kizuna.meridian.burden.test;

import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.JsonOps;
import dev.kizuna.meridian.burden.fabric.BurdenLoad;
import dev.kizuna.meridian.burden.fabric.BurdenOrigins;
import dev.kizuna.meridian.burden.fabric.BurdenView;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static dev.kizuna.meridian.burden.test.BurdenGameTestTicks.DEFAULT_TIMEOUT;

/**
 * 出身天赋 {@code meridian_burden:carry_capacity}：参考负重按倍数变，同样 70 kg 的有效负重走起来负荷更低，
 * 发给客户端的读数也换成这名玩家的参考负重；倍数写成零、负数或漏写就让出身读不进来。
 */
public final class BurdenOriginGameTest {
    private static final String ORIGIN = "meridian_burden_test:peddler";
    private static final int FIRST_MAIN_SLOT = 9;
    // Weights 内置石头 1000 克，主库存系数 2：35 块即 70 kg 有效负重，等于默认参考负重。
    private static final int SEVENTY_KILOGRAM_STONES = 35;
    private static final double DEFAULT_LOAD = 2;
    // 测试出身把参考负重放大到 1.25 倍：87.5 kg，倍率 = 1 + 70 / 87.5。
    private static final double SCALED_REFERENCE_KILOGRAMS = 87.5;
    private static final double SCALED_LOAD = 1.8;
    private static final double TOLERANCE = 1e-9;
    // Weights 每 5 个服务端 tick 最多重算一次，放入石头后多等一 tick 再读。
    private static final long READING_REFRESH_TICKS = 6;
    // 同步每 10 tick 看一次：定下出身后等 20 tick，新读数一定已经发出。
    private static final long SYNC_SETTLE_TICKS = 20;

    @GameTest(maxTicks = DEFAULT_TIMEOUT)
    public void carryCapacityLowersLoadOfSameWeight(GameTestHelper context) {
        // 背 70 kg：没定出身是 ×2.00；定了带 1.25 倍参考负重的出身，当场变成 ×1.80，客户端读数跟着换。
        var fixture = new BurdenTestPlayer(context);
        fixture.player.getInventory().setItem(FIRST_MAIN_SLOT, new ItemStack(Items.STONE, SEVENTY_KILOGRAM_STONES));
        context.runAtTickTime(READING_REFRESH_TICKS, () -> {
            requireLoad(context, fixture, DEFAULT_LOAD, "没定出身时 70 kg 的负荷倍率不对");
            choose(context, fixture);
            requireLoad(context, fixture, SCALED_LOAD, "定了出身，参考负重没跟着变");
        });
        context.runAtTickTime(READING_REFRESH_TICKS + SYNC_SETTLE_TICKS, () -> {
            try (fixture) {
                List<BurdenView> views = fixture.burdenViews();
                BurdenView last = views.get(views.size() - 1);
                context.assertTrue(Math.abs(last.referenceKilograms() - SCALED_REFERENCE_KILOGRAMS) < TOLERANCE
                        && Math.abs(last.multiplier() - SCALED_LOAD) < TOLERANCE, "发给客户端的读数没换成出身的参考负重：" + last);
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = DEFAULT_TIMEOUT)
    public void carryCapacityRejectsNonPositiveOrMissingScale(GameTestHelper context) {
        // 读出身数据时就拒：参考负重是除数，倍数得是正数，也不能漏写。
        requireParsed(context, "{\"reference_scale\":1.25}");
        requireRejected(context, "{\"reference_scale\":0}", "倍数为零没被拒");
        requireRejected(context, "{\"reference_scale\":-1}", "倍数为负没被拒");
        requireRejected(context, "{}", "漏写倍数没被拒");
        context.succeed();
    }

    private static void choose(GameTestHelper context, BurdenTestPlayer fixture) {
        // 走出身附属的管理指令定出身，常驻天赋随即生效。
        MinecraftServer server = context.getLevel().getServer();
        String command = "meridian debug origin set " + fixture.player.getScoreboardName() + " " + ORIGIN;
        try {
            int result = server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack());
            context.assertTrue(result > 0, "定出身的指令没成：" + command);
        } catch (CommandSyntaxException error) {
            context.fail("定出身的指令读不懂：" + error.getMessage());
        }
    }

    private static void requireLoad(GameTestHelper context, BurdenTestPlayer fixture, double expected, String message) {
        // 读的是 Human 结算走跑时拿的同一个倍率。
        double actual = BurdenLoad.currentMultiplier(fixture.player);
        context.assertTrue(Math.abs(actual - expected) < TOLERANCE, message + "：×" + actual);
    }

    private static void requireParsed(GameTestHelper context, String json) {
        // 写对的参数能读出来。
        var result = BurdenOrigins.CARRY_CAPACITY.params().parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        context.assertTrue(result.isSuccess(), "写对的倍数读不出来：" + json);
    }

    private static void requireRejected(GameTestHelper context, String json, String message) {
        // 参数解码失败就说明出身数据包会在这一条上读取失败。
        var result = BurdenOrigins.CARRY_CAPACITY.params().parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        context.assertTrue(result.isError(), message + "：" + json);
    }
}
