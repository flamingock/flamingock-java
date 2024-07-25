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

package io.flamingock.examples.community.couchbase;

import io.flamingock.springboot.v2.context.EnableFlamingock;
import io.flamingock.examples.community.couchbase.events.PipelineFailedEventListener;
import io.flamingock.examples.community.couchbase.events.PipelineStartedEventListener;
import io.flamingock.examples.community.couchbase.events.PipelineCompletedEventListener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@EnableFlamingock
@SpringBootApplication
public class CommunitySpringbootV2CouchbaseApp {

    public static void main(String[] args) {
        SpringApplication.run(CommunitySpringbootV2CouchbaseApp.class, args);
    }

    @Bean
    public PipelineStartedEventListener pipelineStartedEventListener() {
        return new PipelineStartedEventListener();
    }

    @Bean
    public PipelineCompletedEventListener pipelineCompletedEventListener() {
        return new PipelineCompletedEventListener();
    }

    @Bean
    public PipelineFailedEventListener pipelineFailedEventListener() {
        return new PipelineFailedEventListener();
    }

}