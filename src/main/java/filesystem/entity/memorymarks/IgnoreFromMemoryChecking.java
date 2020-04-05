package filesystem.entity.memorymarks;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * By default all types which implement ByteRepresentable interface will be checked on memory consistency.
 * To ignore checking, put this annotation on class.
 *
 * @see filesystem.entity.ByteRepresentable
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface IgnoreFromMemoryChecking {
}
