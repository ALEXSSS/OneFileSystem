package filesystem.entity.filesystem;

import java.util.Objects;

public class DirectoryReadResult {
    private final String name;
    private final FileType type;
    private final long size;


    public DirectoryReadResult(String name, FileType type, long size) {
        this.name = name;
        this.type = type;
        this.size = size;
    }

    public static DirectoryReadResult of(String name, FileType type, long size) {
        return new DirectoryReadResult(name, type, size);
    }

    public String getName() {
        return name;
    }

    public FileType getType() {
        return type;
    }

    public long getSize() {
        return size;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DirectoryReadResult that = (DirectoryReadResult) o;
        return size == that.size &&
                Objects.equals(name, that.name) &&
                type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, size);
    }

    @Override
    public String toString() {
        return "DirectoryReadResult{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", size=" + size +
                '}';
    }
}
