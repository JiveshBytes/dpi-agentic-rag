package tf.dpi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.ReadOnlyProperty;

/**
 * Configuration properties for PostgreSQL datasource access used by the RAG store.
 */
@Configuration
@ConfigurationProperties(prefix = "spring.datasource")
public class PostgreSQLConfig {

    private String url = "jdbc:postgresql://localhost:5432/mosip_dpi";
    private String username = "mosip";
    private String password = "mosip";
    private String driverClassName = "org.postgresql.Driver";
    private Integer maximumPoolSize = 10;
    private Integer minimumIdle = 2;
    private Integer connectionTimeout = 30000;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public Integer getMaximumPoolSize() {
        return maximumPoolSize;
    }

    public void setMaximumPoolSize(Integer maximumPoolSize) {
        this.maximumPoolSize = maximumPoolSize;
    }

    public Integer getMinimumIdle() {
        return minimumIdle;
    }

    public void setMinimumIdle(Integer minimumIdle) {
        this.minimumIdle = minimumIdle;
    }

    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(Integer connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }
}
