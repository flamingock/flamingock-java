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

package io.flamingock.examples.community;

import io.flamingock.springboot.v2.context.EnableFlamingock;
import io.flamingock.examples.community.client.ClientRepository;
import io.flamingock.examples.community.events.PipelineFailedListener;
import io.flamingock.examples.community.events.PipelineStartedListener;
import io.flamingock.examples.community.events.PipelineCompletedListener;

import io.flamingock.examples.community.events.StageCompletedListener;
import io.flamingock.examples.community.events.StageFailedListener;
import io.flamingock.examples.community.events.StageStartedListener;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;


@EnableFlamingock
@SpringBootApplication
@EnableMongoRepositories(basePackageClasses = ClientRepository.class)
public class CommunitySpringbootMongodbSpringdataApp {

    public final static String DATABASE_NAME = "test";
    public final static String CLIENTS_COLLECTION_NAME = "clientCollection";

    public static void main(String[] args) {
        SpringApplication.run(CommunitySpringbootMongodbSpringdataApp.class, args);
    }

    @Bean
    public PipelineStartedListener startFlamingockListener() {
        return new PipelineStartedListener();
    }

    @Bean
    public PipelineCompletedListener successFlamingockListener() {
        return new PipelineCompletedListener();
    }

    @Bean
    public PipelineFailedListener sailedFlamingockListener() {
        return new PipelineFailedListener();
    }

    @Bean
    public StageStartedListener stageStartedListener() {return new StageStartedListener();}

    @Bean
    public StageCompletedListener stageCompletedListener() {return new StageCompletedListener();}

    @Bean
    public StageFailedListener stageFailedListener() {return new StageFailedListener();}

}