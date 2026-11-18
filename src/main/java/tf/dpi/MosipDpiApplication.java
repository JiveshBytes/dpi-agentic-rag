package tf.dpi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the MOSIP DPI Agentic RAG application. 
 * Orchestrates log ingestion, masking, chunking, embedding, storage, retention,
 * and retrieval pipelines for MOSIP log observability.
 */
@SpringBootApplication
@EnableScheduling
public class MosipDpiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MosipDpiApplication.class, args);
    }
}
