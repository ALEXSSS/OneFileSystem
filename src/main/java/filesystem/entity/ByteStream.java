package filesystem.entity;

/**
 * Custom ByteStream, after each operation internal pointer have to move ahead.
 */
public interface ByteStream {
    boolean hasNext();

    byte getByte();

    int getInt();

    /**
     * Copies returned num of bytes to provided byte array.
     * Method changes internal pointer of stream.
     *
     * @param arr to copy bytes in
     * @return how many bytes were copied
     */
    int getArr(byte[] arr);


    String getString();
}
