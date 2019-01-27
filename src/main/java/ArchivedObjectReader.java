/*
 * Copyright (c) 2019 Bill Shannon. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of the copyright holder nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Read data from a (non-keyed) Objective-C archived object.
 */
public class ArchivedObjectReader {
    private InputStream is;
    private byte[] bytes;

    // the known datat types
    private static final int D_INT = 0;
    private static final int D_BYTE_ARRAY = 2;
    private static final int D_FLOAT = 5;

    /**
     * Read the object data from the InputStream.
     */
    public ArchivedObjectReader(InputStream is) throws IOException {
        if (!is.markSupported())
            is = new BufferedInputStream(is);
        this.is = is;
    }

    /**
     * Read the object data from the byte array.
     */
    public ArchivedObjectReader(byte[] ba) {
        bytes = ba;
        is = new ByteArrayInputStream(ba);
    }

    /**
     * Return the bytes backing this object.
     * Used for debug output.
     */
    public byte[] getBytes() {
        return bytes;
    }

    /**
     * Return the next data item.
     */
    public ObjectData next() throws IOException {
        int c = is.read();
        if (c == -1)
            return null;
        ObjectData nd = new ObjectData(c >> 3);
        int type = c & 0x07;
        switch (type) {
        case D_INT:
            long val = getLong();
            if (val > Integer.MAX_VALUE)
                err("int too large %x", val);
            nd.setData(Integer.valueOf((int)val));
            break;
        case D_FLOAT:
            float fval = getFloat();
            nd.setData(Float.valueOf(fval));
            break;
        case D_BYTE_ARRAY:
            int len = getInt();
            byte[] ba = new byte[len];
            for (int i = 0; i < len; i++)
                ba[i] = (byte)is.read();
            nd.setData(ba);
            break;
        default:
            err("Unknown data type: %d", type);
        }
        return nd;
    }

    /**
     * Read a float value.
     */
    private float getFloat() throws IOException {
        int b0 = is.read();
        int b1 = is.read();
        int b2 = is.read();
        int b3 = is.read();
        int f = (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
        return Float.intBitsToFloat(f);
    }

    /**
     * Read an integer value.
     */
    private int getInt() throws IOException {
        int len = 0;
        for (int i = 0; ; i++) {
            int c = is.read();
            len += (c & 0x7f) << (7 * i);
            if ((c & 0x80) == 0)
                break;
        }
        return len;
    }

    /**
     * Read a long integer value.
     */
    private long getLong() throws IOException {
        long len = 0;
        for (int i = 0; ; i++) {
            int c = is.read();
            len += (c & 0x7f) << (7 * i);
            if ((c & 0x80) == 0)
                break;
        }
        return len;
    }

    /**
     * Print an error message.
     */
    private static void err(String s, Object... args) {
        System.out.printf("ERR: " + s, args);
        System.out.println();
    }
}
