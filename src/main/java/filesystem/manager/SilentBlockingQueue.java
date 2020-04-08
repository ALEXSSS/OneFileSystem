package filesystem.manager;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SilentBlockingQueue<T> {
    private final BlockingQueue<T> queue;


    public SilentBlockingQueue(int capacity) {
        this.queue = new LinkedBlockingQueue<T>(capacity);
    }

    public T take() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            throw new IllegalStateException("Thread is interrupted!", e);
        }
    }

    public void put(T elem) {
        try {
            queue.put(elem);
        } catch (InterruptedException e) {
            throw new IllegalStateException("Thread is interrupted!", e);
        }
    }
}
