package dev.kizuna.meridian.burden.fabric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tomlj.Toml;
import org.tomlj.TomlTable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * meridian-burden.toml 的设置。
 *
 * @param referenceKilograms 使行走负荷倍率增加 1 的有效负重，千克
 * @param unavailableMultiplier Weights 算不出负重时结算用的行走负荷倍率，不小于 1
 * @param overloadMultiplier 读数里算作超载的行走负荷倍率，大于 1；只用来分档和显示负重上限，不参与结算
 */
record BurdenSettings(double referenceKilograms, double unavailableMultiplier, double overloadMultiplier) {
    static final String FILE_NAME = "meridian-burden.toml";
    /** 随 JAR 提供的默认设置，首次启动时复制到配置目录，旧文件缺后加的键时也从这里取值。 */
    static final String DEFAULT_RESOURCE = "/meridian-burden.toml";
    private static final Logger LOGGER = LoggerFactory.getLogger("meridian_burden");
    private static final String REFERENCE_KEY = "reference_kg";
    private static final String UNAVAILABLE_KEY = "unavailable_multiplier";
    private static final String OVERLOAD_KEY = "overload_multiplier";
    private static final Set<String> KEYS = Set.of(REFERENCE_KEY, UNAVAILABLE_KEY, OVERLOAD_KEY);
    // 第一版之后才加的键：旧版本导出的设置文件里没有，缺了按默认表运行。参考负重第一版就有，仍须显式配置。
    private static final List<String> ADDED_KEYS = List.of(UNAVAILABLE_KEY, OVERLOAD_KEY);

    BurdenSettings {
        // 参考负重是除数，零、负数和非有限数都会让倍率失真。
        if (!Double.isFinite(referenceKilograms) || referenceKilograms <= 0) {
            throw new IllegalArgumentException(REFERENCE_KEY + " 必须是正的有限数：" + referenceKilograms);
        }
        // Human 拒绝小于 1 或非有限的负荷倍率，在启动时就拦下。
        if (!Double.isFinite(unavailableMultiplier) || unavailableMultiplier < BurdenReading.UNLOADED) {
            throw new IllegalArgumentException(UNAVAILABLE_KEY + " 必须是不小于 1 的有限数：" + unavailableMultiplier);
        }
        // 超载线换算成千克就是显示的负重上限，等于 1 时上限是 0 kg，没有意义。
        if (!Double.isFinite(overloadMultiplier) || overloadMultiplier <= BurdenReading.UNLOADED) {
            throw new IllegalArgumentException(OVERLOAD_KEY + " 必须是大于 1 的有限数：" + overloadMultiplier);
        }
    }

    /**
     * 一份设置文件的解析结果。
     *
     * @param settings 生效的设置
     * @param defaultedKeys 文件里缺了、按默认表取值的键
     */
    record Parsed(BurdenSettings settings, List<String> defaultedKeys) {}

    static BurdenSettings load(Path path) {
        // 首次启动复制默认表；之后只读取，不覆盖服主调整过的文件，旧文件缺的键只在日志里列出。
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                try (InputStream source = BurdenSettings.class.getResourceAsStream(DEFAULT_RESOURCE)) {
                    Files.copy(source, path);
                }
            }
            Parsed parsed = parse(Files.readString(path, StandardCharsets.UTF_8));
            if (!parsed.defaultedKeys().isEmpty()) {
                LOGGER.warn("负重设置 {} 缺少键 {}，按默认值运行：{}；可照模组自带的 {} 补上", path,
                        parsed.defaultedKeys(), parsed.settings(), FILE_NAME);
            }
            return parsed.settings();
        } catch (IOException failure) {
            throw new UncheckedIOException("无法读取负重设置 " + path, failure);
        }
    }

    static Parsed parse(String text) {
        // 语法错误、拼错或多出的键、缺参考负重都拒绝整份设置；后加的键缺了才取默认表的值，代码里不写默认数。
        TomlTable table = table(text);
        if (!KEYS.containsAll(table.keySet())) {
            throw new IllegalArgumentException("负重设置只能包含 " + KEYS + "：" + table.keySet());
        }
        if (!table.contains(REFERENCE_KEY)) {
            throw new IllegalArgumentException("负重设置必须包含 " + REFERENCE_KEY);
        }
        List<String> defaulted = ADDED_KEYS.stream().filter(key -> !table.contains(key)).toList();
        double unavailable = number(withKey(table, UNAVAILABLE_KEY), UNAVAILABLE_KEY);
        double overload = number(withKey(table, OVERLOAD_KEY), OVERLOAD_KEY);
        var settings = new BurdenSettings(number(table, REFERENCE_KEY), unavailable, overload);
        return new Parsed(settings, defaulted);
    }

    private static TomlTable withKey(TomlTable table, String key) {
        // 文件里有这个键就用文件的值，没有就用默认表的值。
        if (table.contains(key)) {
            return table;
        }
        return shipped();
    }

    private static TomlTable shipped() {
        // 默认表随 JAR 发布，读不到说明打包错误。
        try (InputStream source = BurdenSettings.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            return table(new String(source.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new UncheckedIOException("无法读取模组自带的负重设置", failure);
        }
    }

    private static TomlTable table(String text) {
        var table = Toml.parse(text);
        if (table.hasErrors()) {
            throw new IllegalArgumentException("负重设置不是合法 TOML：" + table.errors().get(0).getMessage());
        }
        return table;
    }

    private static double number(TomlTable table, String key) {
        // 整数和小数都接受，字符串等其他类型拒绝。
        Object value = table.get(key);
        if (!(value instanceof Long) && !(value instanceof Double)) {
            throw new IllegalArgumentException(key + " 必须是数字");
        }
        return ((Number) value).doubleValue();
    }
}
