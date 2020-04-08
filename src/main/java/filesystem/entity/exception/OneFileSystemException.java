package filesystem.entity.exception;


/**
 * Base exception of oneFileSystem
 */
public abstract class OneFileSystemException extends RuntimeException {
    public OneFileSystemException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }

    public OneFileSystemException(String errorMessage) {
        super(errorMessage);
    }
}

