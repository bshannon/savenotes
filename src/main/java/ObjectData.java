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

import java.nio.charset.StandardCharsets;

/**
 * A single data item from an archived Objective-C object.
 */
public class ObjectData {
    /**
     * The index (field number?) of the field in the containing object.
     */
    private int index;

    /**
     * The data.
     */
    private Object data;

    /**
     * Construct an ObjectData for the given index.
     */
    public ObjectData(int index) {
        this.index = index;
    }

    public int index() {
        return index;
    }

    void setData(Object data) {
        this.data = data;
    }

    public int getInt() {
        return ((Integer)data).intValue();
    }

    public float getFloat() {
        return ((Float)data).floatValue();
    }

    public String getString() {
        return new String((byte[])data, StandardCharsets.UTF_8);
    }

    public boolean getBoolean() {
        int b = ((Integer)data).intValue();
        assert b == 0 || b == 1;
        return b == 1;
    }

    public byte[] getBytes() {
        return (byte[])data;
    }

    /**
     * If the data representes a nested structure (stored as a byte array),
     * return a new ArchivedObjectReader to read the data items contained
     * in the nested object.
     */
    public ArchivedObjectReader getObject() {
        return new ArchivedObjectReader((byte[])data);
    }
}
