package com.teacheros.modules.feestructure;

import com.teacheros.modules.feestructure.FeeCalculator.DiscountSpec;
import com.teacheros.modules.feestructure.FeeCalculator.HeadInput;
import com.teacheros.modules.feestructure.FeeCalculator.InstalmentResult;
import com.teacheros.modules.feestructure.FeeCalculator.InstalmentSpec;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FeeCalculatorTest {

    private static final UUID TUITION = UUID.randomUUID();
    private static final UUID ADMISSION = UUID.randomUUID();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static List<InstalmentSpec> quarterly() {
        return List.of(
                new InstalmentSpec(1, LocalDate.of(2026, 4, 10), bd("25")),
                new InstalmentSpec(2, LocalDate.of(2026, 7, 10), bd("25")),
                new InstalmentSpec(3, LocalDate.of(2026, 10, 10), bd("25")),
                new InstalmentSpec(4, LocalDate.of(2027, 1, 10), bd("25")));
    }

    @Test
    void annualFeeInFourInstalmentsGivesFourEqualAmounts() {
        var result = FeeCalculator.calculate(List.of(new HeadInput(TUITION, bd("40000"), true)), List.of(), quarterly());

        assertThat(result.instalments()).hasSize(4);
        assertThat(result.instalments()).allSatisfy(i -> assertThat(i.net()).isEqualByComparingTo("10000.00"));
        assertThat(result.instalments().get(0).dueDate()).isEqualTo(LocalDate.of(2026, 4, 10));
    }

    @Test
    void unevenSplitAlwaysSumsToTheExactTotal() {
        var thirds = List.of(
                new InstalmentSpec(1, LocalDate.of(2026, 4, 1), bd("33.33")),
                new InstalmentSpec(2, LocalDate.of(2026, 5, 1), bd("33.33")),
                new InstalmentSpec(3, LocalDate.of(2026, 6, 1), bd("33.34")));
        var result = FeeCalculator.calculate(List.of(new HeadInput(TUITION, bd("10001.01"), true)), List.of(), thirds);

        BigDecimal sum = result.instalments().stream().map(InstalmentResult::net).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("10001.01");
    }

    @Test
    void siblingPercentOnlyAppliesToEligibleHeads() {
        var heads = List.of(new HeadInput(TUITION, bd("40000"), true), new HeadInput(ADMISSION, bd("5000"), false));
        var sibling = new DiscountSpec(null, AmountType.PERCENT, bd("10"), "Sibling concession", true);

        var result = FeeCalculator.calculate(heads, List.of(sibling), quarterly());

        BigDecimal discount = result.instalments().stream().map(InstalmentResult::discount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(discount).isEqualByComparingTo("4000.00"); // 10% of tuition only
        assertThat(result.discountLabels()).containsExactly("Sibling concession");
    }

    @Test
    void fixedDiscountIsCappedAtTheHeadAmount() {
        var manual = new DiscountSpec(TUITION, AmountType.FIXED, bd("99999"), "Scholarship", false);
        var result = FeeCalculator.calculate(List.of(new HeadInput(TUITION, bd("1000"), true)), List.of(manual), quarterly());

        assertThat(result.instalments()).allSatisfy(i -> assertThat(i.net()).isEqualByComparingTo("0.00"));
    }

    @Test
    void fixedAllHeadDiscountIsSharedProRataAndAddsUp() {
        var heads = List.of(new HeadInput(TUITION, bd("30000"), true), new HeadInput(ADMISSION, bd("10000"), true));
        var manual = new DiscountSpec(null, AmountType.FIXED, bd("4000"), "Staff child", false);

        var result = FeeCalculator.calculate(heads, List.of(manual), quarterly());

        BigDecimal discount = result.instalments().stream().map(InstalmentResult::discount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(discount).isEqualByComparingTo("4000.00");
        // 3:1 split -> tuition takes 3000 of it
        BigDecimal tuitionDiscount = result.instalments().stream()
                .flatMap(i -> i.lines().stream()).filter(l -> l.headId().equals(TUITION))
                .map(FeeCalculator.LineResult::discount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(tuitionDiscount).isEqualByComparingTo("3000.00");
    }

    @Test
    void allocateGivesTheRemainderToTheLastShare() {
        var parts = FeeCalculator.allocate(bd("100.00"), List.of(bd("1"), bd("1"), bd("1")));
        assertThat(parts).containsExactly(bd("33.33"), bd("33.33"), bd("33.34"));
    }
}
