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

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import eu.fasten.core.data.ClassHierarchy;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.DirectedGraph;
import eu.fasten.core.data.JavaNode;
import eu.fasten.core.data.JavaScope;
import eu.fasten.core.data.JavaType;
import eu.fasten.core.data.MergedDirectedGraph;
import eu.fasten.core.data.PartialJavaCallGraph;
import eu.fasten.core.data.callableindex.RocksDao;
import eu.fasten.core.data.callableindex.SourceCallSites;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongLongPair;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.json.JSONObject;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CGMerger {

    private static final Logger logger = LoggerFactory.getLogger(CGMerger.class);

    private final ClassHierarchy classHierarchy;
    private Map<String, String> toPruneSig2Type = new HashMap<>();

    private DSLContext dbContext;
    private RocksDao rocksDao;
    private Set<Long> dependencySet;

    private List<Pair<DirectedGraph, PartialJavaCallGraph>> ercgDependencySet;
    private BiMap<Long, String> allUris;

    private Map<String, Map<String, String>> externalUris;
    private long externalGlobalIds = 0;

    public BiMap<Long, String> getAllUris() {
        return this.allUris;
    }

    public ClassHierarchy getClassHierarchy() {
        return classHierarchy;
    }

    /**
     * Creates instance of callgraph merger.
     *
     * @param dependencySet all artifacts present in a resolution
     */
    public CGMerger(final List<PartialJavaCallGraph> dependencySet) {
        this(dependencySet, false);
    }

    public CGMerger(final List<PartialJavaCallGraph> dependencySet,
                    final Map<String, String> toPruneSig2Type) {
        this(dependencySet, false);
        this.toPruneSig2Type = toPruneSig2Type;
    }

    /**
     * Create instance of callgraph merger from package names.
     *
     * @param dependencySet coordinates of dependencies present in the resolution
     * @param dbContext     DSL context
     * @param rocksDao      rocks DAO
     */
    public CGMerger(final List<String> dependencySet,
                    final DSLContext dbContext, final RocksDao rocksDao) {
        this.dbContext = dbContext;
        this.rocksDao = rocksDao;
        this.dependencySet = getDependenciesIds(dependencySet, dbContext);
        this.classHierarchy = new ClassHierarchy(this.dependencySet, dbContext, rocksDao);
    }

    /**
     * Create instance of callgraph merger from package versions ids.
     *
     * @param dependencySet dependencies present in the resolution
     * @param dbContext     DSL context
     * @param rocksDao      rocks DAO
     */
    public CGMerger(final Set<Long> dependencySet,
                    final DSLContext dbContext, final RocksDao rocksDao) {
        this.dbContext = dbContext;
        this.rocksDao = rocksDao;
        this.dependencySet = dependencySet;
        this.classHierarchy = new ClassHierarchy(dependencySet, dbContext, rocksDao);
    }

    /**
     * Creates instance of callgraph merger.
     *
     * @param dependencySet all artifacts present in a resolution
     * @param withExternals true if unresolved external calls should be kept in the generated graph, they will be
     *                      assigned negative ids
     */
    public CGMerger(final List<PartialJavaCallGraph> dependencySet, boolean withExternals) {

        this.allUris = HashBiMap.create();
        if (withExternals) {
            this.externalUris = new HashMap<>();
        }
        this.ercgDependencySet = convertToDirectedGraphsAndSetAllUris(dependencySet);
        this.classHierarchy = new ClassHierarchy(dependencySet, this.allUris);
    }

    private List<Pair<DirectedGraph, PartialJavaCallGraph>> convertToDirectedGraphsAndSetAllUris(
        final List<PartialJavaCallGraph> dependencySet) {
        List<Pair<DirectedGraph, PartialJavaCallGraph>> depSet = new ArrayList<>();
        long offset = 0L;
        for (final var dep : dependencySet) {
            final var directedDep = ercgToDirectedGraph(dep, offset);
            offset = this.allUris.keySet().stream().max(Long::compareTo).orElse(0L) + 1;
            depSet.add(ImmutablePair.of(directedDep, dep));
        }
        return depSet;
    }

    private DirectedGraph ercgToDirectedGraph(final PartialJavaCallGraph ercg, long offset) {
        final var result = new MergedDirectedGraph();
        final var uris = ercg.mapOfFullURIStrings();
        final var internalNodes = getAllInternalNodes(ercg);

        for (Long node : internalNodes) {
            var uri = uris.get(node);

            if (!allUris.containsValue(uri)) {
                final var updatedNode = node + offset;
                this.allUris.put(updatedNode, uri);
                result.addVertex(updatedNode);
            }
        }

        // Index external URIs
        if (isWithExternals()) {
            for (Map.Entry<String, JavaType> entry : ercg.getClassHierarchy()
                .get(JavaScope.externalTypes).entrySet()) {
                Map<String, String> typeMap =
                    this.externalUris.computeIfAbsent(entry.getKey(), k -> new HashMap<>());
                for (JavaNode node : entry.getValue().getMethods().values()) {
                    typeMap.put(node.getSignature(), node.getUri().toString());
                }
            }
        }

        return result;
    }

    private LongSet getAllInternalNodes(PartialJavaCallGraph pcg) {
        LongSet nodes = new LongOpenHashSet();
        LongConsumer nodeAddingFunction = integer -> nodes.add(Long.valueOf(integer).longValue());
        pcg.getClassHierarchy().get(JavaScope.internalTypes)
            .forEach((key, value) -> value.getMethods().keySet().forEach(nodeAddingFunction));
        return nodes;
    }

    /**
     * @return true if unresolved external calls should be kept in the generated graph
     */
    public boolean isWithExternals() {
        return this.externalUris != null;
    }

    public DirectedGraph mergeWithCHA(final long id) {
        final var callGraphData = fetchCallGraphData(id, rocksDao);
        var metadata = rocksDao.getGraphMetadata(id, callGraphData);
        return mergeWithCHA(callGraphData, metadata);
    }

    public DirectedGraph mergeWithCHA(final String artifact) {
        return mergeWithCHA(getPackageVersionId(artifact));
    }

    public DirectedGraph mergeWithCHA(final PartialJavaCallGraph cg) {
        for (final var directedERCGPair : this.ercgDependencySet) {
            if (cg.uri.equals(directedERCGPair.getRight().uri)) {
                directedERCGPair.getRight().setSourceCallSites(this.allUris.inverse());
                return mergeWithCHA(directedERCGPair.getKey(),
                    directedERCGPair.getRight().sourceCallSites);
            }
        }
        logger.warn("This cg does not exist in the dependency set.");
        return new MergedDirectedGraph();
    }

    public BiMap<Long, String> getAllUrisFromDB(DirectedGraph dg) {
        Set<Long> gIDs = new HashSet<>();
        for (Long node : dg.nodes()) {
            if (node > 0) {
                gIDs.add(node);
            }
        }
        BiMap<Long, String> uris = HashBiMap.create();
        dbContext
            .select(Callables.CALLABLES.ID, Packages.PACKAGES.PACKAGE_NAME,
                PackageVersions.PACKAGE_VERSIONS.VERSION,
                Callables.CALLABLES.FASTEN_URI)
            .from(Callables.CALLABLES, Modules.MODULES, PackageVersions.PACKAGE_VERSIONS,
                Packages.PACKAGES)
            .where(Callables.CALLABLES.ID.in(gIDs))
            .and(Modules.MODULES.ID.eq(Callables.CALLABLES.MODULE_ID))
            .and(PackageVersions.PACKAGE_VERSIONS.ID.eq(Modules.MODULES.PACKAGE_VERSION_ID))
            .and(Packages.PACKAGES.ID.eq(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID))
            .fetch().forEach(record -> uris.put(record.component1(),
                "fasten://mvn!" + record.component2() + "$" + record.component3() +
                    record.component4()));

        return uris;
    }

    /**
     * Merges a call graph with its dependencies using CHA algorithm.
     *
     * @param callGraph DirectedGraph of the dependency to stitch
     * @param metadata  SourceCallSites of the dependency to stitch
     * @return merged call graph
     */

    public DirectedGraph mergeWithCHA(final DirectedGraph callGraph,
                                      final SourceCallSites metadata) {
        if (callGraph == null) {
            logger.error("Empty call graph data");
            return null;
        }
        if (metadata == null) {
            logger.error("Graph metadata is not available, cannot merge");
            return null;
        }

        var result = new MergedDirectedGraph();

        final Set<LongLongPair> edges = ConcurrentHashMap.newKeySet();

        metadata.sourceId2SourceInf.long2ObjectEntrySet().forEach(sourceNode -> {
            final var sourceId = getSourceId(sourceNode);
            final var sourceInf = sourceNode.getValue();

            for (var callSite : sourceInf.callSites) {
                if (!resolve(edges, sourceId, callSite, callGraph.isExternal(sourceId))) {
                    if (isWithExternals()) {
                        addExternal(result, edges, sourceId, callSite);
                    }
                }
            }
        });

        for (LongLongPair edge : edges) {
            addEdge(result, edge.firstLong(), edge.secondLong());
        }

        return result;
    }

    private long getSourceId(
        final Long2ObjectMap.Entry<SourceCallSites.SourceMethodInf> sourceNode) {
        final var id = allUris.inverse().get(sourceNode.getValue().sourceUri);
        if (id == null) {
            return sourceNode.getLongKey();
        }
        return id;
    }

    boolean resolve(Set<LongLongPair> edges,
                    final long sourceId, final SourceCallSites.CallSite callSite,
                    final boolean isCallback) {
        boolean resolved = false;
        final Set<String> emptySet = new HashSet<>();
        Map<String, Map<String, Long>> definedMethods = this.classHierarchy.getDefinedType2Sig2Id();
        Map<String, Set<String>> universalChildren = this.classHierarchy.getUniversalChildren();
        Map<String, String> toPruneSig2Type = this.toPruneSig2Type;

        final var targetSignature = callSite.targetSignature;

        for (final var receiverTypeUri : callSite.receiverTypes) {
            // prune if possible
            final var blackType = toPruneSig2Type.get(targetSignature);
            if (blackType != null){
                final var blackChildren = universalChildren.get(blackType);
                if (blackChildren != null) {
                    if (blackChildren.contains(receiverTypeUri)) {
                        continue;
                    }
                }
            }
            // merge
            var targets = new LongOpenHashSet();

            switch (callSite.invocationInstruction) {
                case VIRTUAL:
                case INTERFACE:
                    final var children = universalChildren.getOrDefault(receiverTypeUri, emptySet);
                    children.add(receiverTypeUri);
                    for (final var child : children) {
                        final var childMethods = definedMethods.get(child);
                        if (childMethods != null) {
                            final var target = childMethods.get(targetSignature);
                            if (target != null) {
                                targets.add(target.longValue());
                            }
                        }
                    }
                    break;

                default:
                    final var receiverMethods = definedMethods.get(receiverTypeUri);

                    if (receiverMethods != null) {
                        final var target = receiverMethods.get(targetSignature);
                        if (target != null) {
                            targets.add(target.longValue());
                        }
                    }
            }
            for (final var target : targets) {
                addCall(edges, sourceId, target, isCallback);
                resolved = true;
            }
        }
        return resolved;
    }

    /**
     * Add a non resolved edge to the {@link DirectedGraph}.
     */
    private synchronized void addExternal(final MergedDirectedGraph result,
                                          final Set<LongLongPair> edges, final long sourceId,
                                          SourceCallSites.CallSite callSite) {
        for (String type : callSite.receiverTypes) {
            // Find external node URI
            Map<String, String> typeMap = this.externalUris.get(type);
            if (typeMap != null) {
                String nodeURI = typeMap.get(callSite.targetSignature);

                if (nodeURI != null) {
                    // Find external node id
                    Long target = this.allUris.inverse().get(nodeURI);
                    if (target == null) {
                        // Allocate a global id to the external node
                        target = --this.externalGlobalIds;

                        // Add the external node to the graph if not already there
                        this.allUris.put(target, nodeURI);
                        result.addExternalNode(target);
                    }

                    edges.add(LongLongPair.of(sourceId, target));
                }
            }
        }
    }

    /**
     * Create fully merged for the entire dependency set.
     *
     * @return merged call graph
     */
    public DirectedGraph mergeAllDeps() {
        List<DirectedGraph> depGraphs = new ArrayList<>();
        if (this.dbContext == null) {
            for (final var dep : this.ercgDependencySet) {
                if (dep.getRight().sourceCallSites == null) {
                    dep.getRight().setSourceCallSites(this.allUris.inverse());
                }
                DirectedGraph merged = mergeWithCHA(dep.getKey(), dep.getRight().sourceCallSites);
                if (merged != null) {
                    depGraphs.add(merged);
                }
            }
        } else {
            for (final var dep : this.dependencySet) {
                var merged = mergeWithCHA(dep);
                if (merged != null) {
                    depGraphs.add(merged);
                }
            }
        }
        return augmentGraphs(depGraphs);
    }


    private void addEdge(final MergedDirectedGraph result,
                         final long source, final long target) {
        result.addVertex(source);
        result.addVertex(target);
        result.addEdge(source, target);
    }

    /**
     * Augment generated merged call graphs.
     *
     * @param depGraphs merged call graphs
     * @return augmented graph
     */
    private DirectedGraph augmentGraphs(final List<DirectedGraph> depGraphs) {
        var result = new MergedDirectedGraph();
        int numNode = 0;
        for (DirectedGraph depGraph : depGraphs) {
            numNode += depGraph.numNodes();
            for (LongLongPair longLongPair : depGraph.edgeSet()) {
                addNode(result, longLongPair.firstLong(),
                    depGraph.isExternal(longLongPair.firstLong()));
                addNode(result, longLongPair.secondLong(),
                    depGraph.isExternal(longLongPair.secondLong()));
                result.addEdge(longLongPair.firstLong(), longLongPair.secondLong());
            }
        }
        logger.info("Number of Augmented nodes: {} edges: {}", numNode, result.numArcs());

        return result;
    }

    private void addNode(MergedDirectedGraph result, long node, boolean external) {
        if (external) {
            result.addExternalNode(node);
        } else {
            result.addInternalNode(node);
        }
    }

    /**
     * Add a resolved edge to the {@link DirectedGraph}.
     *
     * @param source     source callable ID
     * @param target     target callable ID
     * @param isCallback true, if a given arc is a callback
     */
    private void addCall(final Set<LongLongPair> edges,
                         Long source, Long target, final boolean isCallback) {
        if (isCallback) {
            Long t = source;
            source = target;
            target = t;
        }

        edges.add(LongLongPair.of(source, target));
    }

    /**
     * Fetches metadata of the nodes of first arg from database.
     *
     * @param graph DirectedGraph to search for its callable's metadata in the database.
     * @return Map of callable ids and their corresponding metadata in the form of
     * JSONObject.
     */
    public Map<Long, JSONObject> getCallablesMetadata(final DirectedGraph graph) {
        final Map<Long, JSONObject> result = new HashMap<>();

        final var metadata = dbContext
            .select(Callables.CALLABLES.ID, Callables.CALLABLES.METADATA)
            .from(Callables.CALLABLES)
            .where(Callables.CALLABLES.ID.in(graph.nodes()))
            .fetch();
        for (final var callable : metadata) {
            result.put(callable.value1(), new JSONObject(callable.value2().data()));
        }
        return result;
    }

    /**
     * Retrieve a call graph from a graph database given a maven coordinate.
     *
     * @param rocksDao rocks DAO
     * @return call graph
     */
    private DirectedGraph fetchCallGraphData(final long artifactId, final RocksDao rocksDao) {
        DirectedGraph callGraphData = null;
        try {
            callGraphData = rocksDao.getGraphData(artifactId);
        } catch (RocksDBException e) {
            logger.error("Could not retrieve callgraph data from the graph database:", e);
        }
        return callGraphData;
    }

    /**
     * Get package version id for an artifact.
     *
     * @param artifact artifact in format groupId:artifactId:version
     * @return package version id
     */
    private long getPackageVersionId(final String artifact) {
        var packageName = artifact.split(":")[0] + ":" + artifact.split(":")[1];
        var version = artifact.split(":")[2];
        return Objects.requireNonNull(dbContext
                .select(PackageVersions.PACKAGE_VERSIONS.ID)
                .from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES)
                .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
                .where(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version))
                .and(Packages.PACKAGES.PACKAGE_NAME.eq(packageName))
                .and(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
                .fetchOne())
            .component1();
    }

    /**
     * Get dependencies IDs from a metadata database.
     *
     * @param dbContext DSL context
     * @return set of IDs of dependencies
     */
    Set<Long> getDependenciesIds(final List<String> dependencySet,
                                 final DSLContext dbContext) {
        var coordinates = new HashSet<>(dependencySet);

        Condition depCondition = null;

        for (var dependency : coordinates) {
            var packageName = dependency.split(":")[0] + ":" + dependency.split(":")[1];
            var version = dependency.split(":")[2];

            if (depCondition == null) {
                depCondition = Packages.PACKAGES.PACKAGE_NAME.eq(packageName)
                    .and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version));
            } else {
                depCondition = depCondition.or(Packages.PACKAGES.PACKAGE_NAME.eq(packageName)
                    .and(PackageVersions.PACKAGE_VERSIONS.VERSION.eq(version)));
            }
        }
        return dbContext
            .select(PackageVersions.PACKAGE_VERSIONS.ID)
            .from(PackageVersions.PACKAGE_VERSIONS).join(Packages.PACKAGES)
            .on(PackageVersions.PACKAGE_VERSIONS.PACKAGE_ID.eq(Packages.PACKAGES.ID))
            .where(depCondition)
            .and(Packages.PACKAGES.FORGE.eq(Constants.mvnForge))
            .fetch()
            .intoSet(PackageVersions.PACKAGE_VERSIONS.ID);
    }
}
