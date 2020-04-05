package filesystem.entity.exception;

public class OneFileSystemException extends RuntimeException {
    public OneFileSystemException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }

    public OneFileSystemException(String errorMessage) { super(errorMessage);}
}

