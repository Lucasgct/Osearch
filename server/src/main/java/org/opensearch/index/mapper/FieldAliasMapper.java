/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.index.mapper;

import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.support.XContentMapValues;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import static java.lang.String.format;

/**
 * A mapper for field aliases.
 *
 * A field alias has no concrete field mappings of its own, but instead points to another field by
 * its path. Once defined, an alias can be used in place of the concrete field name in search requests.
 *
 * @opensearch.internal
 */
public class FieldAliasMapper extends Mapper {
    public static final String CONTENT_TYPE = "alias";

    /**
     * Parameter names
     *
     * @opensearch.internal
     */
    public static class Names {
        public static final String PATH = "path";
    }

    private final String name;
    private final String path;

    public FieldAliasMapper(String simpleName, String name, String path) {
        super(simpleName);
        this.name = name;
        this.path = path;
    }
    public String contentType() {
        return CONTENT_TYPE;
    }
    @Override
    public String name() {
        return name;
    }

    @Override
    public String typeName() {
        return CONTENT_TYPE;
    }

    public String path() {
        return path;
    }

    @Override
    public Mapper merge(Mapper mergeWith) {
        if (!(mergeWith instanceof FieldAliasMapper)) {
            throw new IllegalArgumentException(
                format("Cannot merge a field %s mapping [%s] with a mapping that is not for a field %s.",contentType(),name(),contentType())
            );
        }
        return mergeWith;
    }

    @Override
    public Iterator<Mapper> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.startObject(simpleName()).field("type", CONTENT_TYPE).field(Names.PATH, path).endObject();
    }

    @Override
    public void validate(MappingLookup mappers) {
        if (Objects.equals(this.path(), this.name())) {
            throw new MapperParsingException(
                format("Invalid [path] value [%s] for field %s [%s]: an %s cannot refer to itself.",path(),contentType(),name(),contentType()));
        }
        if (mappers.fieldTypes().get(path) == null) {
            throw new MapperParsingException(
                format("Invalid [path] value [%s] for field %s [%s]: an %s must refer to an existing field in the mappings.",path(),contentType(),name(),contentType()));
        }
        if (mappers.getMapper(path) instanceof FieldAliasMapper) {
            throw new MapperParsingException(format("Invalid [path] value [%s] for field %s [%s]: an %s cannot refer to another %s.",path(),contentType(),name(),contentType(),contentType()));
        }
        String aliasScope = mappers.getNestedScope(name);
        String pathScope = mappers.getNestedScope(path);

        if (!Objects.equals(aliasScope, pathScope)) {
            StringBuilder message = new StringBuilder(
                format("Invalid [path] value [%s] for field %s [%s]: an %s must have the same nested scope as its target. ",path(),contentType(),name(),contentType())
            );
            message.append(aliasScope == null ? format("The %s is not nested",contentType()) : format("The %s's nested scope is [%s]",contentType(),aliasScope));
            message.append(", but ");
            message.append(pathScope == null ? "the target is not nested." : format("the target's nested scope is [%s].",pathScope));
            throw new IllegalArgumentException(message.toString());
        }
    }

    /**
     * The type parser
     *
     * @opensearch.internal
     */
    public static class TypeParser implements Mapper.TypeParser {
        @Override
        public Mapper.Builder parse(String name, Map<String, Object> node, ParserContext parserContext) throws MapperParsingException {
            FieldAliasMapper.Builder builder = new FieldAliasMapper.Builder(name);
            Object pathField = node.remove(Names.PATH);
            String path = XContentMapValues.nodeStringValue(pathField, null);
            if (path == null) {
                throw new MapperParsingException("The [path] property must be specified for field [" + name + "].");
            }
            return builder.path(path);
        }
    }

    /**
     * The bulider for the field alias field mapper
     *
     * @opensearch.internal
     */
    public static class Builder extends Mapper.Builder<FieldAliasMapper.Builder> {
        protected String name;
        protected String path;

        protected Builder(String name) {
            super(name);
            this.name = name;
        }

        public String name() {
            return this.name;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public FieldAliasMapper build(BuilderContext context) {
            String fullName = context.path().pathAsText(name);
            return new FieldAliasMapper(name, fullName, path);
        }
    }
}
