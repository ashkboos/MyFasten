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

package eu.fasten.core.merge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.common.collect.BiMap;
import eu.fasten.core.data.ArrayImmutableDirectedGraph;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.callableindex.RocksDao;
import eu.fasten.core.data.callableindex.SourceCallSites;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.ModuleNames;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLongImmutablePair;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.rocksdb.RocksDBException;

public class CGMergerTest {

    private final static long MAIN_INIT = 0;
    private final static long MAIN_MAIN_METHOD = 1;
    private final static long FOO_CLINIT = 100;
    private final static long FOO_INIT = 101;
    private final static long FOO_FOO_METHOD = 102;
    private final static long FOO_STATIC_METHOD = 103;
    private final static long BAR_INIT = 200;
    private final static long BAR_SUPER_METHOD = 201;
    private final static long BAZ_INIT = 300;
    private final static long BAZ_SUPER_METHOD = 301;

    private static Map<Long, String> typeDictionary;
    private static Map<Long, String> typeMap;
    private static Map<String, Pair<Long[], Long[]>> universalCHA;
    private static Map<String, Long> namespacesMap;
    private static SourceCallSites graphMetadata;

    private static CGMerger merger;
    private static PartialJavaCallGraph importer;
    private static PartialJavaCallGraph imported;


    @BeforeAll
    static void setUp() throws FileNotFoundException, URISyntaxException {
        typeMap = Map.of(
            MAIN_INIT, "/test.group/Main.%3Cinit%3E()%2Fjava.lang%2FVoidType",
            MAIN_MAIN_METHOD,
            "/test.group/Main.main(%2Fjava.lang%2FString%5B%5D)%2Fjava.lang%2FVoidType",
            (long) 2, "/java.lang/Object.%3Cinit%3E()VoidType",
            (long) 3,
            "/test.group/Baz.%3Cinit%3E(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType",
            (long) 4, "/test.group/Bar.superMethod()%2Fjava.lang%2FVoidType",
            (long) 5,
            "/test.group/Bar.%3Cinit%3E(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType",
            (long) 6, "/test.group/Foo.staticMethod()%2Fjava.lang%2FIntegerType",
            (long) 7,
            "/test.group/Foo.%3Cinit%3E(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType"
        );

        typeDictionary = Map.of(
            MAIN_INIT, "/test.group/Main.%3Cinit%3E()%2Fjava.lang%2FVoidType",
            MAIN_MAIN_METHOD,
            "/test.group/Main.main(%2Fjava.lang%2FString%5B%5D)%2Fjava.lang%2FVoidType",
            FOO_CLINIT, "/test.group/Foo.%3Cclinit%3E()%2Fjava.lang%2FVoidType",
            FOO_INIT,
            "/test.group/Foo.%3Cinit%3E(%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType",
            FOO_FOO_METHOD, "/test.group/Foo.fooMethod()%2Fjava.lang%2FVoidType",
            FOO_STATIC_METHOD, "/test.group/Foo.staticMethod()%2Fjava.lang%2FIntegerType",
            BAR_INIT,
            "/test.group/Bar.%3Cinit%3E(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType",
            BAR_SUPER_METHOD, "/test.group/Bar.superMethod()%2Fjava.lang%2FVoidType",
            BAZ_INIT,
            "/test.group/Baz.%3Cinit%3E(%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType,%2Fjava.lang%2FIntegerType)%2Fjava.lang%2FVoidType",
            BAZ_SUPER_METHOD, "/test.group/Baz.superMethod()%2Fjava.lang%2FVoidType"
        );

        universalCHA = Map.of(
            "/test.group/Main", Pair.of(new Long[] {}, new Long[] {}),
            "/test.group/Foo", Pair.of(new Long[] {2L}, new Long[] {}),
            "/test.group/Bar", Pair.of(new Long[] {2L}, new Long[] {}),
            "/test.group/Baz", Pair.of(new Long[] {4L}, new Long[] {})
        );

        namespacesMap = Map.of(
            "/test.group/Main", 1L,
            "/java.lang/Object", 2L,
            "/test.group/Baz", 3L,
            "/test.group/Bar", 4L,
            "/test.group/Foo", 5L
        );

        var gid2nodeMap = new Long2ObjectOpenHashMap<SourceCallSites.SourceMethodInf>();
        gid2nodeMap.put(MAIN_INIT,
            new SourceCallSites.SourceMethodInf("/test.group/Main", "<init>()/java.lang/VoidType",
                Set.of(new SourceCallSites.CallSite(6,
                    SourceCallSites.CallSite.InvocationInstruction.SPECIAL, "<init>()VoidType",
                    List.of("/java.lang/Object")))));
        gid2nodeMap.put(MAIN_MAIN_METHOD, new SourceCallSites.SourceMethodInf("/test.group/Main",
            "main(/java.lang/String[])/java.lang/VoidType", Set.of(
            new SourceCallSites.CallSite(8, SourceCallSites.CallSite.InvocationInstruction.SPECIAL,
                "<init>(/java.lang/IntegerType,/java.lang/IntegerType,/java.lang/IntegerType)/java.lang/VoidType",
                List.of("/test.group/Baz")),
            new SourceCallSites.CallSite(9, SourceCallSites.CallSite.InvocationInstruction.VIRTUAL
                , "superMethod()/java.lang/VoidType", List.of("/test.group/Bar")),
            new SourceCallSites.CallSite(11, SourceCallSites.CallSite.InvocationInstruction.SPECIAL,
                "<init>(/java.lang/IntegerType,/java.lang/IntegerType)/java.lang/VoidType",
                List.of("/test.group/Bar")),
            new SourceCallSites.CallSite(14, SourceCallSites.CallSite.InvocationInstruction.STATIC,
                "staticMethod()/java.lang/IntegerType", List.of("/test.group/Foo")),
            new SourceCallSites.CallSite(15, SourceCallSites.CallSite.InvocationInstruction.SPECIAL,
                "<init>(/java.lang/IntegerType)/java.lang/VoidType", List.of("/test.group/Foo"))
        )));
        gid2nodeMap.put(BAZ_INIT, new SourceCallSites.SourceMethodInf("/test.group/Baz",
            "<init>(/java.lang/IntegerType,/java.lang/IntegerType,/java.lang/IntegerType)/java.lang/VoidType",
            Set.of()));
        gid2nodeMap.put(BAR_SUPER_METHOD, new SourceCallSites.SourceMethodInf("/test.group/Bar",
            "superMethod()/java.lang/VoidType", Set.of()));
        gid2nodeMap.put(BAR_INIT, new SourceCallSites.SourceMethodInf("/test.group/Bar",
            "<init>(/java.lang/IntegerType,/java.lang/IntegerType)/java.lang/VoidType", Set.of()));
        gid2nodeMap.put(FOO_STATIC_METHOD, new SourceCallSites.SourceMethodInf("/test.group/Foo",
            "staticMethod()/java.lang/IntegerType", Set.of()));
        gid2nodeMap.put(FOO_INIT, new SourceCallSites.SourceMethodInf("/test.group/Foo",
            "<init>(/java.lang/IntegerType)/java.lang/VoidType", Set.of()));

        //TODO add all methods to graphmetadata
        graphMetadata = new SourceCallSites(gid2nodeMap);

        var file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
            .getResource("merge/Imported.json")).toURI().getPath());
        JSONTokener tokener = new JSONTokener(new FileReader(file));
        imported = new PartialJavaCallGraph(new JSONObject(tokener));

        file = new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
            .getResource("merge/Importer.json")).toURI().getPath());
        tokener = new JSONTokener(new FileReader(file));
        importer = new PartialJavaCallGraph(new JSONObject(tokener));
    }

    @Disabled
    @Test
    public void mergeWithCHATest() throws RocksDBException {
        var connection = new MockConnection(new MockProvider());
        var context = DSL.using(connection, SQLDialect.POSTGRES);

        var directedGraph = createMockDirectedGraph();

        var rocksDao = Mockito.mock(RocksDao.class);
        Mockito.when(rocksDao.getGraphData(42)).thenReturn(directedGraph);
        Mockito.when(rocksDao.getGraphMetadata(42, directedGraph)).thenReturn(graphMetadata);
        var pckgs = List.of("group1:art1:ver1", "group2:art2:ver2");
        var id = new HashSet<>(Collections.singletonList(42l));
        var mergerMock = Mockito.mock(CGMerger.class);
        Mockito.when(mergerMock.getDependenciesIds(pckgs, context)).thenReturn(id);

        merger = new CGMerger(id, context, rocksDao);

        var mergedGraph = merger.mergeWithCHA(42l);

        assertNotNull(mergedGraph);

        assertEquals(new HashSet<>(mergedGraph.successors(MAIN_MAIN_METHOD)),
            Set.of(BAR_SUPER_METHOD, BAZ_INIT, BAR_INIT, FOO_STATIC_METHOD, FOO_INIT));
    }

    private DirectedGraph createMockDirectedGraph() {
        var directedGraph = new ArrayImmutableDirectedGraph.Builder();
        directedGraph.addInternalNode(MAIN_INIT);
        directedGraph.addInternalNode(MAIN_MAIN_METHOD);
        typeMap.keySet().stream()
            .filter(n -> n != MAIN_INIT && n != MAIN_MAIN_METHOD)
            .forEach(directedGraph::addExternalNode);

        directedGraph.addArc(MAIN_INIT, MAIN_INIT);
        directedGraph.addArc(MAIN_INIT, 2);
        typeMap.keySet().stream()
            .filter(n -> n != MAIN_INIT && n != MAIN_MAIN_METHOD)
            .forEach(n -> directedGraph.addArc(MAIN_MAIN_METHOD, n));

        return directedGraph.build();
    }

    private static class MockProvider implements MockDataProvider {

        private final DSLContext context;

        private final String modulesIdsQuery;
        private final String universalCHAQuery;
        private final String namespacesQuery;
        private final String typeDictionaryQuery;
        private final String typeMapQuery;
        private final String dependenciesQuery;

        public MockProvider() {
            this.context = DSL.using(SQLDialect.POSTGRES);

            this.modulesIdsQuery = context
                .select(Callables.CALLABLES.MODULE_ID)
                .from(Callables.CALLABLES)
                .getSQL();
            this.namespacesQuery = context
                .select(ModuleNames.MODULE_NAMES.ID, ModuleNames.MODULE_NAMES.NAME)
                .from(ModuleNames.MODULE_NAMES)
                .getSQL();
            this.universalCHAQuery = context
                .select(Modules.MODULES.MODULE_NAME_ID, Modules.MODULES.SUPER_CLASSES,
                    Modules.MODULES.SUPER_INTERFACES)
                .from(Modules.MODULES)
                .getSQL();
            this.typeDictionaryQuery = context
                .select(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                .from(Callables.CALLABLES)
                .getSQL();
            this.typeMapQuery = context
                .select(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                .from(Callables.CALLABLES)
                .getSQL();
            this.dependenciesQuery = context
                .select(PackageVersions.PACKAGE_VERSIONS.ID)
                .from(PackageVersions.PACKAGE_VERSIONS)
                .getSQL();
        }

        @Override
        public MockResult[] execute(MockExecuteContext ctx) {
            MockResult[] mock = new MockResult[1];

            var sql = ctx.sql();

            if (sql.startsWith(modulesIdsQuery)) {
                mock[0] = new MockResult(0, context.newResult(Callables.CALLABLES.MODULE_ID));

            } else if (sql.startsWith(dependenciesQuery)) {
                mock[0] = new MockResult(0, context.newResult(PackageVersions.PACKAGE_VERSIONS.ID));

            } else if (sql.startsWith(universalCHAQuery)) {
                mock[0] = createUniversalCHA();

            } else if (sql.startsWith(typeDictionaryQuery)) {
                mock[0] = createTypeDictionary();

            } else if (sql.startsWith(typeMapQuery)) {
                mock[0] = createTypeMap();
            } else if (sql.startsWith(namespacesQuery)) {
                mock[0] = createNamespaces();
            }

            return mock;
        }

        private MockResult createUniversalCHA() {
            var result =
                context.newResult(Modules.MODULES.MODULE_NAME_ID, Modules.MODULES.SUPER_CLASSES,
                    Modules.MODULES.SUPER_INTERFACES);
            for (var type : universalCHA.entrySet()) {
                result.add(context
                    .newRecord(Modules.MODULES.MODULE_NAME_ID, Modules.MODULES.SUPER_CLASSES,
                        Modules.MODULES.SUPER_INTERFACES)
                    .values(namespacesMap.get(type.getKey()), type.getValue().getLeft(),
                        type.getValue().getRight()));
            }
            return new MockResult(result.size(), result);
        }

        private MockResult createNamespaces() {
            Result<Record2<Long, String>> result =
                context.newResult(ModuleNames.MODULE_NAMES.ID, ModuleNames.MODULE_NAMES.NAME);
            for (var namespace : namespacesMap.entrySet()) {
                result.add(context
                    .newRecord(ModuleNames.MODULE_NAMES.ID, ModuleNames.MODULE_NAMES.NAME)
                    .values(namespace.getValue(), namespace.getKey()));
            }
            return new MockResult(result.size(), result);
        }

        private MockResult createTypeDictionary() {
            Result<Record2<String, Long>> result =
                context.newResult(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID);
            for (var node : typeDictionary.entrySet()) {
                result.add(context
                    .newRecord(Callables.CALLABLES.FASTEN_URI, Callables.CALLABLES.ID)
                    .values(node.getValue(), node.getKey()));
            }
            return new MockResult(result.size(), result);
        }

        private MockResult createTypeMap() {
            Result<Record2<Long, String>> result =
                context.newResult(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI);
            for (var node : typeMap.entrySet()) {
                result.add(context
                    .newRecord(Callables.CALLABLES.ID, Callables.CALLABLES.FASTEN_URI)
                    .values(node.getKey(), node.getValue()));
            }
            return new MockResult(result.size(), result);
        }
    }

    @Test
    public void mergeAllDepsTest() {
        merger = new CGMerger(Arrays.asList(imported, importer));

        final var cg = merger.mergeAllDeps();
        final var uris = merger.getAllUris();
        assertEquals(2, cg.edgeSet().size());
        assertEquals(4, uris.size());
        final var source = uris.inverse().get("fasten://mvn!Importer$0/merge" +
            ".simpleImport/Importer.sourceMethod()%2Fjava.lang%2FVoidType");
        final var target1 = uris.inverse().get("fasten://mvn!Imported$1/merge" +
            ".simpleImport/Imported.targetMethod()%2Fjava.lang%2FVoidType");
        final var target2 =
            uris.inverse().get("fasten://mvn!Imported$1/merge.simpleImport/Imported" +
                ".%3Cinit%3E()%2Fjava.lang%2FVoidType");

        assertEquals(Set.of(LongLongPair.of(source, target1), LongLongPair.of(source,
            target2)), convertToImmutablePairs(cg.edgeSet()));
    }

    @Test
    public void mergeAllDepsWithExternalsTest() {
        merger = new CGMerger(Arrays.asList(imported, importer), true);

        final var cg = merger.mergeAllDeps();
        final var uris = merger.getAllUris();
        print(cg, uris);
        assertEquals(4, cg.edgeSet().size());
        assertEquals(5, uris.size());
        final var importedInit =
            uris.inverse().get("fasten://mvn!Imported$1/merge.simpleImport/Imported" +
                ".%3Cinit%3E()%2Fjava.lang%2FVoidType");
        final var importedTarget = uris.inverse().get("fasten://mvn!Imported$1/merge" +
            ".simpleImport/Imported.targetMethod()%2Fjava.lang%2FVoidType");
        final var importerInit = uris.inverse().get("fasten://mvn!Importer$0/merge" +
            ".simpleImport/Importer.%3Cinit%3E()%2Fjava.lang%2FVoidType");
        final var importerSource = uris.inverse().get("fasten://mvn!Importer$0/merge" +
            ".simpleImport/Importer.sourceMethod()%2Fjava.lang%2FVoidType");
        final var objectInit = uris.inverse().get("/java.lang/Object.%3Cinit%3E()VoidType");

        assertEquals(-1L, objectInit);
        assertEquals(LongSet.of(objectInit), cg.externalNodes());

        assertEquals(
            Set.of(LongLongPair.of(importedInit, objectInit),
                LongLongPair.of(importerSource, importedInit),
                LongLongPair.of(importerInit, objectInit),
                LongLongPair.of(importerSource, importedTarget)),
            convertToImmutablePairs(cg.edgeSet()));
    }

    public static void print(DirectedGraph cg, BiMap<Long, String> uris) {
        for (final var edge : cg.edgeSet()) {
            final var opSource = uris.get(edge.firstLong());
            final var opTarget = uris.get(edge.secondLong());
            System.out.println(opSource + " -> " + opTarget);
        }
    }

    private Set<LongLongImmutablePair> convertToImmutablePairs(Set<LongLongPair> edgeSet) {
        Set<LongLongImmutablePair> result = new HashSet<>();
        for (LongLongPair edge : edgeSet) {
            result.add(LongLongImmutablePair.of(edge.leftLong(), edge.rightLong()));
        }
        return result;
    }

    @Disabled
    @Test
    public void souldNotGetIllegalArgumentExceptionWhileMerging()
        throws IOException, URISyntaxException {
        final var dir =
            new File(Objects.requireNonNull(Thread.currentThread().getContextClassLoader()
                .getResource("merge/LocalMergeException")).toURI().getPath());
        final List<PartialJavaCallGraph> depSet = new ArrayList<>();

        for (final var jsonFile : dir.listFiles()) {
            depSet.add(
                new PartialJavaCallGraph(new JSONObject(Files.readString(jsonFile.toPath()))));
        }

        merger = new CGMerger(depSet);
        merger.mergeWithCHA(depSet.get(0));
    }
}
