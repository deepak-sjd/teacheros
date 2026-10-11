package com.teacheros.modules.feestructure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure money maths for fee generation - no Spring, no database, so it is
 * unit tested in milliseconds. Everything is BigDecimal at 2 decimals
 * (HALF_UP); there is no double anywhere in here.
 *
 * Rounding rule: whenever an amount is split (across instalments or across
 * heads) the last share takes the remainder, so the parts always add up to
 * the whole to the paisa.
 */
public final class FeeCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO_2 = new BigDecimal("0.00");

    private FeeCalculator() {
    }

    public record HeadInput(UUID headId, BigDecimal amount, boolean concessionEligible) {
    }

    /**
     * headId null = applies across heads. eligibleHeadsOnly restricts a
     * head-less discount to heads flagged concession-eligible (used for the
     * sibling concession; a manual discount applies to every head).
     */
    public record DiscountSpec(UUID headId, AmountType type, BigDecimal value, String label,
            boolean eligibleHeadsOnly) {
    }

    public record InstalmentSpec(int no, LocalDate dueDate, BigDecimal percentage) {
    }

    public record LineResult(UUID headId, BigDecimal gross, BigDecimal discount) {
    }

    public record InstalmentResult(int no, LocalDate dueDate, BigDecimal gross, BigDecimal discount,
            List<LineResult> lines) {
        public BigDecimal net() {
            return gross.subtract(discount);
        }
    }

    public record Result(List<InstalmentResult> instalments, List<String> discountLabels) {
    }

    public static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Splits total pro rata to weights; the last share takes the rounding remainder. */
    public static List<BigDecimal> allocate(BigDecimal total, List<BigDecimal> weights) {
        List<BigDecimal> out = new ArrayList<>(weights.size());
        if (weights.isEmpty()) {
            return out;
        }
        BigDecimal t = scale(total);
        BigDecimal weightSum = weights.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (weightSum.signum() <= 0) {
            for (int i = 0; i < weights.size() - 1; i++) {
                out.add(ZERO_2);
            }
            out.add(t);
            return out;
        }
        BigDecimal allocated = ZERO_2;
        for (int i = 0; i < weights.size() - 1; i++) {
            BigDecimal share = scale(t.multiply(weights.get(i)).divide(weightSum, 2, RoundingMode.HALF_UP));
            out.add(share);
            allocated = allocated.add(share);
        }
        out.add(t.subtract(allocated));
        return out;
    }

    public static Result calculate(List<HeadInput> heads, List<DiscountSpec> specs, List<InstalmentSpec> instalments) {
        int h = heads.size();
        List<BigDecimal> gross = new ArrayList<>(h);
        List<BigDecimal> disc = new ArrayList<>(h);
        for (HeadInput head : heads) {
            gross.add(scale(head.amount()));
            disc.add(ZERO_2);
        }

        List<String> labels = new ArrayList<>();
        for (DiscountSpec spec : specs) {
            if (spec.type() == null || spec.type() == AmountType.NONE || spec.value() == null
                    || spec.value().signum() <= 0) {
                continue;
            }
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < h; i++) {
                HeadInput head = heads.get(i);
                boolean match = spec.headId() != null
                        ? spec.headId().equals(head.headId())
                        : (!spec.eligibleHeadsOnly() || head.concessionEligible());
                if (match) {
                    idx.add(i);
                }
            }
            if (idx.isEmpty()) {
                continue;
            }

            BigDecimal applied = ZERO_2;
            if (spec.type() == AmountType.PERCENT) {
                for (int i : idx) {
                    BigDecimal d = scale(gross.get(i).multiply(spec.value()).divide(HUNDRED, 2, RoundingMode.HALF_UP));
                    disc.set(i, disc.get(i).add(d));
                    applied = applied.add(d);
                }
            } else {
                List<BigDecimal> weights = new ArrayList<>();
                BigDecimal base = ZERO_2;
                for (int i : idx) {
                    weights.add(gross.get(i));
                    base = base.add(gross.get(i));
                }
                BigDecimal total = base.min(scale(spec.value()));
                List<BigDecimal> parts = allocate(total, weights);
                for (int k = 0; k < idx.size(); k++) {
                    int i = idx.get(k);
                    disc.set(i, disc.get(i).add(parts.get(k)));
                }
                applied = total;
            }
            if (applied.signum() > 0) {
                labels.add(spec.label());
            }
        }

        // A head can never be discounted below zero.
        List<List<BigDecimal>> grossSplit = new ArrayList<>(h);
        List<List<BigDecimal>> discSplit = new ArrayList<>(h);
        List<BigDecimal> weights = instalments.stream().map(InstalmentSpec::percentage).toList();
        for (int i = 0; i < h; i++) {
            BigDecimal capped = disc.get(i).min(gross.get(i));
            grossSplit.add(allocate(gross.get(i), weights));
            discSplit.add(allocate(capped, weights));
        }

        List<InstalmentResult> out = new ArrayList<>(instalments.size());
        for (int j = 0; j < instalments.size(); j++) {
            List<LineResult> lines = new ArrayList<>(h);
            BigDecimal sumGross = ZERO_2;
            BigDecimal sumDisc = ZERO_2;
            for (int i = 0; i < h; i++) {
                BigDecimal g = grossSplit.get(i).get(j);
                BigDecimal d = discSplit.get(i).get(j).min(g).max(ZERO_2);
                lines.add(new LineResult(heads.get(i).headId(), g, d));
                sumGross = sumGross.add(g);
                sumDisc = sumDisc.add(d);
            }
            InstalmentSpec spec = instalments.get(j);
            out.add(new InstalmentResult(spec.no(), spec.dueDate(), sumGross, sumDisc, lines));
        }
        return new Result(out, labels);
    }
}
