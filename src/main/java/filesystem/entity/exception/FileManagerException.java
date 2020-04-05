package filesystem.entity.exception;

public class FileManagerException extends OneFileSystemException {
    public FileManagerException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }

    public FileManagerException(String errorMessage) {
        super(errorMessage);
    }
}
