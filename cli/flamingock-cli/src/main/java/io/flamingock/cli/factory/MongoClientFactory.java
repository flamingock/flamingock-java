/*
 * Copyright 2025 Flamingock (https://www.flamingock.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.flamingock.cli.factory;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import io.flamingock.cli.config.DatabaseConfig;

public class MongoClientFactory {

    public static MongoClient createMongoClient(DatabaseConfig.MongoDBConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("MongoDB configuration is required");
        }

        String connectionString = buildConnectionString(config);
        if (connectionString == null) {
            throw new IllegalArgumentException("MongoDB connection string or host/database must be provided");
        }

        try {
            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(connectionString))
                    .build();
            
            MongoClient client = MongoClients.create(settings);
            
            // Test the connection
            client.listDatabaseNames().first();
            
            return client;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create MongoDB client: " + e.getMessage(), e);
        }
    }

    public static MongoDatabase createMongoDatabase(MongoClient mongoClient, DatabaseConfig.MongoDBConfig config) {
        if (config.getDatabase() == null) {
            throw new IllegalArgumentException("MongoDB database name is required");
        }
        
        try {
            MongoDatabase database = mongoClient.getDatabase(config.getDatabase());
            
            // Test database access
            database.listCollectionNames().first();
            
            return database;
        } catch (Exception e) {
            throw new RuntimeException("Failed to access MongoDB database '" + config.getDatabase() + "': " + e.getMessage(), e);
        }
    }

    private static String buildConnectionString(DatabaseConfig.MongoDBConfig config) {
        if (config.getConnectionString() != null) {
            return config.getConnectionString();
        }

        if (config.getHost() != null && config.getDatabase() != null) {
            StringBuilder sb = new StringBuilder("mongodb://");
            
            if (config.getUsername() != null && config.getPassword() != null) {
                sb.append(config.getUsername()).append(":").append(config.getPassword()).append("@");
            }
            
            sb.append(config.getHost());
            
            if (config.getPort() != null) {
                sb.append(":").append(config.getPort());
            }
            
            sb.append("/").append(config.getDatabase());
            
            return sb.toString();
        }

        return null;
    }
}