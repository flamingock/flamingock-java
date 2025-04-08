package io.flamingock.graalvm;

public class Logger {
    private static final String PREFIX = "[Flamingock]";


    public void startProcess(String message) {
        System.out.printf("%s Starting %s\n", PREFIX, message);
    }

    public void finishedProcess(String message) {
        System.out.printf("%s Completed %s\n", PREFIX, message);
    }

    public void startRegistration(String registrationName) {
        startProcess("registration of " + registrationName);
    }

    public void completedRegistration(String registrationName) {
        finishedProcess(registrationName);
    }

    public void initClassRegistration(Class<?> clazz) {
        System.out.printf("\tRegistering class: %s \n", clazz.getName());
    }

    public void finishedClassRegistration(Class<?> clazz) {
        System.out.printf("\tCompleted registration class: %s \n", clazz.getName());
    }
}
