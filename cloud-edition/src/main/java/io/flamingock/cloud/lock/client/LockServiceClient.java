/*
 * Copyright 2023 Flamingock ("https://oss.flamingock.io")
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.flamingock.cloud.lock.client;

import io.flamingock.core.cloud.api.lock.LockExtensionRequest;
import io.flamingock.core.cloud.api.lock.LockResponse;
import io.flamingock.core.engine.lock.LockKey;
import io.flamingock.commons.utils.RunnerId;

public interface LockServiceClient {

    LockResponse extendLock(LockKey lockKey,
                            RunnerId runnerId,
                            LockExtensionRequest lockRequest);

    LockResponse getLock(LockKey lockKey);

    void releaseLock(LockKey lockKey, RunnerId runnerId);
}
