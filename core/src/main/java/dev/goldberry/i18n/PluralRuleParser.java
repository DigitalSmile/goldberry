package dev.goldberry.i18n;

import java.util.ArrayList;
import java.util.List;

/// Parses the condition of a CLDR plural rule: everything before its first
/// `@integer` or `@decimal`.
///
/// ```
/// condition  = and ("or" and)*
/// and        = relation ("and" relation)*
/// relation   = operand ("%" value)? ("=" | "!=") range ("," range)*
/// range      = value (".." value)?
/// ```
///
/// That is the whole of what CLDR 49 writes; `is`, `in` and `within`, which
/// older rules used, are not in the data.
final class PluralRuleParser {

    private final String[] tokens;
    private int at;

    private PluralRuleParser(String text) {
        this.tokens = text.replace("..", " .. ")
                .replace(",", " , ")
                .replace("!=", " != ")
                .replace("%", " % ")
                .replaceAll("(?<![!])=", " = ")
                .trim()
                .split("\\s+");
    }

    /// The condition of `rule`, samples ignored.
    ///
    /// @throws IllegalArgumentException if it is not one
    static PluralCondition parse(String rule) {
        var sample = rule.indexOf('@');
        var condition = (sample < 0 ? rule : rule.substring(0, sample)).strip();
        if (condition.isEmpty()) {
            return new PluralCondition.Always();
        }
        var parser = new PluralRuleParser(condition);
        var parsed = parser.or();
        if (parser.at != parser.tokens.length) {
            throw new IllegalArgumentException("trailing \"" + parser.tokens[parser.at] + "\" in rule: " + rule);
        }
        return parsed;
    }

    private PluralCondition or() {
        var parts = new ArrayList<PluralCondition>();
        parts.add(and());
        while (accept("or")) {
            parts.add(and());
        }
        return parts.size() == 1 ? parts.getFirst() : new PluralCondition.Or(List.copyOf(parts));
    }

    private PluralCondition and() {
        var parts = new ArrayList<PluralCondition>();
        parts.add(relation());
        while (accept("and")) {
            parts.add(relation());
        }
        return parts.size() == 1 ? parts.getFirst() : new PluralCondition.And(List.copyOf(parts));
    }

    private PluralCondition relation() {
        var operand = next();
        if (operand.length() != 1 || "nivwftec".indexOf(operand.charAt(0)) < 0) {
            throw new IllegalArgumentException("not a plural operand: " + operand);
        }
        var modulus = accept("%") ? Long.parseLong(next()) : 0;
        boolean equal;
        if (accept("=")) {
            equal = true;
        } else if (accept("!=")) {
            equal = false;
        } else {
            throw new IllegalArgumentException("expected = or != after " + operand);
        }
        var ranges = new ArrayList<PluralCondition.Range>();
        do {
            var low = Long.parseLong(next());
            var high = accept("..") ? Long.parseLong(next()) : low;
            ranges.add(new PluralCondition.Range(low, high));
        } while (accept(","));
        return new PluralCondition.Relation(operand.charAt(0), modulus, equal, List.copyOf(ranges));
    }

    private boolean accept(String token) {
        if (at < tokens.length && tokens[at].equals(token)) {
            at++;
            return true;
        }
        return false;
    }

    private String next() {
        if (at >= tokens.length) {
            throw new IllegalArgumentException("a plural rule ended too soon");
        }
        return tokens[at++];
    }
}
