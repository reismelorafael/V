package com.termux.terminal;

/** A circular byte buffer allowing one producer and one consumer thread. */
final class ByteQueue {

    private final byte[] mBuffer;
    private int mHead;
    private int mStoredBytes;
    private boolean mOpen = true;

    public ByteQueue(int size) {
        mBuffer = new byte[size];
    }

    public synchronized void close() {
        mOpen = false;
        notify();
    }

    public synchronized int read(byte[] buffer, boolean block) {
        while (mStoredBytes == 0 && mOpen) {
            if (block) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    // Ignore.
                }
            } else {
                return 0;
            }
        }
        if (!mOpen) return -1;

        final int bufferLength = mBuffer.length;
        final boolean wasFull = bufferLength == mStoredBytes;
        final int bytesToRead = Math.min(buffer.length, mStoredBytes);
        final int firstRun = Math.min(bytesToRead, bufferLength - mHead);
        final int secondRun = bytesToRead - firstRun;

        System.arraycopy(mBuffer, mHead, buffer, 0, firstRun);
        if (secondRun > 0) {
            System.arraycopy(mBuffer, 0, buffer, firstRun, secondRun);
        }

        mHead += bytesToRead;
        if (mHead >= bufferLength) mHead -= bufferLength;
        mStoredBytes -= bytesToRead;

        if (wasFull && bytesToRead > 0) notify();
        return bytesToRead;
    }

    /**
     * Attempt to write the specified portion of the provided buffer to the queue.
     * <p/>
     * Returns whether the output was totally written, false if it was closed before.
     */
    public boolean write(byte[] buffer, int offset, int lengthToWrite) {
        if (lengthToWrite + offset > buffer.length) {
            throw new IllegalArgumentException("length + offset > buffer.length");
        } else if (lengthToWrite <= 0) {
            throw new IllegalArgumentException("length <= 0");
        }

        final int bufferLength = mBuffer.length;

        synchronized (this) {
            while (lengthToWrite > 0) {
                while (bufferLength == mStoredBytes && mOpen) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // Ignore.
                    }
                }
                if (!mOpen) return false;
                final boolean wasEmpty = mStoredBytes == 0;
                final int freeBytes = bufferLength - mStoredBytes;
                final int bytesToWrite = Math.min(lengthToWrite, freeBytes);
                int tail = mHead + mStoredBytes;
                if (tail >= bufferLength) tail -= bufferLength;
                final int firstRun = Math.min(bytesToWrite, bufferLength - tail);
                final int secondRun = bytesToWrite - firstRun;

                System.arraycopy(buffer, offset, mBuffer, tail, firstRun);
                if (secondRun > 0) {
                    System.arraycopy(buffer, offset + firstRun, mBuffer, 0, secondRun);
                }

                offset += bytesToWrite;
                lengthToWrite -= bytesToWrite;
                mStoredBytes += bytesToWrite;
                if (wasEmpty) notify();
            }
        }
        return true;
    }
}
