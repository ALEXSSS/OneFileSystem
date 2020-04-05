package filesystem.entity.filesystem;

import filesystem.entity.ByteRepresentable;
import filesystem.entity.memorymarks.IgnoreFromMemoryChecking;

import static filesystem.utils.ByteArrayConverterUtils.stringToByteArray;

/**
 * Represents abstract file in one file system.
 */
@IgnoreFromMemoryChecking
public class BaseFileInf implements ByteRepresentable {
    protected String name;

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
