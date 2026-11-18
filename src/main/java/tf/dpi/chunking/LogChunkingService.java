package tf.dpi.chunking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tf.dpi.model.LogChunk;
import tf.dpi.model.LogEntry;
import tf.dpi.storage.LogChunkRepository;

import java.util.List;

/**
 * Service that orchestrates log chunking: segmentation, trace correlation,
 * time-window grouping, stack-trace handling, and persistence.
 */
@Service
public class LogChunkingService {

    private static final Logger log = LoggerFactory.getLogger(LogChunkingService.class);

    private final MetadataBasedSegmenter segmenter;
    private final TraceBasedCorrelator correlator;
    private final TimeWindowGrouper grouper;
    private final StackTraceProcessor stackTraceProcessor;
    private final LogChunkRepository chunkRepository;

    @Autowired
    public LogChunkingService(MetadataBasedSegmenter segmenter,
                              TraceBasedCorrelator correlator,
                              TimeWindowGrouper grouper,
                              StackTraceProcessor stackTraceProcessor,
                              LogChunkRepository chunkRepository) {
        this.segmenter = segmenter;
        this.correlator = correlator;
        this.grouper = grouper;
        this.stackTraceProcessor = stackTraceProcessor;
        this.chunkRepository = chunkRepository;
    }

    @Transactional
    public List<LogChunk> chunkAndStore(List<LogEntry> entries) {
        return storeChunks(chunk(entries));
    }

    public List<LogChunk> chunk(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            log.info("No log entries to chunk");
            return List.of();
        }
        log.info("Chunking {} log entries", entries.size());

        var segments = segmenter.segment(entries);
        log.debug("Produced {} metadata-based segments", segments.size());

        var correlated = correlator.correlate(segments);
        log.debug("Correlated into {} trace groups", correlated.size());

        var windows = grouper.group(correlated);
        log.debug("Grouped into {} time windows", windows.size());

        var chunks = stackTraceProcessor.process(windows);
        log.info("Produced {} final log chunks", chunks.size());
        return chunks;
    }

    @Transactional
    public List<LogChunk> storeChunks(List<LogChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<LogChunk> saved = chunkRepository.saveAllAndFlush(chunks);
        log.info("Saved {} log chunks to the database", saved.size());
        return saved;
    }
}
