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

package io.flamingock.community.couchbase.internal.util;

import static io.flamingock.community.couchbase.internal.CouchbaseConstants.DOCUMENT_TYPE_KEY;

public final class N1QLQueryProvider {

  private final static String SELECT_ALL_CHANGES_DEFAULT = "SELECT `%s`.* FROM `%s` WHERE %s = $p";
  private final static String SELECT_ALL_CHANGES_CUSTOM = "SELECT %s.* FROM `%s`.%s.%s WHERE %s = $p";
  private final static String DELETE_ALL_CHANGES_DEFAULT = "DELETE FROM `%s`";
  private final static String DELETE_ALL_CHANGES_CUSTOM = "DELETE FROM `%s`.%s.%s";
  
  private  N1QLQueryProvider(){
    // nothing to do
  }
  
  public static String selectAllChangesQuery(String bucket, String scope, String collection){
    if(CollectionIdentifierUtil.isDefaultCollection(scope, collection)){
      return String.format(SELECT_ALL_CHANGES_DEFAULT, bucket, bucket, DOCUMENT_TYPE_KEY);
    }
    return String.format(SELECT_ALL_CHANGES_CUSTOM, collection, bucket, scope, collection, DOCUMENT_TYPE_KEY);
  }

  public static String deleteAllChangesQuery(String bucket, String scope, String collection){
    if(CollectionIdentifierUtil.isDefaultCollection(scope, collection)){
      return String.format(DELETE_ALL_CHANGES_DEFAULT, bucket);
    }
    return String.format(DELETE_ALL_CHANGES_CUSTOM, bucket, scope, collection);
  }
}
