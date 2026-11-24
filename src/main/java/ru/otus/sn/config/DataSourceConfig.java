package ru.otus.sn.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    public static final String MASTER_KEY = "master";
    public static final String REPLICA_KEY_PREFIX = "replica-";

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("app.datasource.master")
    public HikariDataSource masterDataSource(DataSourceProperties properties) {
        // URL/логин/пароль берутся из spring.datasource.* (в тестах их подставляет @ServiceConnection),
        // значения из app.datasource.master имеют приоритет.
        HikariDataSource ds = DataSourceBuilder.create().type(HikariDataSource.class).build();
        if (ds.getJdbcUrl() == null) {
            ds.setJdbcUrl(properties.determineUrl());
            ds.setUsername(properties.determineUsername());
            ds.setPassword(properties.determinePassword());
        }
        return ds;
    }

    @Bean
    public ReplicaDataSources replicaDataSources(
            @Value("${app.datasource.replica-urls:}") List<String> replicaUrls,
            @Value("${spring.datasource.username:sn_user}") String username,
            @Value("${spring.datasource.password:sn_password}") String password) {
        ReplicaDataSources replicas = new ReplicaDataSources();
        for (String url : replicaUrls) {
            if (url == null || url.isBlank()) {
                continue;
            }
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(url.trim());
            ds.setUsername(username);
            ds.setPassword(password);
            ds.setMaximumPoolSize(10);
            ds.setPoolName("replica-" + replicas.size());
            replicas.add(ds);
        }
        return replicas;
    }

    @Bean
    public ReplicationRoutingDataSource routingDataSource(
            HikariDataSource masterDataSource, ReplicaDataSources replicaDataSources) {
        Map<Object, Object> targets = new HashMap<>();
        targets.put(MASTER_KEY, masterDataSource);
        List<Object> replicaKeys = new ArrayList<>();
        for (int i = 0; i < replicaDataSources.size(); i++) {
            String key = REPLICA_KEY_PREFIX + i;
            targets.put(key, replicaDataSources.all().get(i));
            replicaKeys.add(key);
        }
        ReplicationRoutingDataSource routing = new ReplicationRoutingDataSource(masterDataSource, MASTER_KEY, replicaKeys);
        routing.setTargetDataSources(targets);
        return routing;
    }

    @Bean
    @Primary
    public DataSource dataSource(ReplicationRoutingDataSource routingDataSource) {
        // Откладывает получение физического соединения до первого SQL-запроса:
        // к этому моменту readOnly-флаг транзакции уже установлен и роутинг корректен.
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }
}
