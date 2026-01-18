package br.com.leonardson.database;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Constants;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.logging.Level;

public class DatabaseManager {
    private static final String MAIN_PATH = Constants.UNIVERSE_PATH.resolve("TaleShop").toAbsolutePath().toString();
    private static final String DATABASE_PATH = MAIN_PATH + File.separator + "taleshop.db";

    private static final Set<String> ALLOWED_STAT_COLUMNS = Set.of();
    
    private Connection connection;
    private final HytaleLogger logger;

    public DatabaseManager(HytaleLogger logger) {
        this.logger = logger;
    }

    /**
     * Ensures the main directory exists
     */
    private void ensureMainDirectory() {
        File directory = new File(MAIN_PATH);
        if (!directory.exists()) {
            directory.mkdirs();
            logger.at(Level.INFO).log("Created plugin directory at: " + MAIN_PATH);
        }
    }

    /**
     * Ensures the database file is created
     */
    private void ensureDatabaseFile() {
        File databaseFile = new File(DATABASE_PATH);
        if (!databaseFile.exists()) {
            try {
                databaseFile.createNewFile();
                logger.at(Level.INFO).log("Created database file at: " + DATABASE_PATH);
            } catch (Exception e) {
                logger.at(Level.SEVERE).log("Failed to create database file: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Establishes a connection to the SQLite database
     */
    public void connect() {
        try {
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            logger.at(Level.INFO).log("SQLite JDBC driver loaded successfully");
            
            // Ensure main directory exists
            ensureMainDirectory();
            
            // Ensure database file exists
            ensureDatabaseFile();

            // Establish connection
            String url = "jdbc:sqlite:" + DATABASE_PATH;
            connection = DriverManager.getConnection(url);
            logger.at(Level.INFO).log("Successfully connected to SQLite database at: " + DATABASE_PATH);

            // Initialize tables
            initializeTables();
            
            // Verify connection by running a simple query
            if (connection != null && !connection.isClosed()) {
                logger.at(Level.INFO).log("Database connection verified and ready");
            }
        } catch (ClassNotFoundException e) {
            logger.at(Level.SEVERE).log("SQLite JDBC driver not found: " + e.getMessage());
            e.printStackTrace();
        } catch (SQLException e) {
            logger.at(Level.SEVERE).log("Failed to connect to database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Creates the necessary tables if they don't exist
     */
    private void initializeTables() {
        String createPlayerStatsTable = """
            CREATE TABLE IF NOT EXISTS shops (
                player_uuid TEXT PRIMARY KEY,
                player_name TEXT NOT NULL,
                shop_name TEXT NOT NULL
            )
            """;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createPlayerStatsTable);
            logger.at(Level.INFO).log("Database tables initialized successfully");
        } catch (SQLException e) {
            logger.at(Level.SEVERE).log("Failed to initialize database tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gets the current database connection
     */
    public Connection getConnection() {
        try {
            // Check if connection is still valid
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            logger.at(Level.SEVERE).log("Failed to check connection status: " + e.getMessage());
        }
        return connection;
    }

    /**
     * Closes the database connection
     */
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.at(Level.INFO).log("Database connection closed");
            }
        } catch (SQLException e) {
            logger.at(Level.SEVERE).log("Error while closing database connection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Checks if the database connection is active
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
