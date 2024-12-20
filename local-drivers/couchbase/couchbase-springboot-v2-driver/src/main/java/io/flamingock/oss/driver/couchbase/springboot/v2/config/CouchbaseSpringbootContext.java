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

package io.flamingock.oss.driver.couchbase.springboot.v2.config;

import io.flamingock.core.engine.local.driver.LocalDriver;
import io.flamingock.oss.driver.couchbase.CouchbaseConfiguration;
import io.flamingock.oss.driver.couchbase.driver.CouchbaseDriver;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.data.couchbase.CouchbaseDataAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.couchbase.CouchbaseClientFactory;
import org.springframework.util.StringUtils;

import com.couchbase.client.java.Collection;

@Configuration
@ConditionalOnExpression("${flamingock.enabled:true}")
@ConditionalOnBean({CouchbaseClientFactory.class})
@EnableConfigurationProperties(CouchbaseSpringbootConfiguration.class)
@AutoConfigureAfter(CouchbaseDataAutoConfiguration.class)
public class CouchbaseSpringbootContext {

  @Bean
  public LocalDriver<CouchbaseConfiguration> connectionDriver(CouchbaseClientFactory couchbaseClientFactory,
                                                              CouchbaseSpringbootConfiguration couchbaseConfiguration) {
    Collection collection = isCustomCollection(couchbaseConfiguration) ? 
        couchbaseClientFactory.withScope(couchbaseConfiguration.getScope()).getCollection(couchbaseConfiguration.getCollection()) : 
        couchbaseClientFactory.getDefaultCollection();  
    return new CouchbaseDriver(couchbaseClientFactory.getCluster(), collection).setDriverConfiguration(couchbaseConfiguration);
  }
  
  private boolean isCustomCollection(CouchbaseSpringbootConfiguration couchbaseConfiguration){
    return StringUtils.hasText(couchbaseConfiguration.getCollection())  &&
        StringUtils.hasText(couchbaseConfiguration.getScope());
  }
}
