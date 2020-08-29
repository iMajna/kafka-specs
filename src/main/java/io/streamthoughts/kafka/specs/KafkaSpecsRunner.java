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

import io.streamthoughts.kafka.specs.acl.AclRulesBuilder;
import io.streamthoughts.kafka.specs.acl.builder.LiteralAclRulesBuilder;
import io.streamthoughts.kafka.specs.acl.builder.TopicMatchingAclRulesBuilder;
import io.streamthoughts.kafka.specs.command.ClusterCommand;
import io.streamthoughts.kafka.specs.command.ExecuteAclCommand;
import io.streamthoughts.kafka.specs.command.ExecuteTopicCommand;
import io.streamthoughts.kafka.specs.command.ExportClusterSpecCommand;
import io.streamthoughts.kafka.specs.internal.AdminClientUtils;
import org.apache.kafka.clients.admin.AdminClient;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The command line main class.
 */
public class KafkaSpecsRunner {

    public static void main(String[] args) {

        final KafkaSpecsRunnerOptions options = new KafkaSpecsRunnerOptions(args);
        String[] clusterPath =  Stream.of(args).filter(str -> str.endsWith("yaml"))
                                .collect(Collectors.toSet()).toArray(new String[0]);
        String[] splitPath = clusterPath[0].split("/");
        String[] namespaceSpliter = splitPath[splitPath.length-1].split(".yaml");
        String namespace = namespaceSpliter[namespaceSpliter.length-1];


        if(args.length == 0) {
            CLIUtils.printUsageAndDie(options.parser, "Create, Alter, Delete or Describe Kafka cluster resources");
        }

        if(!options.hasSingleAction()) {
            CLIUtils.printUsageAndDie(options.parser, "Command must include exactly one action: --execute, --describe");
        }

        options.checkArgs();

        int exitCode = 0;
        try (AdminClient client = AdminClientUtils.newAdminClient(options)) {

            if (options.isExecuteCommand() ) {
                if (! options.isAssumeYes()) {
                    CLIUtils.askToProceed();
                }

                Collection<OperationResult> results = new LinkedList<>();

                if (options.entityTypes().contains(EntityType.TOPICS)) {
                    ExecuteTopicCommand command = new ExecuteTopicCommand(client, namespace);
                    results.addAll(command.execute(options));
                }

                if (options.entityTypes().contains(EntityType.ACLS)) {
                    AclRulesBuilder builder = AclRulesBuilder.combines(
                            new LiteralAclRulesBuilder(),
                            new TopicMatchingAclRulesBuilder(client));
                    ExecuteAclCommand command = new ExecuteAclCommand(client, builder);
                    results.addAll(command.execute(options));
                }
                Printer.printAndExit(results, options.verbose());
            }

            if (options.isExportCommand()) {
                ClusterCommand command = new ExportClusterSpecCommand(client);
                command.execute(options);
            }

        } catch (Exception e) {
            e.printStackTrace();
            exitCode = 1;
        } finally {
            System.exit(exitCode);
        }
    }
}
