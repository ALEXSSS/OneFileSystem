package filesystem.entity.filesystem;

import filesystem.entity.ByteRepresentable;
import filesystem.entity.memorymarks.IgnoreFromMemoryChecking;

import static filesystem.utils.ByteArrayConverterUtils.stringToByteArray;

/**
 * Represents abstract file in OneFileSystem.
 */
@IgnoreFromMemoryChecking
public class BaseFileInf implements ByteRepresentable {
    protected final String name;

    public BaseFileInf(String name) {
        this.name = name;
    }

    @Override
    public byte[] toByteArray() {
        return stringToByteArray(name);
    }


    public static BaseFileInf of(String name) {
        return new BaseFileInf(name);
    }
}
