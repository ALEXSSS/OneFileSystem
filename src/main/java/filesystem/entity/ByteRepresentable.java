package filesystem.entity;


/**
 * Interface to transfer object to bytes.
 * It also forces all classes implementing it to have {@code public static int getSizeOfStructure()} method,
 * with correct resultant value, otherwise, corresponding unit test will fail. To ignore memory checking put annotation
 *
 * @see filesystem.entity.memorymarks.IgnoreFromMemoryChecking
 */
public interface ByteRepresentable {
    /**
     * @return Returns bytes array as byte representation of given instance.
     */
    byte[] toByteArray();
}
