package dev.kizuna.meridian.burden.fabric;

import dev.kizuna.bs3d.weights.server.Burden;
import dev.kizuna.meridian.human.fabric.HumanMovementLoad;
import dev.kizuna.meridian.human.fabric.HumanMovementLoads;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import net.minecraft.server.level.ServerPlayer;

/** 把 Weights 的有效负重换算为 Human 行走负荷；Weights 算不出时按设置里的重负荷结算。 */
public final class BurdenLoad implements HumanMovementLoad {
    private static final String STATUS_FORMAT = "负重 %.1f kg，行走负荷 ×%.2f";
    // Weights 只报"算不出"、不分原因，这里列出它会判为算不出的几种情况，玩家据此整理随身物品。
    private static final String UNAVAILABLE_FORMAT =
            "负重算不出（随身容器或物品太多、套得太深或物品数据异常），按重负荷计，行走负荷 ×%.2f";
    private static volatile BurdenLoad installed;
    private final BurdenSettings settings;

    private BurdenLoad(BurdenSettings settings) {
        this.settings = settings;
    }

    static void install(BurdenSettings settings) {
        // 模组初始化时只安装一次；Human 拒绝重复注册，读取 API 与提供者共用同一份设置。
        var load = new BurdenLoad(settings);
        HumanMovementLoads.register(load);
        installed = load;
    }

    /** 当前读数；Weights 判定库存不可用时为空。只能在逻辑服务器主线程调用。 */
    public static Optional<BurdenReading> read(ServerPlayer player) {
        return installed().reading(player);
    }

    /** Human 拿到的行走负荷倍率；Weights 算不出时是设置里的重负荷倍率。只能在逻辑服务器主线程调用。 */
    public static double currentMultiplier(ServerPlayer player) {
        return installed().multiplier(player);
    }

    @Override
    public double multiplier(ServerPlayer player) {
        // 每次都按 Weights 当次结果：算不出就是重负荷，不沿用上一次读数，先空身读一次再装满也拿不到轻负荷。
        return reading(player).map(BurdenReading::multiplier).orElse(settings.unavailableMultiplier());
    }

    @Override
    public Optional<String> status(ServerPlayer player) {
        // 算不出时说明可能的原因和按哪档结算，不显示成 0 kg。
        String line = reading(player).map(BurdenLoad::describe).orElseGet(this::describeUnavailable);
        return Optional.of(line);
    }

    private static BurdenLoad installed() {
        BurdenLoad load = installed;
        if (load == null) {
            throw new IllegalStateException("负重附属尚未初始化");
        }
        return load;
    }

    private Optional<BurdenReading> reading(ServerPlayer player) {
        // Weights 每 5 个服务端 tick 最多重算一次，同一 tick 内的实重与有效负重读自同一份结果。
        OptionalLong effective = Burden.effectiveGrams(player);
        OptionalLong total = Burden.totalGrams(player);
        if (effective.isEmpty() || total.isEmpty()) {
            return Optional.empty();
        }
        BurdenSettings own = BurdenOrigins.settingsFor(player, settings);
        return Optional.of(BurdenReading.of(total.getAsLong(), effective.getAsLong(), own));
    }

    private static String describe(BurdenReading reading) {
        double effectiveKilograms = reading.effectiveGrams() / BurdenReading.GRAMS_PER_KILOGRAM;
        return String.format(Locale.ROOT, STATUS_FORMAT, effectiveKilograms, reading.multiplier());
    }

    private String describeUnavailable() {
        return String.format(Locale.ROOT, UNAVAILABLE_FORMAT, settings.unavailableMultiplier());
    }
}
