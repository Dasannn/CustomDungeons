package dev.dasan.customdungeons.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NumericInputTest {
    @Test void integersAreExactAndRejectFractionsAndOverflow() {
        assertEquals(100, Inputs.parseInteger("100", 1, 100));
        for (String value : new String[]{"1.5", "1,0", "101", "0", "2147483648", ""})
            assertThrows(IllegalArgumentException.class, () -> Inputs.parseInteger(value, 1, 100));
    }
    @Test void decimalsAcceptCommaAndPointButRejectInvalidPrecisionAndRange() {
        assertEquals(.5, Inputs.parseDecimal(".5", 0, 1, 2));
        assertEquals(.5, Inputs.parseDecimal(",5", 0, 1, 2));
        assertEquals(12.5, Inputs.parseDecimal("12,5", 0, 100, 1));
        assertEquals(1000000000.0, Inputs.parseDecimal("1000000000.00", 0, 1e9, 2));
        for (String value : new String[]{"NaN", "Infinity", "1e2", "1.234", "-1", "1,2.3"})
            assertThrows(IllegalArgumentException.class, () -> Inputs.parseDecimal(value, 0, 100, 2));
    }
    @Test void blankResetsOnlyInputsWhoseRangeAllowsZero() {
        assertEquals(0, Inputs.parseDecimal("  ", 0, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> Inputs.parseDecimal("", 1, 2048, 1));
    }
    @Test void unboundedInputsAcceptEveryFiniteMagnitudeIncludingScientificNotation() {
        for(double value:new double[]{Double.MIN_VALUE,1e-100,.123456789123,5000,1e100,Double.MAX_VALUE})
            assertEquals(value,Inputs.parseDecimal(Double.toString(value),Double.MIN_VALUE,Double.MAX_VALUE,8));
        assertEquals(0,Inputs.parseDecimal("0",0,Double.MAX_VALUE,8));
        for(String invalid:new String[]{"NaN","Infinity","1e309","-1","0"})
            assertThrows(IllegalArgumentException.class,()->Inputs.parseDecimal(invalid,Double.MIN_VALUE,Double.MAX_VALUE,8));
    }
    @Test void formattingNeverUsesFloatForInteger() {
        assertEquals("42", Inputs.formatNumber(42, 0));
        assertEquals("0.10", Inputs.formatNumber(.1, 2));
    }
}
