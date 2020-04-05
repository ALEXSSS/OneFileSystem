package filesystem;

import filesystem.entity.ByteRepresentable;
import filesystem.entity.memorymarks.IgnoreFromMemoryChecking;
import filesystem.entity.memorymarks.MemorySize;
import org.junit.Test;
import org.reflections.Reflections;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

/**
 * Controls changing of writeable to memory classes
 */
public class SizeOfStructuresTest {

    @Test
    public void sizeOfStructuresTest() {
        Reflections reflections = new Reflections("filesystem");

        List<Class<? extends ByteRepresentable>> classesToCheck = reflections.getSubTypesOf(ByteRepresentable.class)
                .stream()
                .filter(clazz -> !clazz.isAnnotationPresent(IgnoreFromMemoryChecking.class))
                .collect(toList());

        for (Class<? extends ByteRepresentable> clazz : classesToCheck) {
            assertEquals("Size of the structure should be the size of fields inside",
                    calculateSizeOfStructureByFields(clazz),
                    invokeGetSizeOfStructureMethod(clazz));
        }
        System.out.println();
    }

    private int calculateSizeOfStructureByFields(Class<? extends ByteRepresentable> clazz) {
        return Arrays.stream(clazz.getDeclaredFields()).mapToInt(it -> getSizeOfPrimitives(it.getType().getName())).sum();
    }

    private int invokeGetSizeOfStructureMethod(Class<? extends ByteRepresentable> clazz) {
        try {
            return (int) clazz.getMethod("getSizeOfStructure").invoke(clazz);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Class must have getSizeOfStructure method!", e);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("test fail!", e);
        }
    }

    private int getSizeOfPrimitives(String type) {
        try {
            switch (type) {
                case "int":
                    return 4;
                case "long":
                    return 8;
                case "byte":
                case "boolean":
                    return 1;
                default:
                    return Class.forName(type).getAnnotation(MemorySize.class).sizeInBytes();
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("check that you put MemorySize annotation!", e);
        }
    }
}
