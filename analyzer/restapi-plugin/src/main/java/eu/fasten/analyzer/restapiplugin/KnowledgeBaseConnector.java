/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.analyzer.restapiplugin;

import eu.fasten.core.data.Constants;
import eu.fasten.core.data.callableindex.RocksDao;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.dbconnectors.PostgresConnector;
import eu.fasten.core.dbconnectors.RocksDBConnector;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.SQLException;
import java.util.Properties;

@Component
public class KnowledgeBaseConnector {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseConnector.class.getName());

    /**
     * KnowledgeBase data access object.
     */
    public static MetadataDao kbDao;

    public static RocksDao graphDao;

    public static String rcgBaseUrl;

    public static String dependencyGraphPath;


    /**
     * Database connection context
     */
    public static DSLContext dbContext;


    public static String vulnerableCallChainsPath;


    public static KafkaProducer<String, String> kafkaProducer;

    public static String ingestTopic;

    public static String forge;

    public static String dependencyResolverAddress;

    public static String initDepGraphRequest;

    /**
     * KnowledgeBase username, retrieved from the server configuration file.
     */
    @Value("${kb.user}")
    private String kbUser;

    /**
     * KnowledgeBase address, retrieved from the server configuration file.
     */
    @Value("${kb.url}")
    private String kbUrl;
    
    /**
     * Path to the serialized dependency graph
     */
    @Value("${kb.depgraph.path}")
    private String depGraphPath;


    @Value("${kb.graphdb.path}")
    private String graphdbPath;

    @Value("${kb.vulnchains.path}")
    private String vulnChainsPath;

    @Value("${lima.rcg.url}")
    private String rcgUrl;

    @Value("${kafka.address}")
    private String kafkaAddress;

    @Value("${kafka.output.topic}")
    private String kafkaOutputTopic;

    /**
     * KnowledgeBase Forge, retrieved from the server configuration file.
     */

    @Value("${forge}")
    private String kbForge;

    /**
     * The IP Address of the Dependency Resolver Microsrervice used for C or Python
     */

    @Value("${dependency.resolver.address}")
    private String dependencyResolverApiAddress;

    /**
     * Request url used to initialize the Dependency Graph, retrieved from the server configuration file.
     */

    @Value("${init.depgraph.request}")
    private String depgraphRequest;

    /**
     * Connects to the KnowledgeBase before starting the REST server.
     */
    @PostConstruct
    public void connectToKnowledgeBase() {
        try {
            logger.info("Establishing connection to the " + kbForge + " KnowledgeBase at " + kbUrl + ", user " + kbUser + "...");
            // JDBC auto-commit should be false: (1) Postgres doesn't like it when using non-zero fetch size.
            // (2) It is not usually a good practice.
            dbContext = PostgresConnector.getDSLContext(kbUrl, kbUser, false);
            kbDao = new MetadataDao(dbContext);
        } catch (SQLException e) {
            logger.error("Couldn't connect to the KnowledgeBase", e);
            System.exit(1);
        }
        logger.info("...KnowledgeBase connection established successfully.");
    }

    /**
     * Sets base URL for retrieving JSON RCGs.
     */
    @PostConstruct
    public void setLimaUrl() {
        var url = this.rcgUrl;
        if (!url.endsWith("/")) {
            url += "/";
        }
        KnowledgeBaseConnector.rcgBaseUrl = url;
        logger.info("RCG base URL successfully set");
    }


    /**
     * Retrieves the dependency graph if possible, otherwise constructs the graph from database.
     */
    @PostConstruct
    public void setDependencyGraphPath() {
        KnowledgeBaseConnector.dependencyGraphPath = depGraphPath;
    }

    /**
     * Retrieves the dependency graph if possible, otherwise constructs the graph from database.
     */
    @PostConstruct
    public void setDependencyResolverPath() {
        KnowledgeBaseConnector.dependencyResolverAddress= dependencyResolverApiAddress;
    }

    /**
     * Retrieves the vulnerability call chains path if possible.
     */
    @PostConstruct
    public void setVulnerableCallChainsPath() {
        KnowledgeBaseConnector.vulnerableCallChainsPath = vulnChainsPath;
    }

    @PostConstruct
    public void setForge() {
        forge = this.kbForge;
    }

    @PostConstruct
    public void setInitDepGraphRequest() {
        initDepGraphRequest = this.depgraphRequest;
    }

    /**
     * Established read-only connection to the graph database.
     */
    @PostConstruct
    public void connectToGraphDB() {
        if (forge.equals(Constants.mvnForge)) {
            logger.info("Establishing connection to the Graph Database at " + graphdbPath + "...");
            try {
                graphDao = RocksDBConnector.createReadOnlyRocksDBAccessObject(graphdbPath);
            } catch (RuntimeException e) {
                logger.error("Couldn't connect to the Graph Database", e);
                System.exit(1);
            }
            logger.info("...Graph database connection established successfully.");
        }
    }

    @PostConstruct
    public void initKafkaProducer() {
        if (forge.equals(Constants.pypiForge) | forge.equals(Constants.mvnForge)) {
            ingestTopic = this.kafkaOutputTopic;
            var producerProperties = new Properties();
            producerProperties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaAddress);
            producerProperties.setProperty(ProducerConfig.CLIENT_ID_CONFIG, "fasten_restapi_producer");
            producerProperties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProperties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProperties.setProperty(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, "3000000");
            kafkaProducer = new KafkaProducer<>(producerProperties);
        }
    }
}
