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

package eu.fasten.core.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Objects;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PartialCCallGraphTest {

    private static PartialCCallGraph graph;

    @BeforeAll
    static void setUp() throws IOException, URISyntaxException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("extended-revision-call-graph/testCRCG.json")).toURI().getPath());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        graph = new PartialCCallGraph(new JSONObject(tokener));
    }

   @Test
    void getNodeCount() {
        assertEquals(5, graph.getNodeCount());
    }

    @Test
    void toJSON() throws FileNotFoundException, URISyntaxException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("extended-revision-call-graph/testCRCG.json")).toURI().getPath());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        JSONObject jsonGraph = new JSONObject(tokener);

        assertEquals(jsonGraph.getJSONObject("functions").getJSONObject("internal").toString(),
                graph.toJSON().getJSONObject("functions").getJSONObject("internal").toString());

        assertEquals(jsonGraph.getJSONObject("functions").getJSONObject("external").toString(),
                graph.toJSON().getJSONObject("functions").getJSONObject("external").toString());

        assertEquals(jsonGraph.getJSONObject("graph").toMap(),
                graph.toJSON().getJSONObject("graph").toMap());
    }

    @Test
    void toJSONFromCHA() {
        assertEquals(graph.classHierarchyToJSON(graph.getClassHierarchy()).toString(),
                graph.toJSON().getJSONObject("functions").toString());
    }

    @Test
    void mapOfAllMethods() {
        var methodsMap = graph.mapOfAllMethods();

        assertEquals(5, methodsMap.size());

        assertEquals("/binary;C/foo()",
                methodsMap.get(0).getUri().toString());
        assertEquals("/;./binary.c;bar()",
                methodsMap.get(1).getUri().toString());
        assertEquals("//libfoo-dev/libfoo.a;C/buz()",
                methodsMap.get(2).getUri().toString());
        assertEquals("///libc.so;C/fgets()",
                methodsMap.get(3).getUri().toString());
        assertEquals("//libfoo-dev/;%2Fusr%2Finclude%2Flibfoo/client.h;bar()",
                methodsMap.get(4).getUri().toString());
    }

    @Test
    void isCallGraphEmptyEmptyInternal() throws IOException, URISyntaxException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("extended-revision-call-graph/testCRCGEmptyInternal.json")).toURI().getPath());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        var cg = new PartialCCallGraph(new JSONObject(tokener));

        assertFalse(cg.isCallGraphEmpty());
    }

    @Test
    void isCallGraphEmptyEmptyExternal() throws IOException, URISyntaxException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("extended-revision-call-graph/testCRCGEmptyExternal.json")).toURI().getPath());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        var cg = new PartialCCallGraph(new JSONObject(tokener));

        assertFalse(cg.isCallGraphEmpty());
    }

    @Test
    void isCallGraphEmptyEmptyResolved() throws IOException, URISyntaxException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("extended-revision-call-graph/testCRCGEmptyResolved.json")).toURI().getPath());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        var cg = new PartialCCallGraph(new JSONObject(tokener));

        assertFalse(cg.isCallGraphEmpty());
    }

    @Test
    void isCallGraphEmptyEmptyAll() throws IOException, URISyntaxException {
        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("extended-revision-call-graph/testCRCGEmptyAll.json")).toURI().getPath());

        JSONTokener tokener = new JSONTokener(new FileReader(file));

        var cg = new PartialCCallGraph(new JSONObject(tokener));

        assertTrue(cg.isCallGraphEmpty());
    }

    @Test
    void isCallGraphEmptyAllExist() {
        assertFalse(graph.isCallGraphEmpty());
    }

    @Test
    void getCgGenerator() {
        assertEquals("cscout", graph.getCgGenerator());
    }
}
