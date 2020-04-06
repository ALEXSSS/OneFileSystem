package filesystem.entity.exception;

public class SegmentAllocatorException extends OneFileSystemException {
    public SegmentAllocatorException(String errorMessage, Throwable err) {
        super(errorMessage, err);
    }

    public SegmentAllocatorException(String errorMessage) {
        super(errorMessage);
    }
}
