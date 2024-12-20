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

package io.flamingock.oss.driver.mongodb.springdata.v3.config;

import io.flamingock.core.engine.local.driver.LocalDriver;
import io.flamingock.oss.driver.mongodb.springdata.v3.driver.SpringDataMongoV3Driver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
@ConditionalOnExpression("${flamingock.enabled:true}")
@EnableConfigurationProperties(SpringDataMongoV3Configuration.class)
public class SpringDataMongoV3Context {

  @Bean
  public LocalDriver<SpringDataMongoV3Configuration> connectionDriver(MongoTemplate mongoTemplate, SpringDataMongoV3Configuration driverConfiguration) {
      return new SpringDataMongoV3Driver(mongoTemplate).setDriverConfiguration(driverConfiguration);
  }
}
