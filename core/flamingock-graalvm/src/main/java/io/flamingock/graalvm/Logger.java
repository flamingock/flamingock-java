/*
 * Copyright 2025 Flamingock (https://oss.flamingock.io)
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
 
package io.flamingock.graalvm;

public class Logger {
    private static final String PREFIX = "[Flamingock]";


    public void startProcess(String message) {
        System.out.printf("%s Starting %s\n", PREFIX, message);
    }

    public void finishedProcess(String message) {
        System.out.printf("%s Completed %s\n", PREFIX, message);
    }

    public void startRegistrationProcess(String registrationName) {
        startProcess("registration of " + registrationName);
    }

    public void completedRegistrationProcess(String registrationName) {
        finishedProcess(registrationName);
    }

    public void startClassRegistration(Class<?> clazz) {
        System.out.printf("\tRegistering class: %s \n", clazz.getName());
    }





    public void startInitializationProcess(String registrationName) {
        startProcess("initialization at build time of " + registrationName);
    }

    public void completeInitializationProcess(String registrationName) {
        finishedProcess("initialization at build time of " + registrationName);
    }

    public void startClassInitialization(Class<?> clazz) {
        System.out.printf("\tInitializing class at build time: %s \n", clazz.getName());
    }

}
