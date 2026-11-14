package dev.kizuna.meridian.burden.fabric;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** 参考负重只接受正的有限数、必须写；算不出时的重负荷倍率不小于 1、超载线大于 1，旧文件缺这两个键时取默认表的 4.2。 */
public final class BurdenSettingsTest {
    private static final double SHIPPED_REFERENCE_KG = 70;
    // 疾跑封顶那档，占位待试玩。
    private static final double SHIPPED_UNAVAILABLE_MULTIPLIER = 4.2;
    // 超载线默认也取疾跑封顶那档。
    private static final double SHIPPED_OVERLOAD_MULTIPLIER = 4.2;
    private static final double CUSTOM_REFERENCE_KG = 50;
    // 加 unavailable_multiplier 之前随 JAR 发布、已导出到服主配置目录的文件。
    private static final String OLD_EXPORT = """
            # 只允许这一个键，拼错或多出的键会让服务器拒绝启动。
            reference_kg = 50
            """;

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void shippedSettingsUseSeventyKilogramsAndTheSprintCapWhenUnavailable() throws IOException {
        // 读取随 JAR 发布的默认表，确认表中数值就是约定的参考负重、重负荷倍率和超载线，而且三个键都写全了。
        try (var source = BurdenSettings.class.getResourceAsStream(BurdenSettings.DEFAULT_RESOURCE)) {
            var parsed = BurdenSettings.parse(new String(source.readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(SHIPPED_REFERENCE_KG, parsed.settings().referenceKilograms(), 0);
            assertEquals(SHIPPED_UNAVAILABLE_MULTIPLIER, parsed.settings().unavailableMultiplier(), 0);
            assertEquals(SHIPPED_OVERLOAD_MULTIPLIER, parsed.settings().overloadMultiplier(), 0);
            assertEquals(List.of(), parsed.defaultedKeys());
        }
    }

    @Test
    public void integerAndDecimalValuesAreAccepted() {
        assertEquals(50, BurdenSettings.parse("reference_kg = 50").settings().referenceKilograms(), 0);
        assertEquals(42.5, BurdenSettings.parse("reference_kg = 42.5").settings().referenceKilograms(), 0);
        var decimal = BurdenSettings.parse("reference_kg = 70\nunavailable_multiplier = 4.5");
        assertEquals(4.5, decimal.settings().unavailableMultiplier(), 0);
        // 写 1 是服主明确选择算不出时不计负重，允许。
        var unloaded = BurdenSettings.parse("reference_kg = 70\nunavailable_multiplier = 1");
        assertEquals(BurdenReading.UNLOADED, unloaded.settings().unavailableMultiplier(), 0);
        // 超载线和算不出时的倍率各管各的：改一个不动另一个。
        var overload = BurdenSettings.parse("reference_kg = 70\noverload_multiplier = 3");
        assertEquals(3, overload.settings().overloadMultiplier(), 0);
        assertEquals(SHIPPED_UNAVAILABLE_MULTIPLIER, overload.settings().unavailableMultiplier(), 0);
        assertEquals(SHIPPED_OVERLOAD_MULTIPLIER, unloaded.settings().overloadMultiplier(), 0);
    }

    @Test
    public void oldExportWithoutUnavailableMultiplierRunsOnShippedDefault() throws IOException {
        // 旧导出文件不会自动得到新键：读取时按默认表补上并列出缺的键，文件本身保持原样。
        var path = folder.getRoot().toPath().resolve(BurdenSettings.FILE_NAME);
        Files.writeString(path, OLD_EXPORT, StandardCharsets.UTF_8);
        var parsed = BurdenSettings.parse(OLD_EXPORT);
        assertEquals(List.of("unavailable_multiplier", "overload_multiplier"), parsed.defaultedKeys());
        assertEquals(CUSTOM_REFERENCE_KG, parsed.settings().referenceKilograms(), 0);
        assertEquals(SHIPPED_UNAVAILABLE_MULTIPLIER, parsed.settings().unavailableMultiplier(), 0);
        assertEquals(SHIPPED_OVERLOAD_MULTIPLIER, parsed.settings().overloadMultiplier(), 0);
        // 上一版导出的文件有前两个键，只缺超载线。
        var previous = BurdenSettings.parse("reference_kg = 50\nunavailable_multiplier = 3");
        assertEquals(List.of("overload_multiplier"), previous.defaultedKeys());
        assertEquals(SHIPPED_OVERLOAD_MULTIPLIER, previous.settings().overloadMultiplier(), 0);
        assertEquals(parsed.settings(), BurdenSettings.load(path));
        assertEquals(OLD_EXPORT, Files.readString(path, StandardCharsets.UTF_8));
    }

    @Test
    public void missingFileGetsTheShippedTable() throws IOException {
        // 首次启动复制默认表，复制出的文件三个键都在，不再告警。
        var path = folder.getRoot().toPath().resolve("config").resolve(BurdenSettings.FILE_NAME);
        var settings = BurdenSettings.load(path);
        assertEquals(SHIPPED_UNAVAILABLE_MULTIPLIER, settings.unavailableMultiplier(), 0);
        var copied = BurdenSettings.parse(Files.readString(path, StandardCharsets.UTF_8));
        assertEquals(List.of(), copied.defaultedKeys());
    }

    @Test
    public void invalidReferencesAreRejected() {
        // 零、负数、nan、inf、字符串、缺参考负重、多余键和语法错误都拒绝整份设置。
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse("reference_kg = 0"));
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse("reference_kg = -70"));
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse("reference_kg = nan"));
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse("reference_kg = inf"));
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse("reference_kg = \"70\""));
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse(""));
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse("unavailable_multiplier = 4.2"));
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse("reference_kg = 70\nrefrence_kg = 70"));
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse("reference_kg = = 70"));
    }

    @Test
    public void invalidUnavailableMultipliersAreRejected() {
        // 小于 1、nan、inf、字符串和拼错的键都拒绝，不悄悄退回默认值。
        assertThrows(IllegalArgumentException.class, () -> unavailable("0.5"));
        assertThrows(IllegalArgumentException.class, () -> unavailable("0"));
        assertThrows(IllegalArgumentException.class, () -> unavailable("nan"));
        assertThrows(IllegalArgumentException.class, () -> unavailable("inf"));
        assertThrows(IllegalArgumentException.class, () -> unavailable("\"4.2\""));
        assertThrows(IllegalArgumentException.class,
                () -> BurdenSettings.parse("reference_kg = 70\nunavailable_multipler = 4.2"));
    }

    @Test
    public void invalidOverloadMultipliersAreRejected() {
        // 超载线等于 1 时负重上限是 0 kg，和小于 1、nan、inf、字符串、拼错的键一样拒绝。
        assertThrows(IllegalArgumentException.class, () -> overload("1"));
        assertThrows(IllegalArgumentException.class, () -> overload("0.5"));
        assertThrows(IllegalArgumentException.class, () -> overload("nan"));
        assertThrows(IllegalArgumentException.class, () -> overload("inf"));
        assertThrows(IllegalArgumentException.class, () -> overload("\"4.2\""));
        assertThrows(IllegalArgumentException.class,
                () -> BurdenSettings.parse("reference_kg = 70\noverload_multipler = 4.2"));
    }

    private static BurdenSettings.Parsed overload(String value) {
        return BurdenSettings.parse("reference_kg = 70\noverload_multiplier = " + value);
    }

    private static BurdenSettings.Parsed unavailable(String value) {
        return BurdenSettings.parse("reference_kg = 70\nunavailable_multiplier = " + value);
    }
}
