package ru.otus.sn.config;

import java.util.ArrayList;
import java.util.List;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.DisposableBean;

/** Пул соединений к слейвам; закрытием ресурсов управляет Spring. */
public class ReplicaDataSources implements DisposableBean {

    private final List<HikariDataSource> dataSources = new ArrayList<>();

    public void add(HikariDataSource ds) {
        dataSources.add(ds);
    }

    public List<HikariDataSource> all() {
        return List.copyOf(dataSources);
    }

    public int size() {
        return dataSources.size();
    }

    @Override
    public void destroy() {
        for (HikariDataSource ds : dataSources) {
            ds.close();
        }
    }
}
