package io.flamingock.support.change;

public abstract class ChangeValidatorResult {

    public static Ok OK_INSTANCE;

    public static ChangeValidatorResult.Ok OK() {
        if (OK_INSTANCE == null) {
            OK_INSTANCE = new ChangeValidatorResult.Ok();
        }
        return OK_INSTANCE;
    }

    public static ChangeValidatorResult.Error error(String message) {
        return new ChangeValidatorResult.Error(message);
    }

    public static class Ok  extends ChangeValidatorResult {

    }

    public static class Error extends ChangeValidatorResult {
        private final String message;

        public Error(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public boolean isError() {
        return this instanceof ChangeValidatorResult.Error;
    }
}
