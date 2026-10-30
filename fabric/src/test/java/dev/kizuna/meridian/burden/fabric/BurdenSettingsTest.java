package dev.kizuna.meridian.burden.fabric;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** 参考负重只接受正的有限数，默认表就是任务约定的 70 kg。 */
public final class BurdenSettingsTest {
    private static final double SHIPPED_REFERENCE_KG = 70;

    @Test
    public void shippedSettingsUseSeventyKilograms() throws IOException {
        // 读取随 JAR 发布的默认表，确认表中数值就是约定的参考负重。
        try (var source = BurdenSettings.class.getResourceAsStream(BurdenSettings.DEFAULT_RESOURCE)) {
            var text = new String(source.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(SHIPPED_REFERENCE_KG, BurdenSettings.parse(text).referenceKilograms(), 0);
        }
    }

    @Test
    public void integerAndDecimalReferencesAreAccepted() {
        assertEquals(50, BurdenSettings.parse("reference_kg = 50").referenceKilograms(), 0);
        assertEquals(42.5, BurdenSettings.parse("reference_kg = 42.5").referenceKilograms(), 0);
    }

    @Test
    public void invalidSettingsAreRejected() {
        // 零、负数、nan、inf、字符串、缺键、多余键和语法错误都拒绝整份设置。
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse("reference_kg = 0"));
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse("reference_kg = -70"));
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse("reference_kg = nan"));
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse("reference_kg = inf"));
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse("reference_kg = \"70\""));
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse(""));
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse("reference_kg = 70\nrefrence_kg = 70"));
        assertThrows(IllegalArgumentException.class, () -> BurdenSettings.parse("reference_kg = = 70"));
    }
}
