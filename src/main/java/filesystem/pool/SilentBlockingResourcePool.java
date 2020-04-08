package filesystem.pool;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * Resource pool with blocking put and take methods.
 * Removes necessity for dealing with checked exceptions.
 * <p>
 * ++ will be added some asserts soon
 *
 * @param <T> type of resource
 */
public class SilentBlockingResourcePool<T> {
    private final BlockingQueue<T> queue;

    public SilentBlockingResourcePool(int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    public T take() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException("Thread is interrupted!", e);
        }
    }

    public void put(T elem) {
        try {
            queue.put(elem);
        } catch (InterruptedException e) {
            throw new RuntimeException("Thread is interrupted!", e);
        }
    }
}
