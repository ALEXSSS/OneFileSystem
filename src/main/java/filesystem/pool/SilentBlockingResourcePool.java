package filesystem.pool;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * Resource pool with blocking put and take methods.
 * Removes necessity for dealing with checked exceptions.
 * <p>
 *
 * @param <T> type of resource
 */
public class SilentBlockingResourcePool<T> {
    private final BlockingQueue<T> queue;
    private final Set<Long> threadsUse;


    /**
     *
     * @param resources to control them
     */
    public SilentBlockingResourcePool(Collection<T> resources) {
        this.queue = new LinkedBlockingQueue<>(resources);
        this.threadsUse = ConcurrentHashMap.newKeySet(resources.size());
    }

    public T take() {
        try {
            long threadId = Thread.currentThread().getId();
            if (threadsUse.contains(threadId)) {
                throw new IllegalStateException("Thread has taken resource twice!");
            }
            threadsUse.add(threadId);
            return queue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException("Thread is interrupted!", e);
        }
    }

    public void put(T elem) {
        try {
            long threadId = Thread.currentThread().getId();
            if (!threadsUse.contains(threadId)) {
                throw new IllegalStateException("Another thread returned resource!");
            }
            threadsUse.remove(threadId);
            queue.put(elem);
        } catch (InterruptedException e) {
            throw new RuntimeException("Thread is interrupted!", e);
        }
    }
}
