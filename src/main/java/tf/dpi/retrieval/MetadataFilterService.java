package tf.dpi.retrieval;

import org.springframework.stereotype.Service;

import tf.dpi.model.SearchRequest;

import java.util.Map;

/**
 * Service that builds structured metadata filters (service, trace id, time range,
 * log level) from a retrieval request for use in vector search.
 */
@Service
public class MetadataFilterService {

    public Object buildFilters(SearchRequest request) {
        // Translate request metadata into store-native filter expression
        return Map.of();
    }
}
