package dev.kizuna.meridian.burden.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** `/meridian burden [targets]`：管理员查看玩家的实重、有效负重与行走负荷倍率。 */
final class BurdenCommand {
    // 与 Progression、Threatening 挂在 /meridian 下的管理分支相同，要求权限 2。
    static final int PERMISSION_LEVEL = 2;
    private static final String LINE_FORMAT = "%s：实重 %.1f kg，有效负重 %.1f kg，行走负荷 ×%.2f";
    private static final String UNAVAILABLE_SUFFIX = "：负重暂不可用，Weights 判定库存损坏或重量溢出";

    private BurdenCommand() {
        // 只构造命令分支，不保存玩家状态。
    }

    static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        // Brigadier 按名字合并 /meridian 根节点；根上不加权限，其他附属的分支保持原样。
        var targets = CommandManager.argument("targets", EntityArgumentType.players())
                .executes(context -> print(context.getSource(), EntityArgumentType.getPlayers(context, "targets")));
        var burden = CommandManager.literal("burden")
                .requires(source -> source.hasPermissionLevel(PERMISSION_LEVEL))
                .executes(BurdenCommand::printSelf)
                .then(targets);
        dispatcher.register(CommandManager.literal("meridian").then(burden));
    }

    private static int printSelf(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        // 省略目标时查看执行者；控制台执行时由原版报告需要玩家。
        ServerCommandSource source = context.getSource();
        return print(source, List.of(source.getPlayerOrThrow()));
    }

    private static int print(ServerCommandSource source, Collection<ServerPlayerEntity> players) {
        // 每名玩家一行；库存不可算时报告失败，不显示成 0 kg。
        int shown = 0;
        for (ServerPlayerEntity player : players) {
            String name = player.getName().getString();
            Optional<BurdenReading> reading = BurdenLoad.read(player);
            if (reading.isEmpty()) {
                source.sendError(Text.literal(name + UNAVAILABLE_SUFFIX));
                continue;
            }
            String line = describe(name, reading.get());
            source.sendFeedback(() -> Text.literal(line), false /* broadcastToOps */);
            shown++;
        }
        return shown;
    }

    private static String describe(String name, BurdenReading reading) {
        double totalKilograms = reading.totalGrams() / BurdenReading.GRAMS_PER_KILOGRAM;
        double effectiveKilograms = reading.effectiveGrams() / BurdenReading.GRAMS_PER_KILOGRAM;
        return String.format(Locale.ROOT, LINE_FORMAT, name, totalKilograms, effectiveKilograms,
                reading.multiplier());
    }
}
