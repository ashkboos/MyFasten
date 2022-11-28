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

package eu.fasten.analyzer.qualityanalyzer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class QualityAnalyzerPluginTest {
    private QualityAnalyzerPlugin.QualityAnalyzer qualityAnalyzer;

    @BeforeEach
    public void setup() {
        qualityAnalyzer = new QualityAnalyzerPlugin.QualityAnalyzer();
        qualityAnalyzer.setTopics(Collections.singletonList("fasten.RapidPlugin.callable.out"));
    }

    @Test
    public void consumerTopicsTest() {
        var topics = Optional.of(Collections.singletonList("fasten.RapidPlugin.callable.out"));
        assertEquals(topics, qualityAnalyzer.consumeTopic());
    }

    @Test
    public void consumerTopicChangeTest() {
        var topics1 = Optional.of(Collections.singletonList("fasten.RapidPlugin.callable.out"));
        assertEquals(topics1, qualityAnalyzer.consumeTopic());
        var differentTopic = Collections.singletonList("DifferentKafkaTopic");
        qualityAnalyzer.setTopics(differentTopic);
        assertEquals(Optional.of(differentTopic), qualityAnalyzer.consumeTopic());
    }
}
