package de.caluga.morphium.data;

import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;

import java.util.Iterator;
import java.util.List;

/**
 * Morphium-backed implementation of Jakarta Data's {@link Page}.
 *
 * @param <T> the entity type
 */
public class MorphiumPage<T> implements Page<T> {

    private final List<T> content;
    private final long totalElements;
    private final PageRequest pageRequest;

    public MorphiumPage(List<T> content, long totalElements, PageRequest pageRequest) {
        this.content = content;
        this.totalElements = totalElements;
        this.pageRequest = pageRequest;
    }

    @Override
    public List<T> content() {
        return content;
    }

    @Override
    public boolean hasTotals() {
        return totalElements >= 0;
    }

    @Override
    public long totalElements() {
        if (!hasTotals()) {
            throw new IllegalStateException("Total not requested. Use PageRequest.withTotal().");
        }
        return totalElements;
    }

    @Override
    public long totalPages() {
        if (!hasTotals()) {
            throw new IllegalStateException("Total not requested. Use PageRequest.withTotal().");
        }
        if (pageRequest.size() <= 0) return 1;
        return (totalElements + pageRequest.size() - 1) / pageRequest.size();
    }

    @Override
    public PageRequest pageRequest() {
        return pageRequest;
    }

    @Override
    public PageRequest nextPageRequest() {
        if (!hasNext()) {
            return null;
        }
        return PageRequest.ofPage(pageRequest.page() + 1, pageRequest.size(), pageRequest.requestTotal());
    }

    @Override
    public PageRequest previousPageRequest() {
        if (pageRequest.page() <= 1) {
            return null;
        }
        return PageRequest.ofPage(pageRequest.page() - 1, pageRequest.size(), pageRequest.requestTotal());
    }

    @Override
    public boolean hasContent() {
        return !content.isEmpty();
    }

    @Override
    public int numberOfElements() {
        return content.size();
    }

    @Override
    public boolean hasNext() {
        if (!hasTotals()) {
            // If no totals, check if we got a full page (heuristic)
            return content.size() >= pageRequest.size();
        }
        return pageRequest.page() < totalPages();
    }

    @Override
    public boolean hasPrevious() {
        return pageRequest.page() > 1;
    }

    @Override
    public Iterator<T> iterator() {
        return content.iterator();
    }
}
