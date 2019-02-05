package com.epam.connectionpool.impl;

import com.epam.connectionpool.ConnectionPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Semaphore;


public class BasicConnectionPool implements ConnectionPool {
    private final static Logger LOGGER = LogManager.getLogger(BasicConnectionPool.class);
    private final String driverClass;
    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final int poolCapacity;
    private final Semaphore semaphore;
    private final Deque<Connection> connectionDeque = new ConcurrentLinkedDeque<>();

    public BasicConnectionPool(String driverClass, String jdbcUrl, String user, String
            password, int poolCapacity) {
        this.driverClass = driverClass;
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.poolCapacity = poolCapacity;
        initDriver(this.driverClass);
        semaphore = new Semaphore(this.poolCapacity);
    }


    @Override
    public Connection getConnection() {
        try {
            semaphore.acquire();
            if (connectionDeque.size() == 0) {
                return createConnection();
            }
            return connectionDeque.pop();
        } catch (InterruptedException | SQLException e) {
            LOGGER.info(e);
            throw new RuntimeException(e);
        }
    }

    private void initDriver(String driverClass) {
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            LOGGER.error(e);
            throw new IllegalStateException("Driver cannot be found", e);
        }
    }


    private Connection createConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl, user, password);
        InvocationHandler connectionHandler = (Object proxy, Method method, Object[] args) -> {
            if (method.getName().equals("close")) {
                releaseConnection((Connection) proxy);
            }
            return method.invoke(connection, args);
        };

        return (Connection) Proxy.newProxyInstance(connection.getClass().getClassLoader(),
                connection.getClass().getInterfaces(), connectionHandler);
    }

    public void releaseConnection(Connection connection) {
        connectionDeque.push(connection);
        semaphore.release();
    }
}

