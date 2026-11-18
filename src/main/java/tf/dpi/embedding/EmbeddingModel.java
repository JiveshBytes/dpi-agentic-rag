package tf.dpi.embedding;

import java.util.List;

/**
 * Simple embedding model interface (stand-in for Spring AI EmbeddingModel).
 * In production, this would be replaced by Spring AI's EmbeddingModel.
 */
public interface EmbeddingModel {

    float[] embed(String text);

    default List<float[]> embedAll(List<String> texts) {
        return texts.stream().map(this::embed).toList();
    }
}
