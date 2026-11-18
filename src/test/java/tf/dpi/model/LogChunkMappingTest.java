package tf.dpi.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.Transient;
import org.junit.jupiter.api.Test;

class LogChunkMappingTest {

    @Test
    void leavesPgvectorColumnOutOfHibernateEntityBinding() throws Exception {
        assertTrue(LogChunk.class.getDeclaredField("embedding").isAnnotationPresent(Transient.class));
    }
}
