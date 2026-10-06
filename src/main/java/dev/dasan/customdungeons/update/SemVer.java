package dev.dasan.customdungeons.update;

import java.math.BigInteger;
import java.util.List;
import java.util.regex.Pattern;

/** SemVer 2.0 precedence; build metadata never makes an update newer. */
public final class SemVer implements Comparable<SemVer> {
    private static final Pattern FORMAT = Pattern.compile(
            "(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?");
    private final String text;
    private final List<BigInteger> numbers;
    private final List<String> prerelease;
    private SemVer(String text, List<BigInteger> numbers, List<String> prerelease) {
        this.text = text; this.numbers = numbers; this.prerelease = prerelease;
    }
    public static SemVer parse(String value) {
        if (value == null || value.length() > 256) throw new IllegalArgumentException("Invalid SemVer");
        var match = FORMAT.matcher(value);
        if (!match.matches()) throw new IllegalArgumentException("Invalid SemVer");
        var pre = match.group(4) == null ? List.<String>of() : List.of(match.group(4).split("\\."));
        for (String identifier : pre)
            if (numeric(identifier) && identifier.length() > 1 && identifier.startsWith("0"))
                throw new IllegalArgumentException("Leading zero in prerelease");
        return new SemVer(value, List.of(new BigInteger(match.group(1)), new BigInteger(match.group(2)), new BigInteger(match.group(3))), pre);
    }
    private static boolean numeric(String value) { return value.chars().allMatch(c -> c >= '0' && c <= '9'); }
    @Override public int compareTo(SemVer other) {
        for (int i = 0; i < 3; i++) {
            int order = numbers.get(i).compareTo(other.numbers.get(i));
            if (order != 0) return order;
        }
        if (prerelease.isEmpty() || other.prerelease.isEmpty())
            return prerelease.isEmpty() ? (other.prerelease.isEmpty() ? 0 : 1) : -1;
        for (int i = 0; i < Math.min(prerelease.size(), other.prerelease.size()); i++) {
            String left = prerelease.get(i), right = other.prerelease.get(i);
            boolean ln = numeric(left), rn = numeric(right);
            int order = ln && rn ? new BigInteger(left).compareTo(new BigInteger(right))
                    : ln != rn ? (ln ? -1 : 1) : left.compareTo(right);
            if (order != 0) return order;
        }
        return Integer.compare(prerelease.size(), other.prerelease.size());
    }
    @Override public String toString() { return text; }
}
