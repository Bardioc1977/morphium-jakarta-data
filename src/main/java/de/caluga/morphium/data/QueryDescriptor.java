package de.caluga.morphium.data;

import java.util.List;

/**
 * Describes a parsed query derived from a repository method name.
 * Built at deploy time by {@code MethodNameParser}, executed at runtime by {@code QueryExecutor}.
 */
public record QueryDescriptor(
        Prefix prefix,
        List<Condition> conditions,
        Combinator combinator,
        List<OrderSpec> orderBy,
        ReturnType returnType
) {

    public enum Prefix { FIND, COUNT, EXISTS, DELETE }

    public enum Combinator { AND, OR }

    public enum ReturnType { SINGLE, OPTIONAL, LIST, STREAM, COUNT, BOOLEAN }

    public record Condition(
            String field,
            Operator operator,
            int paramIndex,
            int paramIndex2   // only used by BETWEEN (second param)
    ) {
        public Condition(String field, Operator operator, int paramIndex) {
            this(field, operator, paramIndex, -1);
        }
    }

    public enum Operator {
        EQ, NE, GT, GTE, LT, LTE, BETWEEN,
        IN, NIN,
        LIKE, STARTS_WITH, ENDS_WITH, CONTAINS, NOT_CONTAINS,
        IS_NULL, IS_NOT_NULL, IS_TRUE, IS_FALSE,
        IS_EMPTY, IS_NOT_EMPTY, SIZE, MATCHES, IGNORE_CASE
    }

    public record OrderSpec(String field, Direction direction) {}

    public enum Direction { ASC, DESC }
}
