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

package io.flamingock.core.configurator.local;

import io.flamingock.core.local.driver.LocalDriver;

import java.util.function.Supplier;

public class LocalConfiguratorDelegate<HOLDER> implements LocalConfigurator<HOLDER> {

    private final LocalConfigurable LocalConfiguration;

    private final Supplier<HOLDER> holderSupplier;

    private LocalDriver<?> connectionDriver;

    private LocalSystemModuleManager systemModules = new LocalSystemModuleManager();


    public LocalConfiguratorDelegate(LocalConfigurable communityConfiguration, Supplier<HOLDER> holderSupplier) {
        this.LocalConfiguration = communityConfiguration;
        this.holderSupplier = holderSupplier;

    }

    @Override
    public HOLDER setDriver(LocalDriver<?> connectionDriver) {
        this.connectionDriver = connectionDriver;
        return holderSupplier.get();
    }

    @Override
    public LocalDriver<?> getDriver() {
        return connectionDriver;
    }

    @Override
    public LocalConfigurable getLocalConfiguration() {
        return LocalConfiguration;
    }

    @Override
    public HOLDER disableTransaction() {
        getLocalConfiguration().setTransactionDisabled(true);
        return holderSupplier.get();
    }

    @Override
    public boolean isTransactionDisabled() {
        return getLocalConfiguration().isTransactionDisabled();
    }

}
