package tf.dpi.retrieval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tf.dpi.model.SearchRequest;
import tf.dpi.model.SearchResult;

import java.util.List;

/**
 * Service that coordinates retrieval: applies metadata filters, runs vector search,
 * and returns ranked results to the caller.
 */
@Service
public class LogRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(LogRetrievalService.class);

    private final VectorSearchService vectorSearchService;
    private final MetadataFilterService metadataFilterService;

    @Autowired
    public LogRetrievalService(VectorSearchService vectorSearchService,
                               MetadataFilterService metadataFilterService) {
        this.vectorSearchService = vectorSearchService;
        this.metadataFilterService = metadataFilterService;
    }

    public List<SearchResult> retrieve(SearchRequest request) {
        log.info("Retrieving logs for request: {}", request);
        var filters = metadataFilterService.buildFilters(request);
        return vectorSearchService.search(request.getQueryVector(), filters, request.getTopK());
    }
}
