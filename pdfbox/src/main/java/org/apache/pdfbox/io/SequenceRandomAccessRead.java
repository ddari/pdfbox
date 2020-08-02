/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper class to combine several RandomAccessRead instances so that they can be accessed as one big RandomAccessRead.
 */
public class SequenceRandomAccessRead implements RandomAccessRead
{
    private final List<RandomAccessRead> randomAccessReadList;
    private final long[] startPositions;
    private final long[] endPositions;
    private final int numberOfReader;
    private int currentIndex = 0;
    private long currentPosition = 0;
    private long length = 0;
    private boolean isClosed = false;
    private RandomAccessRead currentRandomAccessRead = null;
    
    public SequenceRandomAccessRead(List<RandomAccessRead> randomAccessReadList)
    {
        if (randomAccessReadList == null)
        {
            throw new IllegalArgumentException("Missing input parameter");
        }
        if (randomAccessReadList.isEmpty())
        {
            throw new IllegalArgumentException("Empty list");
        }
        this.randomAccessReadList = new ArrayList<>(randomAccessReadList);
        numberOfReader = randomAccessReadList.size();
        currentRandomAccessRead = randomAccessReadList.get(currentIndex);
        startPositions = new long[numberOfReader];
        endPositions = new long[numberOfReader];
        for(int i=0;i<numberOfReader;i++) 
        {
            try
            {
                startPositions[i] = length;
                length += randomAccessReadList.get(i).length();
                endPositions[i] = length - 1;
            }
            catch (IOException e)
            {
                throw new IllegalArgumentException("Problematic list", e);
            }
        }
    }

    @Override
    public void close() throws IOException
    {
        for (RandomAccessRead randomAccessRead : randomAccessReadList)
        {
            randomAccessRead.close();
        }
        randomAccessReadList.clear();
        currentRandomAccessRead = null;
        isClosed = true;
    }

    private RandomAccessRead getCurrentReader() throws IOException
    {
        if (currentRandomAccessRead.isEOF() && currentIndex < numberOfReader - 1)
        {
            currentIndex++;
            currentRandomAccessRead = randomAccessReadList.get(currentIndex);
            currentRandomAccessRead.seek(0);
        }
        return currentRandomAccessRead;
    }

    @Override
    public int read() throws IOException
    {
        checkClosed();
        RandomAccessRead randomAccessRead = getCurrentReader();
        int value = randomAccessRead.read();
        if (value > -1)
        {
            currentPosition++;
        }
        return value;
    }

    @Override
    public int read(byte[] b) throws IOException
    {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int offset, int length) throws IOException
    {
        checkClosed();
        RandomAccessRead randomAccessRead = getCurrentReader();
        int bytesRead = randomAccessRead.read(b, offset, length);
        while (bytesRead < length && available() > 0)
        {
            randomAccessRead = getCurrentReader();
            bytesRead += randomAccessRead.read(b, offset + bytesRead, length - bytesRead);
        }
        currentPosition += bytesRead;
        return bytesRead;
    }

    @Override
    public long getPosition() throws IOException
    {
        checkClosed();
        return currentPosition;
    }

    @Override
    public void seek(long position) throws IOException
    {
        checkClosed();
        if (position < 0)
        {
            throw new IOException("Invalid position " + position);
        }
        // it is allowed to jump beyond the end of the file
        // jump to the end of the reader
        if (position >= length)
        {
            currentIndex = numberOfReader - 1;
        }
        else
        {
            for (int i = 0; i < numberOfReader; i++)
            {
                if (position >= startPositions[i] && position <= endPositions[i])
                {
                    currentIndex = i;
                    break;
                }
            }
        }
        currentRandomAccessRead = randomAccessReadList.get(currentIndex);
        currentRandomAccessRead.seek(position - startPositions[currentIndex]);
        currentPosition = position;
    }

    @Override
    public long length() throws IOException
    {
        checkClosed();
        return length;
    }

    @Override
    public void rewind(int bytes) throws IOException
    {
        seek(getPosition() - bytes);
    }

    @Override
    public boolean isClosed()
    {
        return isClosed;
    }

    /**
     * Ensure that the SequenceRandomAccessRead is not closed
     * 
     * @throws IOException
     */
    private void checkClosed() throws IOException
    {
        if (isClosed)
        {
            // consider that the rab is closed if there is no current buffer
            throw new IOException("RandomAccessBuffer already closed");
        }
    }

    @Override
    public int peek() throws IOException
    {
        int result = read();
        if (result != -1)
        {
            rewind(1);
        }
        return result;
    }

    @Override
    public boolean isEOF() throws IOException
    {
        checkClosed();
        return currentPosition >= length;
    }

    @Override
    public int available() throws IOException
    {
        checkClosed();
        return (int) Math.min(length - currentPosition, Integer.MAX_VALUE);
    }

    @Override
    public RandomAccessReadView createView(long startPosition, long streamLength) throws IOException
    {
        throw new IOException(getClass().getName() + ".createView isn't supported.");
    }

}
