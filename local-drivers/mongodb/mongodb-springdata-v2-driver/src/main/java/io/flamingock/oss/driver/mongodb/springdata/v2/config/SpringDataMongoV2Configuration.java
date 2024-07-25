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

package io.flamingock.oss.driver.mongodb.springdata.v2.config;

import com.mongodb.ReadConcernLevel;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;

import io.flamingock.oss.driver.common.mongodb.MongoDBDriverConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@ConfigurationProperties("flamingock.mongo-db")
public class SpringDataMongoV2Configuration extends MongoDBDriverConfiguration {

    public static SpringDataMongoV2Configuration getDefault() {
        return new SpringDataMongoV2Configuration();
    }

    private WriteConcernLevel writeConcern = WriteConcernLevel.MAJORITY_WITH_JOURNAL;

    private ReadConcernLevel readConcern = ReadConcernLevel.MAJORITY;

    private ReadPreferenceLevel readPreference = ReadPreferenceLevel.PRIMARY;

    public WriteConcernLevel getWriteConcern() {
        return writeConcern;
    }

    public void setWriteConcern(WriteConcernLevel writeConcern) {
        this.writeConcern = writeConcern;
    }

    public ReadConcernLevel getReadConcern() {
        return readConcern;
    }

    public void setReadConcern(ReadConcernLevel readConcern) {
        this.readConcern = readConcern;
    }

    public ReadPreferenceLevel getReadPreference() {
        return readPreference;
    }

    public void setReadPreference(ReadPreferenceLevel readPreference) {
        this.readPreference = readPreference;
    }

    public WriteConcern getBuiltMongoDBWriteConcern() {
        WriteConcern wc = new WriteConcern(writeConcern.w).withJournal(writeConcern.journal);
        return writeConcern.getwTimeoutMs() == null
                ? wc
                : wc.withWTimeout(writeConcern.getwTimeoutMs().longValue(), TimeUnit.MILLISECONDS);
    }

    public static class WriteConcernLevel {

        private String w;
        private Integer wTimeoutMs;
        private Boolean journal;

        public static final WriteConcernLevel MAJORITY_WITH_JOURNAL = new WriteConcernLevel(
                WriteConcern.MAJORITY.getWString(),
                null,
                true);

        public WriteConcernLevel(String w, Integer wTimeoutMs, Boolean journal) {
            this.w = w;
            this.wTimeoutMs = wTimeoutMs;
            this.journal = journal;
        }

        public String getW() {
            return w;
        }

        public void setW(String w) {
            this.w = w;
        }

        public Integer getwTimeoutMs() {
            return wTimeoutMs;
        }

        public void setwTimeoutMs(Integer wTimeoutMs) {
            this.wTimeoutMs = wTimeoutMs;
        }

        public Boolean isJournal() {
            return journal;
        }

        public void setJournal(Boolean journal) {
            this.journal = journal;
        }
    }

    public enum ReadPreferenceLevel {
        PRIMARY(ReadPreference.primary()),
        PRIMARY_PREFERRED(ReadPreference.primaryPreferred()),
        SECONDARY(ReadPreference.secondary()),
        SECONDARY_PREFERRED(ReadPreference.secondaryPreferred()),
        NEAREST(ReadPreference.nearest());

        private ReadPreference value;

        ReadPreferenceLevel(ReadPreference value) {
            this.value = value;
        }

        public ReadPreference getValue() {
            return value;
        }
    }
}
