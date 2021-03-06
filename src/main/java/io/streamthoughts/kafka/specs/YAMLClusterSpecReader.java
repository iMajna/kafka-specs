/*
 * Copyright 2020 StreamThoughts.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.streamthoughts.kafka.specs;

import io.streamthoughts.kafka.specs.acl.AclGroupPolicy;
import io.streamthoughts.kafka.specs.acl.AclUserPolicy;
import io.streamthoughts.kafka.specs.reader.AclGroupPolicyReader;
import io.streamthoughts.kafka.specs.reader.AclUserPolicyReader;
import io.streamthoughts.kafka.specs.reader.EntitySpecificationReader;
import io.streamthoughts.kafka.specs.reader.MapObjectReader;
import io.streamthoughts.kafka.specs.reader.TopicClusterSpecReader;
import io.streamthoughts.kafka.specs.resources.TopicResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Class used to read a Kafka cluster specification a from a YAML input file.
 */
public class YAMLClusterSpecReader implements ClusterSpecReader {

    private static final Logger LOG = LoggerFactory.getLogger(YAMLClusterSpecReader.class);

    private static final String VERSION_FIELD = "version";

    static final SpecVersion CURRENT_VERSION = SpecVersion.VERSION_1;

    public enum SpecVersion {
        VERSION_1("1") {
            @SuppressWarnings("unchecked")
            @Override
            ClusterSpec read(Map<String, Object> specification) {
                final Set<TopicResource> mTopics = read(Fields.TOPICS_FIELD, specification, new TopicClusterSpecReader());

                Map<String, Object> acls = (Map<String, Object>) specification.get(Fields.ACL_FIELD);

                final Set<AclGroupPolicy> mGroups = read(Fields.ACL_GROUP_POLICIES_FIELD, acls, new AclGroupPolicyReader());
                final Set<AclUserPolicy> sUsers = read(Fields.ACL_ACCESS_POLICIES_FIELD, acls, new AclUserPolicyReader());

                return new ClusterSpec(mTopics, mGroups, sUsers);
            }
        };

        private String version;

        SpecVersion(final String version) {
            this.version = version;
        }

        public String version() {
            return version;
        }

        abstract ClusterSpec read(final Map<String, Object> specification);


        /**
         * Read the specification entity with the specified reader.
         *
         * @param key       the entity key to read.
         * @param input     the specification input.
         * @param reader    the reader
         * @param <T>       the output entity type.
         *
         * @return          a set of new {@link T} instance.
         */
        protected static <T> Set<T> read(final String key, final Map<String, Object> input, final EntitySpecificationReader<T> reader) {
            if (input == null) return Collections.emptySet();

            return Optional.ofNullable(input.get(key))
                    .map(t -> reader.read(MapObjectReader.toList(t)))
                    .orElse(Collections.emptySet());
        }

        public static Optional<SpecVersion> getVersionFromString(final String version) {
            for (SpecVersion e : SpecVersion.values()) {
                if (e.version().endsWith(version)) {
                    return Optional.of(e);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @SuppressWarnings("unchecked")
    public ClusterSpec read(final InputStream stream) {
        Yaml yaml = new Yaml();
        Map<String, Object> specification = yaml.load(stream);
        requireNonNull(specification, "Cluster specification is empty or invalid.");

        final Object version = specification.get(VERSION_FIELD);
        if (version == null) {
            LOG.warn("No version found in input specification file, using current version {}", CURRENT_VERSION.version);
            return CURRENT_VERSION.read(specification);
        }

        Optional<SpecVersion> specVersion = SpecVersion.getVersionFromString(version.toString());
        return specVersion.orElseGet(() -> {
            LOG.info("Unknown version '{}', using current version {}", version, CURRENT_VERSION);
            return CURRENT_VERSION;
        }).read(specification);
    }


    private static void requireNonNull(final Object o, final String message) {
        if (o == null) {
            throw new InvalidSpecificationException(message);
        }
    }
}
