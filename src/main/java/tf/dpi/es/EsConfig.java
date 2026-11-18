package tf.dpi.es;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
public class EsConfig {

    @Bean(name = "legacyElasticsearchClient")
    public RestHighLevelClient elasticsearchClient() {
        String url = System.getProperty("es.url", "http://localhost:9200");
        return new RestHighLevelClient(
                RestClient.builder(new HttpHost(url.split("//")[1], 9200, "http"))
        );
    }

    @Bean
    public ZoneId tz() {
        return ZoneId.systemDefault();
    }
}