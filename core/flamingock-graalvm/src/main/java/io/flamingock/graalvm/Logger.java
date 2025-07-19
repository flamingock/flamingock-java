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
