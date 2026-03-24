package de.caluga.morphium.data;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import de.caluga.morphium.annotations.*;
import de.caluga.morphium.data.QueryDescriptor.*;
import de.caluga.morphium.driver.inmem.InMemoryDriver;
import de.caluga.morphium.query.Query;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that derived queries correctly use $or to match both the current
 * MongoDB field name and any @Aliases, so old documents stored under a
 * previous field name are found.
 */
class QueryExecutorAliasTest {

    private static Morphium morphium;

    @Entity
    static class OtaUpdate {
        @Id
        private String id;

        private String campaignNumber;

        private String vin;

        @Aliases({"updateId"})
        private String otaUpdateId;

        @Aliases({"uploadDate"})
        private String otaUploadDate;

        private String status;
    }

    @BeforeAll
    static void setUp() {
        MorphiumConfig cfg = new MorphiumConfig();
        cfg.setDatabase("test");
        cfg.addHostToSeed("localhost");
        cfg.setDriverName(InMemoryDriver.driverName);
        morphium = new Morphium(cfg);
    }

    @AfterAll
    static void tearDown() {
        if (morphium != null) {
            morphium.close();
        }
    }

    @Test
    @DisplayName("Query on aliased field generates $or with current name + aliases")
    void queryOnAliasedFieldGeneratesOr() {
        // findByOtaUpdateId → should query {$or: [{ota_update_id: val}, {updateId: val}]}
        QueryDescriptor descriptor = new QueryDescriptor(
                Prefix.FIND,
                List.of(new Condition("otaUpdateId", Operator.EQ, 0)),
                Combinator.AND,
                List.of(),
                ReturnType.LIST
        );

        Query<?> query = buildQuery(descriptor, new Object[]{"update-123"});
        Map<String, Object> queryObj = query.toQueryObject();

        // Must contain $or with both field names
        assertThat(queryObj).containsKey("$or");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orList = (List<Map<String, Object>>) queryObj.get("$or");
        assertThat(orList).hasSize(2);

        Set<String> queriedFields = new HashSet<>();
        for (Map<String, Object> sub : orList) {
            queriedFields.addAll(sub.keySet());
        }
        assertThat(queriedFields).containsExactlyInAnyOrder("ota_update_id", "updateId");
    }

    @Test
    @DisplayName("Query on non-aliased field generates simple condition (no $or)")
    void queryOnNonAliasedFieldIsSimple() {
        // findByCampaignNumber → should NOT generate $or
        QueryDescriptor descriptor = new QueryDescriptor(
                Prefix.FIND,
                List.of(new Condition("campaignNumber", Operator.EQ, 0)),
                Combinator.AND,
                List.of(),
                ReturnType.LIST
        );

        Query<?> query = buildQuery(descriptor, new Object[]{"CN-001"});
        Map<String, Object> queryObj = query.toQueryObject();

        assertThat(queryObj).doesNotContainKey("$or");
        assertThat(queryObj).containsKey("campaign_number");
    }

    @Test
    @DisplayName("Combined AND query with aliased + non-aliased fields")
    void combinedAndQueryWithAliasedField() {
        // findByCampaignNumberAndOtaUpdateId
        QueryDescriptor descriptor = new QueryDescriptor(
                Prefix.FIND,
                List.of(
                        new Condition("campaignNumber", Operator.EQ, 0),
                        new Condition("otaUpdateId", Operator.EQ, 1)
                ),
                Combinator.AND,
                List.of(),
                ReturnType.LIST
        );

        Query<?> query = buildQuery(descriptor, new Object[]{"CN-001", "update-123"});
        Map<String, Object> queryObj = query.toQueryObject();

        // Should have $and containing the simple condition + the $or for the alias
        assertThat(queryObj).containsKey("$and");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> andList = (List<Map<String, Object>>) queryObj.get("$and");

        // One entry for campaignNumber, one $or entry for otaUpdateId/updateId
        assertThat(andList).hasSizeGreaterThanOrEqualTo(2);

        String flatJson = queryObj.toString();
        assertThat(flatJson).contains("campaign_number");
        assertThat(flatJson).contains("ota_update_id");
        assertThat(flatJson).contains("updateId");
    }

    @Test
    @DisplayName("Multiple aliased fields each get their own $or")
    void multipleAliasedFields() {
        // findByOtaUpdateIdAndOtaUploadDate
        QueryDescriptor descriptor = new QueryDescriptor(
                Prefix.FIND,
                List.of(
                        new Condition("otaUpdateId", Operator.EQ, 0),
                        new Condition("otaUploadDate", Operator.EQ, 1)
                ),
                Combinator.AND,
                List.of(),
                ReturnType.LIST
        );

        Query<?> query = buildQuery(descriptor, new Object[]{"update-123", "2025-01-01"});
        Map<String, Object> queryObj = query.toQueryObject();
        String flatJson = queryObj.toString();

        // Both alias names must appear
        assertThat(flatJson).contains("ota_update_id");
        assertThat(flatJson).contains("updateId");
        assertThat(flatJson).contains("ota_upload_date");
        assertThat(flatJson).contains("uploadDate");
    }

    @Test
    @DisplayName("IN operator on aliased field generates $or")
    void inOperatorOnAliasedField() {
        QueryDescriptor descriptor = new QueryDescriptor(
                Prefix.FIND,
                List.of(new Condition("otaUpdateId", Operator.IN, 0)),
                Combinator.AND,
                List.of(),
                ReturnType.LIST
        );

        Query<?> query = buildQuery(descriptor, new Object[]{List.of("u1", "u2")});
        Map<String, Object> queryObj = query.toQueryObject();

        assertThat(queryObj).containsKey("$or");
        String flatJson = queryObj.toString();
        assertThat(flatJson).contains("ota_update_id");
        assertThat(flatJson).contains("updateId");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Query<?> buildQuery(QueryDescriptor descriptor, Object[] args) {
        Class entityClass = OtaUpdate.class;
        Query query = morphium.createQueryFor(entityClass);

        // Build query by calling QueryExecutor.applyCondition via reflection
        boolean isOr = descriptor.combinator() == Combinator.OR;
        if (isOr && descriptor.conditions().size() > 1) {
            List<Query> orQueries = new ArrayList<>();
            for (Condition cond : descriptor.conditions()) {
                Query sub = morphium.createQueryFor(entityClass);
                callApplyCondition(sub, cond, args, morphium, entityClass);
                orQueries.add(sub);
            }
            query.or(orQueries);
        } else {
            for (Condition cond : descriptor.conditions()) {
                callApplyCondition(query, cond, args, morphium, entityClass);
            }
        }
        return query;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void callApplyCondition(Query query, Condition cond, Object[] args,
                                     Morphium morphium, Class entityClass) {
        // Call the private applyCondition method via reflection to test internal logic
        try {
            var method = QueryExecutor.class.getDeclaredMethod(
                    "applyCondition", Query.class, Condition.class,
                    Object[].class, Morphium.class, Class.class);
            method.setAccessible(true);
            method.invoke(null, query, cond, args, morphium, entityClass);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke applyCondition", e);
        }
    }
}
