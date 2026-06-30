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
package com.github.cloudyrock.mongock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test-only minimal replica of the legacy Mongock {@code @ChangeSet} annotation.
 * <p>Replaces the {@code :legacy:mongock-support} test dependency so that
 * {@link io.flamingock.springboot.SpringbootProfileFilter} FQCN-based detection
 * of {@code com.github.cloudyrock.mongock.ChangeSet} can be verified without
 * pulling the full legacy module into the test classpath.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ChangeSet {

    String author();

    String id();

    String order();
}
