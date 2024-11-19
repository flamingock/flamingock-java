/*
 * Copyright 2023 Flamingock (https://oss.flamingock.io)
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

package io.flamingock.examples.mysql.standalone;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import flamingock.internal.legacy.importer.mongodb.MongoDBLegacyImporter;
import io.flamingock.core.configurator.standalone.FlamingockStandalone;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class LegacyImporterMongoDBApplication {

    private static final String SERVICE_NAME = "clients-service";
    private static final String ENVIRONMENT = "development";
    private static final String API_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImZsYW1pbmdvY2staW50ZXJuYWwifQ.eyJpc3MiOiJodHRwczovL2ZsYW1pbmdvY2suZXUuYXV0aDAuY29tIiwiYXVkIjoiaHR0cHM6Ly9hcGkuZmxhbWluZ29jay5pbyIsImlhdCI6MTcyNzc5MzM5NiwiZXhwIjoxNzU5MzI5Mzk2LCJ0b2tlbl90eXBlIjoiYXBpX3Rva2VuIiwib3JnYW5pemF0aW9uIjoiNmI0YzNiM2MtMDAyNy00OWY4LWIxNjQtZGNmNmYwZDkxNzAwIiwicHJvamVjdCI6IjM2MzhjMzk0LTE4NTMtNGM3OC05ZmJiLWI4ZGYxY2QwMmNlZiIsImVudmlyb25tZW50IjoiNjcxMjg3YzItNTUyZC00Yjk0LTk0ZjMtMjc0ODkxM2I2MTM0Iiwic2VydmljZSI6IjA3YTE2MmI0LTU0OGQtNDI2ZS1hNmFmLTg5Mjk0NjNiM2UzYSJ9.IEkYfV5TBQ80KGw6AjjV_8ac-tjTOnHVDt_zZAawW_ON9ejEtz8sdrC4fSUeqZCAbJm-ZsABeIaxUxp2S7oYG-TJEEMBenDz-eExN_Xq5lq4S7-UdYO7NGEJCw9Qi5sfDjHp6IkGimOJoU15jyHwg9APMzyeOjG4N-2Zy975xgqDzvOir3wbvM-sT6Uk_-bwyHxW4eLPCFvH0Hz9P3sZZfoKIttx3Mw83Z3DeIyFMXzbWQOlj5wECkvD0A2Bg9gzUJQeRHZnfCU9A5efbr3Ps3vNRm8kdMqNoZnMW6G-WGf9uy-65D04_sNN8Ttc_yArpOeKbyDxuPJ2yAiZYw";
    private final static String MONGODB_CHANGELOG_COLLECTION = "mongockChangeLog";
    public final static String DATABASE_NAME = "test";
    public static void main(String[] args) throws ClassNotFoundException {
        new LegacyImporterMongoDBApplication()
                .run(getMongoClient("mongodb://localhost:27017/"), DATABASE_NAME);

    }

    public void run(MongoClient mongoClient, String databaseName) throws ClassNotFoundException {

        FlamingockStandalone
                    .cloud()
                    .setHost("http://localhost:8080")
                    .setApiToken(API_TOKEN)
                    .setEnvironment(ENVIRONMENT)
                    .setService(SERVICE_NAME)
                    .addSystemModule(new MongoDBLegacyImporter(MONGODB_CHANGELOG_COLLECTION))
                    .setLockAcquiredForMillis(6 * 1000L)//this is just to show how is set. Default value is still 60 * 1000L
                    .setLockQuitTryingAfterMillis(10 * 1000L)//this is just to show how is set. Default value is still 3 * 60 * 1000L
                    .setLockTryFrequencyMillis(3000L)//this is just to show how is set. Default value is still 1000L
                    .addDependency(mongoClient.getDatabase(databaseName))
                    .build()
                    .run();

    }
    private static MongoClient getMongoClient(String connectionString) {

        CodecRegistry codecRegistry = fromRegistries(CodecRegistries.fromCodecs(new ZonedDateTimeCodec()),
                MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));

        MongoClientSettings.Builder builder = MongoClientSettings.builder();
        builder.applyConnectionString(new ConnectionString(connectionString));
        builder.codecRegistry(codecRegistry);
        MongoClientSettings build = builder.build();
        return MongoClients.create(build);
    }

}