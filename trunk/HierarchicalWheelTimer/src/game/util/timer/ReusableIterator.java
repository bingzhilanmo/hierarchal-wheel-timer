package game.util.timer;

import java.util.Iterator;

public interface ReusableIterator<E> extends Iterator<E> {
    void rewind();
}
