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
package io.flamingock.cloud.api.request;

public enum CloudAuditTxType {
    NON_TX,
    TX_SHARED,                  // SharedTx (Target system the same as the audit store)
    TX_SEPARATE_WITH_MARKER,    // SimpleTx (Target system is not the audit store). With marker
    TX_SEPARATE_NO_MARKER;      // SimpleTx (Target system is not the audit store). Without marker

}