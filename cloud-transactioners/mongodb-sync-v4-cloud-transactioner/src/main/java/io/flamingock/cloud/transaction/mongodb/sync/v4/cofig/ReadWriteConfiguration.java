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

package io.flamingock.cloud.transaction.mongodb.sync.v4.cofig;


import com.mongodb.ReadConcern;
import com.mongodb.ReadPreference;
import com.mongodb.WriteConcern;

public class ReadWriteConfiguration {
  private final WriteConcern writeConcern;
  private final ReadConcern readConcern;
  private final ReadPreference readPreference;


  public static ReadWriteConfiguration getDefault() {
    return new ReadWriteConfiguration(
        WriteConcern.MAJORITY.withJournal(true), ReadConcern.MAJORITY,ReadPreference.primary()
    );
  }

  public ReadWriteConfiguration(WriteConcern writeConcern, ReadConcern readConcern, ReadPreference readPreference) {
    this.writeConcern = writeConcern;
    this.readConcern = readConcern;
    this.readPreference = readPreference;
  }


  public WriteConcern getWriteConcern() {
    return writeConcern;
  }

  public ReadConcern getReadConcern() {
    return readConcern;
  }

  public ReadPreference getReadPreference() {
    return readPreference;
  }
}

