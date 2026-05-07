package io.wiretap.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskUtilTest {

    @Test
    void maskAllPans_validPanWithLuhn_isMasked() {
        // Valid Visa-style PAN passing the Luhn check.
        String input = "card 4539578763621486 paid";

        String masked = MaskUtil.maskAllPans(input, true);

        assertThat(masked).contains("453957******1486").doesNotContain("4539578763621486");
    }

    @Test
    void maskAllPans_invalidLuhn_keepsOriginal() {
        String input = "tracking 1234567890123456";

        assertThat(MaskUtil.maskAllPans(input, true)).isEqualTo(input);
    }

    @Test
    void maskAllPans_invalidLuhnButLuhnDisabled_isMasked() {
        String input = "tracking 1234567890123456";

        String masked = MaskUtil.maskAllPans(input, false);

        assertThat(masked).contains("123456******3456").doesNotContain("1234567890123456");
    }

    @Test
    void removeAllPans_dropsAnythingPanShaped() {
        String input = "card 4539578763621486 paid";

        assertThat(MaskUtil.removeAllPans(input)).isEqualTo("card  paid");
    }

    @Test
    void maskPhoneNumber_replacesMiddleOfRussianNumber() {
        String input = "call +79161234567 now";

        String masked = MaskUtil.maskPhoneNumber(input);

        assertThat(masked).contains("916").contains("*****").contains("67");
        assertThat(masked).doesNotContain("1234");
    }

    @Test
    void maskPhoneNumber_blankInput_returnedAsIs() {
        assertThat(MaskUtil.maskPhoneNumber(null)).isNull();
        assertThat(MaskUtil.maskPhoneNumber("")).isEmpty();
    }

    @Test
    void maskEverything_replacesEachCharWithStar() {
        assertThat(MaskUtil.maskEverything("secret")).isEqualTo("******");
        assertThat(MaskUtil.maskEverything(null)).isNull();
    }

    @Test
    void maskAllExpDates_masksDateLikeJsonValueButLeavesAllowlistedTagsAlone() {
        // Distinct values per key — String.replace masks globally, so identical
        // values would mask both occurrences regardless of the allow-list.
        String input = "{\"expiry\":\"2510\",\"atmId\":\"2412\"}";

        String masked = MaskUtil.maskAllExpDates(input);

        assertThat(masked).contains("\"expiry\":\"****\"");
        assertThat(masked).contains("\"atmId\":\"2412\"");
    }

    @Test
    void maskAllPinBlocks_masksHexValueWithMixedAlphasAndDigits() {
        String input = "{\"pin\":\"A1B2C3D4E5F60718\"}";

        String masked = MaskUtil.maskAllPinBlocks(input);

        assertThat(masked).contains("\"pin\":\"****************\"");
    }

    @Test
    void maskLog_appliesAllMaskersTogether() {
        String input = "card 4539578763621486 phone +79161234567 expiry \"exp\":\"2510\"";

        String masked = MaskUtil.maskLog(input);

        assertThat(masked)
                .doesNotContain("4539578763621486")
                .doesNotContain("1234567")
                .doesNotContain("\"exp\":\"2510\"");
    }
}
