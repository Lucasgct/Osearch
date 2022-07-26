/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.common.io.stream;

import org.opensearch.transport.TransportRequest;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Extensibility support for Named Writeable Registry: request to extensions to parse context
 *
 * @opensearch.internal
 */
public class NamedWriteableRegistryParseRequest extends TransportRequest {

    private final Class categoryClass;
    private byte[] context;

    /**
     * @param categoryClassName String representing the fully qualified class name used to generate the corresponding class object at runtime
     * @param context StreamInput object to convert into a byte array and transport to the extension
     * @throws IllegalArgumentException if the fully qualified class name is invalid and the class object cannot be generated at runtime
     */
    public NamedWriteableRegistryParseRequest(String categoryClassName, StreamInput context) {
        try {
            byte[] streamInputBytes = context.readAllBytes();
            this.categoryClass = Class.forName(categoryClassName);
            this.context = Arrays.copyOf(streamInputBytes, streamInputBytes.length);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Category class definition not found", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid context", e);
        }
    }

    /**
     * @param in StreamInput from which class fields are read from
     * @throws IllegalArgumentException if the fully qualified class name is invalid and the class object cannot be generated at runtime
     */
    public NamedWriteableRegistryParseRequest(StreamInput in) throws IOException {
        super(in);
        try {
            this.categoryClass = Class.forName(in.readString());
            this.context = in.readByteArray();
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Category class definition not found", e);
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeString(categoryClass.getName());
        out.writeByteArray(context);
    }

    @Override
    public String toString() {
        return "NamedWriteableRegistryParseRequest{"
            + "categoryClass="
            + categoryClass.getName()
            + ", context length="
            + context.length
            + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NamedWriteableRegistryParseRequest that = (NamedWriteableRegistryParseRequest) o;
        return Objects.equals(categoryClass, that.categoryClass) && Objects.equals(context, that.context);
    }

    @Override
    public int hashCode() {
        return Objects.hash(categoryClass, context);
    }

    /**
     * Returns the class instance of the category class sent over by the SDK
     */
    public Class getCategoryClass() {
        return this.categoryClass;
    }

    /**
     * Returns a copy of a byte array that a {@link Writeable.Reader} will be applied to. This byte array is generated from a {@link StreamInput} instance and transported to the SDK for deserialization.
     */
    public byte[] getContext() {
        return Arrays.copyOf(this.context, this.context.length);
    }
}
