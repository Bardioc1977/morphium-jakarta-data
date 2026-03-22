package de.caluga.morphium.data;

/**
 * Holds the metadata extracted at build time for a single {@code @Repository} interface.
 *
 * @param entityClass the entity type {@code T}
 * @param idClass     the primary-key type {@code K}
 * @param idFieldName the Java field name annotated with {@code @Id}
 */
public record RepositoryMetadata(
        Class<?> entityClass,
        Class<?> idClass,
        String idFieldName
) {}
