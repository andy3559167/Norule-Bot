package com.norule.musicbot.shorturl;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class MySqlMediaSecurityRepository extends JdbcMediaSecurityRepository {
    private final HikariDataSource dataSource;

    public MySqlMediaSecurityRepository(String jdbcUrl, String username, String password, int poolSize) {
        this(createDataSource(jdbcUrl, username, password, poolSize));
    }

    private MySqlMediaSecurityRepository(HikariDataSource dataSource) {
        super(dataSource::getConnection, true);
        this.dataSource = dataSource;
    }

    @Override
    public void close() {
        dataSource.close();
    }

    private static HikariDataSource createDataSource(String jdbcUrl, String username,
                                                     String password, int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(Math.max(2, poolSize));
        config.setMinimumIdle(1);
        config.setPoolName("media-security-pool");
        config.setConnectionTimeout(10_000L);
        config.setValidationTimeout(5_000L);
        return new HikariDataSource(config);
    }
}
