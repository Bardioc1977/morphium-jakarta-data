package de.caluga.morphium.data;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.query.Query;
import jakarta.data.page.PageRequest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility for cursor-based (keyset) pagination.
 * Extracts cursor values from entities and applies cursor conditions to queries.
 */
public final class CursorHelper {

    private CursorHelper() {}

    public record SortSpec(String javaField, boolean ascending) {}

    /**
     * Parses an orderBySpec string ("field1:ASC,field2:DESC") into SortSpec list.
     */
    public static List<SortSpec> parseSortSpecs(String orderBySpec) {
        List<SortSpec> specs = new ArrayList<>();
        if (orderBySpec == null || orderBySpec.isEmpty()) return specs;
        for (String part : orderBySpec.split(",")) {
            String[] fieldAndDir = part.split(":");
            specs.add(new SortSpec(fieldAndDir[0], !"DESC".equals(fieldAndDir[1])));
        }
        return specs;
    }

    /**
     * Extracts a cursor from an entity based on the sort fields.
     * The cursor contains the values of the sort fields in order.
     */
    @SuppressWarnings("unchecked")
    public static PageRequest.Cursor extractCursor(Object entity, List<String> sortFields,
                                                    Morphium morphium, Class<?> entityClass) {
        Object[] values = new Object[sortFields.size()];
        for (int i = 0; i < sortFields.size(); i++) {
            values[i] = getFieldValue(entity, sortFields.get(i), morphium, entityClass);
        }
        return PageRequest.Cursor.forKey(values);
    }

    /**
     * Extracts cursors for all entities in a content list.
     */
    public static List<PageRequest.Cursor> extractCursors(List<?> content, List<String> sortFields,
                                                           Morphium morphium, Class<?> entityClass) {
        List<PageRequest.Cursor> cursors = new ArrayList<>(content.size());
        for (Object entity : content) {
            cursors.add(extractCursor(entity, sortFields, morphium, entityClass));
        }
        return cursors;
    }

    /**
     * Applies a cursor condition to the query for keyset pagination.
     * <p>
     * For CURSOR_NEXT with sort [amount ASC, id ASC] and cursor [200, "abc"]:
     * <pre>
     * $or: [
     *   { amount: { $gt: 200 } },
     *   { amount: 200, _id: { $gt: "abc" } }
     * ]
     * </pre>
     * For CURSOR_PREVIOUS, comparison operators are inverted and sort direction is flipped.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void applyCursorCondition(Query query, PageRequest.Cursor cursor,
                                             List<SortSpec> sortSpecs,
                                             Morphium morphium, Class entityClass,
                                             boolean isForward) {
        List orQueries = new ArrayList();

        for (int i = 0; i < sortSpecs.size(); i++) {
            Query sub = morphium.createQueryFor(entityClass);

            // All preceding fields must be equal
            for (int j = 0; j < i; j++) {
                String mongoField = resolveMongoField(morphium, entityClass, sortSpecs.get(j).javaField());
                sub.f(mongoField).eq(cursor.get(j));
            }

            // The i-th field uses a comparison operator
            SortSpec spec = sortSpecs.get(i);
            String mongoField = resolveMongoField(morphium, entityClass, spec.javaField());
            Object cursorValue = cursor.get(i);

            // Determine comparison direction:
            // CURSOR_NEXT + ASC → $gt, CURSOR_NEXT + DESC → $lt
            // CURSOR_PREVIOUS + ASC → $lt, CURSOR_PREVIOUS + DESC → $gt
            boolean useGt = isForward == spec.ascending();

            if (useGt) {
                sub.f(mongoField).gt(cursorValue);
            } else {
                sub.f(mongoField).lt(cursorValue);
            }

            orQueries.add(sub);
        }

        query.or(orQueries);
    }

    /**
     * Applies sort to a query, inverting direction for CURSOR_PREVIOUS.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void applySort(Query query, List<SortSpec> sortSpecs,
                                  Morphium morphium, Class entityClass,
                                  boolean isForward) {
        Map<String, Integer> sortMap = new LinkedHashMap<>();
        for (SortSpec spec : sortSpecs) {
            String mongoField = resolveMongoField(morphium, entityClass, spec.javaField());
            boolean ascending = isForward ? spec.ascending() : !spec.ascending();
            sortMap.put(mongoField, ascending ? 1 : -1);
        }
        query.sort(sortMap);
    }

    @SuppressWarnings("unchecked")
    private static Object getFieldValue(Object entity, String javaFieldName,
                                         Morphium morphium, Class<?> entityClass) {
        try {
            Field field = morphium.getARHelper().getField(entityClass, javaFieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(entity);
            }
        } catch (Exception e) {
            // fallback below
        }
        throw new IllegalStateException(
                "Cannot extract cursor value for field '" + javaFieldName + "' on " + entityClass.getName());
    }

    @SuppressWarnings("unchecked")
    static String resolveMongoField(Morphium morphium, Class<?> entityClass, String javaFieldName) {
        try {
            return morphium.getARHelper().getMongoFieldName(entityClass, javaFieldName);
        } catch (Exception e) {
            return javaFieldName;
        }
    }
}
