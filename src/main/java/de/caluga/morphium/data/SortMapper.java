package de.caluga.morphium.data;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.query.Query;
import jakarta.data.Order;
import jakarta.data.Sort;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps Jakarta Data {@link Order} / {@link Sort} to Morphium query sorting.
 */
public final class SortMapper {

    private SortMapper() {}

    /**
     * Applies the given Jakarta Data Order to the Morphium query.
     *
     * @param query       the Morphium query
     * @param order       the Jakarta Data order specification
     * @param morphium    the Morphium instance (for field name resolution)
     * @param entityClass the entity class
     */
    @SuppressWarnings("unchecked")
    public static void apply(Query<?> query, Order<?> order, Morphium morphium, Class<?> entityClass) {
        if (order == null || order.sorts().isEmpty()) return;

        Map<String, Integer> sortMap = new LinkedHashMap<>();
        for (Sort<?> sort : order.sorts()) {
            String mongoField = resolveMongoField(morphium, entityClass, sort.property());
            sortMap.put(mongoField, sort.isAscending() ? 1 : -1);
        }
        query.sort(sortMap);
    }

    @SuppressWarnings("unchecked")
    private static String resolveMongoField(Morphium morphium, Class<?> entityClass, String javaFieldName) {
        try {
            return morphium.getARHelper().getMongoFieldName(entityClass, javaFieldName);
        } catch (Exception e) {
            return javaFieldName;
        }
    }
}
