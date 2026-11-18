package tf.dpi.es;

public class ElasticsearchLogFetcherApplication {
    public static void main(String[] args) {
        try {
            EsLogFetcher.main(args);
        } catch (Exception ex) {
            System.err.println("Kibana log fetch failed: " + ex.getMessage());
            System.exit(1);
        }
    }
}