package net.coderazzi.aws_codeartifact_maven.utils;

public class OperationException extends Exception {
    public OperationException(String message) {
        super(message);
    }
    public OperationException(String message, Object ...args) {
        super(String.format(message, args));
    }

    public static OperationException cancelled() throws OperationException {throw new OperationException(null);}
}
