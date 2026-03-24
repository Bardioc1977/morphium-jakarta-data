package de.caluga.morphium.data;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.annotations.Aliases;
import de.caluga.morphium.query.Query;
import de.caluga.morphium.data.QueryDescriptor.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Executes a {@link QueryDescriptor} against a Morphium instance at runtime.
 * Resolves Java field names to MongoDB field names via
 * {@code morphium.getARHelper().getMongoFieldName()}.
 */
public final class QueryExecutor {

    private QueryExecutor() {}

    /**
     * Executes the given query descriptor and returns the result.
     *
     * @param descriptor the parsed query
     * @param args       the method arguments
     * @param repo       the repository instance (provides Morphium + metadata)
     * @return the query result (List, single entity, long, boolean, or Stream)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object execute(QueryDescriptor descriptor,
                                 Object[] args,
                                 AbstractMorphiumRepository<?, ?> repo) {
        Morphium morphium = repo.getMorphium();
        Class entityClass = repo.getMetadata().entityClass();
        Query query = morphium.createQueryFor(entityClass);

        // Apply conditions
        applyConditions(query, descriptor, args, morphium, entityClass);

        // Apply sorting
        if (descriptor.orderBy() != null && !descriptor.orderBy().isEmpty()) {
            applySorting(query, descriptor.orderBy(), morphium, entityClass);
        }

        // Execute based on prefix
        return switch (descriptor.prefix()) {
            case FIND -> switch (descriptor.returnType()) {
                case SINGLE -> QueryResultHelper.requireSingle(query);
                case OPTIONAL -> QueryResultHelper.optionalSingle(query);
                case STREAM -> query.stream();
                default -> query.asList();
            };
            case COUNT -> query.countAll();
            case EXISTS -> query.countAll() > 0;
            case DELETE -> {
                long count = query.countAll();
                // Uses bulk deleteMany — does NOT fire @PreRemove/@PostRemove lifecycle
                // callbacks. This is intentional for performance (avoids loading all entities
                // into memory). Entities requiring lifecycle hooks should use Morphium.delete()
                // directly instead of derived deleteBy* methods.
                query.delete();
                yield count;
            }
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyConditions(Query query,
                                         QueryDescriptor descriptor,
                                         Object[] args,
                                         Morphium morphium,
                                         Class entityClass) {
        boolean isOr = descriptor.combinator() == Combinator.OR;

        if (isOr && descriptor.conditions().size() > 1) {
            // Build OR query using Morphium's or() mechanism
            List<Query> orQueries = new ArrayList<>();
            for (Condition cond : descriptor.conditions()) {
                Query sub = morphium.createQueryFor(entityClass);
                applyCondition(sub, cond, args, morphium, entityClass);
                orQueries.add(sub);
            }
            query.or(orQueries);
        } else {
            for (Condition cond : descriptor.conditions()) {
                applyCondition(query, cond, args, morphium, entityClass);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applyCondition(Query query,
                                        Condition cond,
                                        Object[] args,
                                        Morphium morphium,
                                        Class entityClass) {
        String mongoField = resolveMongoField(morphium, entityClass, cond.field());
        List<String> aliases = resolveAliases(morphium, entityClass, cond.field());

        if (!aliases.isEmpty()) {
            // Field has @Aliases — build $or to match current name + all aliases.
            // The primary field uses query.f() (normal resolution), but alias names
            // must be used as raw MongoDB field names (query.f() would resolve them
            // back to the current name via ARHelper).
            List<Query> orQueries = new ArrayList<>();

            // Sub-query for the current (primary) field name
            Query primarySub = morphium.createQueryFor(entityClass);
            applySingleFieldCondition(primarySub, mongoField, cond, args, morphium, entityClass);
            orQueries.add(primarySub);

            // Sub-queries for each alias — using rawQuery to bypass ARHelper resolution
            for (String alias : aliases) {
                Query aliasSub = morphium.createQueryFor(entityClass);
                aliasSub.rawQuery(buildRawCondition(alias, cond, args));
                orQueries.add(aliasSub);
            }
            query.or(orQueries);
        } else {
            applySingleFieldCondition(query, mongoField, cond, args, morphium, entityClass);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void applySingleFieldCondition(Query query,
                                                   String mongoField,
                                                   Condition cond,
                                                   Object[] args,
                                                   Morphium morphium,
                                                   Class entityClass) {
        var field = query.f(mongoField);

        switch (cond.operator()) {
            case EQ -> field.eq(args[cond.paramIndex()]);
            case NE -> field.ne(args[cond.paramIndex()]);
            case GT -> field.gt(args[cond.paramIndex()]);
            case GTE -> field.gte(args[cond.paramIndex()]);
            case LT -> field.lt(args[cond.paramIndex()]);
            case LTE -> field.lte(args[cond.paramIndex()]);
            case BETWEEN -> {
                field.gte(args[cond.paramIndex()]);
                query.f(mongoField).lte(args[cond.paramIndex2()]);
            }
            case IN -> field.in((Collection) args[cond.paramIndex()]);
            case NIN -> field.nin((Collection) args[cond.paramIndex()]);
            case LIKE -> {
                String pattern = args[cond.paramIndex()].toString();
                // Convert SQL LIKE pattern to regex: % → .*, _ → .
                String regex = pattern
                        .replace("%", ".*")
                        .replace("_", ".");
                field.matches(Pattern.compile(regex));
            }
            case STARTS_WITH -> {
                String val = Pattern.quote(args[cond.paramIndex()].toString());
                field.matches(Pattern.compile("^" + val));
            }
            case ENDS_WITH -> {
                String val = Pattern.quote(args[cond.paramIndex()].toString());
                field.matches(Pattern.compile(val + "$"));
            }
            case CONTAINS -> field.eq(args[cond.paramIndex()]);
            case NOT_CONTAINS -> field.ne(args[cond.paramIndex()]);
            case IS_EMPTY -> field.size(0);
            case IS_NOT_EMPTY -> {
                // {$nor: [{field: {$size: 0}}]} — matches docs where array is not empty
                Query sub = morphium.createQueryFor(entityClass);
                sub.f(mongoField).size(0);
                query.nor(sub);
            }
            case SIZE -> field.size(((Number) args[cond.paramIndex()]).intValue());
            case MATCHES -> field.matches(args[cond.paramIndex()].toString());
            case IGNORE_CASE -> {
                String val = java.util.regex.Pattern.quote(args[cond.paramIndex()].toString());
                field.matches("^" + val + "$", "i");
            }
            case IS_NULL -> field.eq(null);
            case IS_NOT_NULL -> field.ne(null);
            case IS_TRUE -> field.eq(true);
            case IS_FALSE -> field.eq(false);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void applySorting(Query query,
                             List<OrderSpec> orderSpecs,
                             Morphium morphium,
                             Class entityClass) {
        Map<String, Integer> sortMap = new LinkedHashMap<>();
        for (OrderSpec spec : orderSpecs) {
            String mongoField = resolveMongoField(morphium, entityClass, spec.field());
            sortMap.put(mongoField, spec.direction() == Direction.ASC ? 1 : -1);
        }
        query.sort(sortMap);
    }

    @SuppressWarnings("unchecked")
    private static String resolveMongoField(Morphium morphium,
                                             Class entityClass,
                                             String javaFieldName) {
        try {
            return morphium.getARHelper().getMongoFieldName(entityClass, javaFieldName);
        } catch (Exception e) {
            // Fallback: use the Java field name as-is
            return javaFieldName;
        }
    }

    /**
     * Builds a raw MongoDB query condition for an alias field name.
     * This bypasses ARHelper resolution so the alias name is used as-is.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Object> buildRawCondition(String fieldName,
                                                          Condition cond,
                                                          Object[] args) {
        Map<String, Object> result = new LinkedHashMap<>();
        switch (cond.operator()) {
            case EQ -> result.put(fieldName, args[cond.paramIndex()]);
            case NE -> result.put(fieldName, Map.of("$ne", args[cond.paramIndex()]));
            case GT -> result.put(fieldName, Map.of("$gt", args[cond.paramIndex()]));
            case GTE -> result.put(fieldName, Map.of("$gte", args[cond.paramIndex()]));
            case LT -> result.put(fieldName, Map.of("$lt", args[cond.paramIndex()]));
            case LTE -> result.put(fieldName, Map.of("$lte", args[cond.paramIndex()]));
            case BETWEEN -> {
                Map<String, Object> range = new LinkedHashMap<>();
                range.put("$gte", args[cond.paramIndex()]);
                range.put("$lte", args[cond.paramIndex2()]);
                result.put(fieldName, range);
            }
            case IN -> result.put(fieldName, Map.of("$in", args[cond.paramIndex()]));
            case NIN -> result.put(fieldName, Map.of("$nin", args[cond.paramIndex()]));
            case LIKE -> {
                String pattern = args[cond.paramIndex()].toString()
                        .replace("%", ".*").replace("_", ".");
                result.put(fieldName, Map.of("$regex", pattern));
            }
            case STARTS_WITH -> result.put(fieldName, Map.of("$regex", "^" + Pattern.quote(args[cond.paramIndex()].toString())));
            case ENDS_WITH -> result.put(fieldName, Map.of("$regex", Pattern.quote(args[cond.paramIndex()].toString()) + "$"));
            case CONTAINS -> result.put(fieldName, args[cond.paramIndex()]);
            case NOT_CONTAINS -> result.put(fieldName, Map.of("$ne", args[cond.paramIndex()]));
            case MATCHES -> result.put(fieldName, Map.of("$regex", args[cond.paramIndex()].toString()));
            case IGNORE_CASE -> {
                Map<String, Object> regex = new LinkedHashMap<>();
                regex.put("$regex", "^" + Pattern.quote(args[cond.paramIndex()].toString()) + "$");
                regex.put("$options", "i");
                result.put(fieldName, regex);
            }
            case IS_NULL -> result.put(fieldName, null);
            case IS_NOT_NULL -> {
                Map<String, Object> ne = new LinkedHashMap<>();
                ne.put("$ne", null);
                result.put(fieldName, ne);
            }
            case IS_TRUE -> result.put(fieldName, true);
            case IS_FALSE -> result.put(fieldName, false);
            case IS_EMPTY -> result.put(fieldName, Map.of("$size", 0));
            case IS_NOT_EMPTY -> result.put("$nor", List.of(Map.of(fieldName, Map.of("$size", 0))));
            case SIZE -> result.put(fieldName, Map.of("$size", ((Number) args[cond.paramIndex()]).intValue()));
        }
        return result;
    }

    /**
     * Returns the @Aliases values for the given Java field, or an empty list if none.
     * This enables queries to match documents stored under old field names.
     */
    @SuppressWarnings("unchecked")
    private static List<String> resolveAliases(Morphium morphium,
                                                Class entityClass,
                                                String javaFieldName) {
        try {
            Field javaField = morphium.getARHelper().getField(entityClass, javaFieldName);
            if (javaField != null && javaField.isAnnotationPresent(Aliases.class)) {
                return List.of(javaField.getAnnotation(Aliases.class).value());
            }
        } catch (Exception e) {
            // ignore — no aliases
        }
        return List.of();
    }
}
