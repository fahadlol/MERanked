package com.meranked.database;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface SqlConsumer {
    void accept(Connection conn) throws SQLException;
}
