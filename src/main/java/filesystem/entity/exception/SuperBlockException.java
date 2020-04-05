package filesystem.entity.exception;

public class SuperBlockException extends OneFileSystemException {
    public SuperBlockException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }

    public SuperBlockException(String errorMessage) {
        super(errorMessage);
    }
}
