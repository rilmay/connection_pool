package com.epam.connectionpool;

import java.sql.Connection;

public interface ConnectionPool{
    Connection getConnection();
}

