package de.caluga.morphium.data;

import de.caluga.morphium.Morphium;
import de.caluga.morphium.query.Query;
import jakarta.data.Order;
import jakarta.data.Sort;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.page.impl.CursoredPageRecord;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/**
 * Framework-agnostic base class for repository implementations.
 * Contains all CRUD logic as regular Java methods; framework-specific
 * subclasses or proxies provide the bridge from the repository interface
 * methods to the {@code doXxx()} methods here.
 * <p>
 * The Morphium instance is injected via {@link #setMorphium(Morphium)}
 * by the framework integration (Quarkus CDI, Spring DI, etc.).
 *
 * @param <T> the entity type
 * @param <K> the primary-key type
 */
public abstract class AbstractMorphiumRepository<T, K> {

    private Morphium morphium;

    private final RepositoryMetadata metadata;

    protected AbstractMorphiumRepository(RepositoryMetadata metadata) {
        this.metadata = metadata;
    }

    // -- accessors for subclasses and QueryExecutor --------------------------

    public Morphium getMorphium() {
        return morphium;
    }

    protected void setMorphium(Morphium morphium) {
        this.morphium = morphium;
    }

    public RepositoryMetadata getMetadata() {
        return metadata;
    }

    @SuppressWarnings("unchecked")
    public Class<T> entityClass() {
        return (Class<T>) metadata.entityClass();
    }

    // -- BasicRepository CRUD operations -------------------------------------

    @SuppressWarnings("unchecked")
    public Optional<T> doFindById(K id) {
        T result = (T) morphium.findById(entityClass(), id, null);
        return Optional.ofNullable(result);
    }

    public Stream<T> doFindAll() {
        return morphium.createQueryFor(entityClass()).stream();
    }

    @SuppressWarnings("unchecked")
    public Page<T> doFindAllPaged(PageRequest pageRequest, Order<T> sortBy) {
        Query<T> query = morphium.createQueryFor(entityClass());

        // Apply sorting from Order
        if (sortBy != null && !sortBy.sorts().isEmpty()) {
            Map<String, Integer> sortMap = new LinkedHashMap<>();
            for (Sort<? super T> sort : sortBy.sorts()) {
                String mongoField = resolveMongoField(sort.property());
                sortMap.put(mongoField, sort.isAscending() ? 1 : -1);
            }
            query.sort(sortMap);
        }

        // Apply pagination
        int size = pageRequest.size();
        long page = pageRequest.page();
        int skip = (int) ((page - 1) * size);
        query.skip(skip).limit(size);

        List<T> content = query.asList();

        // Total count (only if requested)
        long totalElements = -1;
        if (pageRequest.requestTotal()) {
            totalElements = morphium.createQueryFor(entityClass()).countAll();
        }

        return new MorphiumPage<>(content, totalElements, pageRequest);
    }

    @SuppressWarnings("unchecked")
    public CursoredPage<T> doFindAllCursored(PageRequest pageRequest, Order<T> sortBy) {
        Query<T> query = morphium.createQueryFor(entityClass());

        // Build sort specs from Order
        List<CursorHelper.SortSpec> sortSpecs = new ArrayList<>();
        if (sortBy != null && !sortBy.sorts().isEmpty()) {
            for (Sort<? super T> sort : sortBy.sorts()) {
                sortSpecs.add(new CursorHelper.SortSpec(sort.property(), sort.isAscending()));
            }
        }

        boolean isForward = pageRequest.mode() != PageRequest.Mode.CURSOR_PREVIOUS;

        if (pageRequest.mode() != PageRequest.Mode.OFFSET) {
            PageRequest.Cursor cursor = pageRequest.cursor()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "PageRequest mode is " + pageRequest.mode() + " but no cursor provided"));
            CursorHelper.applyCursorCondition(query, cursor, sortSpecs, morphium, entityClass(), isForward);
        }

        CursorHelper.applySort(query, sortSpecs, morphium, entityClass(), isForward);
        int requestedSize = pageRequest.size();
        query.limit(requestedSize + 1);

        List<T> content = query.asList();
        boolean hasMore = content.size() > requestedSize;
        if (hasMore) {
            content = new ArrayList<>(content.subList(0, requestedSize));
        }
        if (!isForward) {
            Collections.reverse(content);
        }

        List<String> sortFields = sortSpecs.stream().map(CursorHelper.SortSpec::javaField).toList();
        List<PageRequest.Cursor> cursors = CursorHelper.extractCursors(content, sortFields, morphium, entityClass());

        long totalElements = -1;
        if (pageRequest.requestTotal()) {
            totalElements = morphium.createQueryFor(entityClass()).countAll();
        }

        boolean isFirstPage = pageRequest.mode() == PageRequest.Mode.OFFSET;
        boolean isLastPage = !hasMore;

        if (content.isEmpty()) {
            return new CursoredPageRecord<>(content, cursors, totalElements, pageRequest,
                    (PageRequest) null, (PageRequest) null);
        }

        return new CursoredPageRecord<>(content, cursors, totalElements, pageRequest,
                isFirstPage, isLastPage);
    }

    public Object doSave(Object entity) {
        morphium.store(entity);
        return entity;
    }

    @SuppressWarnings("unchecked")
    public List<Object> doSaveAll(List<?> entities) {
        morphium.storeList((List) entities);
        return (List<Object>) entities;
    }

    public Object doInsert(Object entity) {
        morphium.insert(entity);
        return entity;
    }

    @SuppressWarnings("unchecked")
    public List<Object> doInsertAll(List<?> entities) {
        morphium.insertList((List) entities);
        return (List<Object>) entities;
    }

    public Object doUpdate(Object entity) {
        morphium.store(entity);
        return entity;
    }

    @SuppressWarnings("unchecked")
    public List<Object> doUpdateAll(List<?> entities) {
        morphium.storeList((List) entities);
        return (List<Object>) entities;
    }

    public void doDelete(Object entity) {
        morphium.delete(entity);
    }

    @SuppressWarnings("unchecked")
    public void doDeleteById(K id) {
        T entity = (T) morphium.findById(entityClass(), id, null);
        if (entity != null) {
            morphium.delete(entity);
        }
    }

    @SuppressWarnings("unchecked")
    public void doDeleteAll(List<?> entities) {
        for (Object entity : entities) {
            morphium.delete(entity);
        }
    }

    public void doDeleteAllNoArg() {
        morphium.clearCollection(entityClass());
    }

    public Query<T> createQuery() {
        return morphium.createQueryFor(entityClass());
    }

    // -- MorphiumRepository operations ----------------------------------------

    @SuppressWarnings("unchecked")
    public List<Object> doDistinct(String fieldName) {
        String mongoField = resolveMongoField(fieldName);
        return (List<Object>) (List<?>) morphium.createQueryFor(entityClass()).distinct(mongoField);
    }

    public Morphium doMorphium() {
        return morphium;
    }

    public Query<T> doQuery() {
        return morphium.createQueryFor(entityClass());
    }

    @SuppressWarnings("unchecked")
    private String resolveMongoField(String javaFieldName) {
        try {
            return morphium.getARHelper().getMongoFieldName(entityClass(), javaFieldName);
        } catch (Exception e) {
            return javaFieldName;
        }
    }

    // -- Async support --------------------------------------------------------

    public Executor getAsyncExecutor() {
        return morphium.getAsyncOperationsThreadPool();
    }

    public CompletionStage<Optional<T>> doFindByIdAsync(K id) {
        return CompletableFuture.supplyAsync(() -> doFindById(id), getAsyncExecutor());
    }

    public CompletionStage<Stream<T>> doFindAllAsync() {
        return CompletableFuture.supplyAsync(this::doFindAll, getAsyncExecutor());
    }

    public CompletionStage<Object> doSaveAsync(Object entity) {
        return CompletableFuture.supplyAsync(() -> doSave(entity), getAsyncExecutor());
    }

    public CompletionStage<List<Object>> doSaveAllAsync(List<?> entities) {
        return CompletableFuture.supplyAsync(() -> doSaveAll(entities), getAsyncExecutor());
    }

    public CompletionStage<Object> doInsertAsync(Object entity) {
        return CompletableFuture.supplyAsync(() -> doInsert(entity), getAsyncExecutor());
    }

    public CompletionStage<List<Object>> doInsertAllAsync(List<?> entities) {
        return CompletableFuture.supplyAsync(() -> doInsertAll(entities), getAsyncExecutor());
    }

    public CompletionStage<Object> doUpdateAsync(Object entity) {
        return CompletableFuture.supplyAsync(() -> doUpdate(entity), getAsyncExecutor());
    }

    public CompletionStage<List<Object>> doUpdateAllAsync(List<?> entities) {
        return CompletableFuture.supplyAsync(() -> doUpdateAll(entities), getAsyncExecutor());
    }

    public CompletionStage<Void> doDeleteAsync(Object entity) {
        return CompletableFuture.runAsync(() -> doDelete(entity), getAsyncExecutor());
    }

    public CompletionStage<Void> doDeleteByIdAsync(K id) {
        return CompletableFuture.runAsync(() -> doDeleteById(id), getAsyncExecutor());
    }
}
