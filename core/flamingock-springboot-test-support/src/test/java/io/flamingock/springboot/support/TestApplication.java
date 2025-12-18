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
package io.flamingock.springboot.support;

import io.flamingock.core.kit.inmemory.InMemoryTestKit;
import io.flamingock.internal.core.builder.AbstractChangeRunnerBuilder;
import io.flamingock.targetsystem.nontransactional.NonTransactionalTargetSystem;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = "io.flamingock.springboot.support")
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Bean
    public InMemoryTestKit inMemoryTestKit() {
        return InMemoryTestKit.create();
    }

    @Bean
    public AbstractChangeRunnerBuilder<?, ?> flamingockBuilder(InMemoryTestKit testKit) {
        return testKit.createBuilder()
                .addTargetSystem(kafkaTargetSystem());
    }

    @Bean
    public NonTransactionalTargetSystem kafkaTargetSystem() {
        return new NonTransactionalTargetSystem("kafka");
    }
}
