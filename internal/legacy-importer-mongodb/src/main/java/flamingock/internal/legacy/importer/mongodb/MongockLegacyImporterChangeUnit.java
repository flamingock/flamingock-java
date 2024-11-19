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

package flamingock.internal.legacy.importer.mongodb;


import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.changock.migration.api.annotations.NonLockGuarded;
import io.flamingock.commons.utils.JsonObjectMapper;
import io.flamingock.commons.utils.RunnerId;
import io.flamingock.commons.utils.http.Http;
import io.flamingock.core.api.annotations.ChangeUnit;
import io.flamingock.core.api.annotations.Execution;
import io.flamingock.core.api.annotations.SystemChange;
import org.apache.http.impl.client.HttpClients;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

@SystemChange
@ChangeUnit(id = "mongock-legacy-importer-mongodb-v1", order = "1")
public class MongockLegacyImporterChangeUnit {

    private static final Logger logger = LoggerFactory.getLogger(MongockLegacyImporterChangeUnit.class);

    @Execution
    public void execution(@NonLockGuarded MongoDBLegacyImportConfiguration configuration, @NonLockGuarded MongoDatabase mongoDatabase) {

        //Get MongoDB ChangeLog
        MongoCollection<Document> collection = mongoDatabase.getCollection(configuration.getChangeUnitsCollection());

        ArrayList<Document> data = collection
                .find()
                .into(new ArrayList<>());

        //Instance HttpClient
        Http.RequestBuilderFactory requestBuilderFactory = Http.builderFactory(HttpClients.createDefault(), JsonObjectMapper.DEFAULT_INSTANCE);
        Http.RequestBuilder requestBuilder = requestBuilderFactory.getRequestBuilder(configuration.getServerHost());

        String pathTemplate = String.format(
                "/api/v1/environment/%s/service/%s/execution/import",
                configuration.getEnvironmentId().toString(),
                configuration.getServiceId().toString()
        );

        try {
            RunnerId runnerId = RunnerId.generate();

            requestBuilder
                    .POST(pathTemplate)
                    .withRunnerId(runnerId)
                    .withBearerToken(configuration.getJwt())
                    .setBody(data)
                    .execute();

        } catch (Throwable throwable) {
            logger.error("Error writing legacy audit:\n{}", throwable.toString());
            throw throwable;
        }
    }
}
