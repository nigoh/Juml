// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package juml.core.aaos;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link VehiclePropertyCatalog} の定数抽出の単体テスト。
 */
public class VehiclePropertyCatalogTest {

    @Test
    public void loadsLowercaseHexConstant() {
        VehiclePropertyCatalog cat = new VehiclePropertyCatalog();
        cat.loadFromSource("public static final int FOO = 0x11400400;");
        assertEquals(0x11400400L, (long) cat.idOf("FOO").orElseThrow(AssertionError::new));
    }

    @Test
    public void loadsUppercaseHexConstant() {
        // 0X (大文字) の 16 進定数も取り込むこと (以前は 0x のみで取りこぼしていた)。
        VehiclePropertyCatalog cat = new VehiclePropertyCatalog();
        cat.loadFromSource("public static final int BAR = 0X11400500;");
        assertTrue("0X 定数が取り込まれるはず", cat.idOf("BAR").isPresent());
        assertEquals(0x11400500L, (long) cat.idOf("BAR").orElseThrow(AssertionError::new));
    }

    @Test
    public void loadsDecimalConstant() {
        VehiclePropertyCatalog cat = new VehiclePropertyCatalog();
        cat.loadFromSource("public static final int BAZ = 289408000;");
        assertEquals(289408000L, (long) cat.idOf("BAZ").orElseThrow(AssertionError::new));
    }

    @Test
    public void loadsHexWithUnderscores() {
        VehiclePropertyCatalog cat = new VehiclePropertyCatalog();
        cat.loadFromSource("public static final int SEP = 0X1140_0600;");
        assertEquals(0x11400600L, (long) cat.idOf("SEP").orElseThrow(AssertionError::new));
    }
}
