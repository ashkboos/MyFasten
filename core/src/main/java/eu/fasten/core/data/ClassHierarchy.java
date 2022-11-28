/*
 * Copyright 2021 Delft University of Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.core.data;

import com.google.common.collect.BiMap;
import eu.fasten.core.data.callableindex.RocksDao;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.ModuleNames;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.codehaus.plexus.util.CollectionUtils;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.rocksdb.RocksDBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassHierarchy {
    public static final String OBJECT_TYPE = "/java.lang/Object";
    private static final Logger logger = LoggerFactory.getLogger(ClassHierarchy.class);

    private Map<String, Map<String, Long>> definedMethods;
    private Map<String, Set<String>> universalChildren;
    private Map<String, List<String>> universalParents;
    private Map<String, Map<String, Long>> abstractMethods;

    public Map<String, Map<String, Long>> getDefinedMethods() {
        return definedMethods;
    }

    public Map<String, Set<String>> getUniversalChildren() {
        return universalChildren;
    }

    public Map<String, List<String>> getUniversalParents() {
        return universalParents;
    }

    private Map<Long, String> namespaceMap;

    private RocksDao rocksDao;

    public ClassHierarchy(final List<PartialJavaCallGraph> dependencySet,
                          final BiMap<Long, String> allUris) {

        setUniversalParents(dependencySet);
        setUniversalChildren();
        setDefinedAndAbstractMethods(dependencySet, allUris);
        propagateInheritedMethodsToDefinedMethods();
    }

    public ClassHierarchy(final Set<Long> dependencySet, final DSLContext dbContext,
                          final RocksDao rocksDao) {
        this.rocksDao = rocksDao;
        final var universalCHA = createUniversalCHA(dependencySet, dbContext, rocksDao);
        this.universalChildren = new HashMap<>(universalCHA.getRight().size());
        universalCHA.getRight()
            .forEach((k, v) -> this.universalChildren.put(k, new HashSet<>(v)));
        this.universalParents = new HashMap<>(universalCHA.getLeft().size());
        universalCHA.getLeft().forEach((k, v) -> this.universalParents.put(k, new ArrayList<>(v)));
        this.definedMethods = createTypeDictionary(dependencySet);
        this.abstractMethods = new HashMap<>();
    }


    /**
     * Create a universal class hierarchy from all dependencies.
     *
     * @param dependenciesIds IDs of dependencies
     * @param dbContext       DSL context
     * @param rocksDao        rocks DAO
     * @return universal CHA
     */
    private Pair<Map<String, Set<String>>, Map<String, Set<String>>> createUniversalCHA(
        final Set<Long> dependenciesIds, final DSLContext dbContext, final RocksDao rocksDao) {
        var universalCHA = new DefaultDirectedGraph<String, DefaultEdge>(DefaultEdge.class);

        var callables = getCallables(dependenciesIds, rocksDao);

        var modulesIds = dbContext
            .select(Callables.CALLABLES.MODULE_ID)
            .from(Callables.CALLABLES)
            .where(Callables.CALLABLES.ID.in(callables))
            .fetch();

        var modules = dbContext
            .select(Modules.MODULES.MODULE_NAME_ID, Modules.MODULES.SUPER_CLASSES,
                Modules.MODULES.SUPER_INTERFACES)
            .from(Modules.MODULES)
            .where(Modules.MODULES.ID.in(modulesIds))
            .fetch();

        var namespaceIDs = new HashSet<>(modules.map(Record3::value1));
        modules.forEach(m -> namespaceIDs.addAll(Arrays.asList(m.value2())));
        modules.forEach(m -> namespaceIDs.addAll(Arrays.asList(m.value3())));
        var namespaceResults = dbContext
            .select(ModuleNames.MODULE_NAMES.ID, ModuleNames.MODULE_NAMES.NAME)
            .from(ModuleNames.MODULE_NAMES)
            .where(ModuleNames.MODULE_NAMES.ID.in(namespaceIDs))
            .fetch();
        this.namespaceMap = new HashMap<>(namespaceResults.size());
        namespaceResults.forEach(r -> namespaceMap.put(r.value1(), r.value2()));

        for (var callable : modules) {
            if (!universalCHA.containsVertex(namespaceMap.get(callable.value1()))) {
                universalCHA.addVertex(namespaceMap.get(callable.value1()));
            }

            try {
                var superClasses = Arrays.stream(callable.value2()).map(n -> namespaceMap.get(n))
                    .collect(Collectors.toList());
                addSuperTypes(universalCHA, namespaceMap.get(callable.value1()), superClasses);
            } catch (NullPointerException ignore) {
            }
            try {
                var superInterfaces = Arrays.stream(callable.value3()).map(n -> namespaceMap.get(n))
                    .collect(Collectors.toList());
                addSuperTypes(universalCHA, namespaceMap.get(callable.value1()), superInterfaces);
            } catch (NullPointerException ignore) {
            }
        }

        final Map<String, Set<String>> universalParents = new HashMap<>();
        final Map<String, Set<String>> universalChildren = new HashMap<>();
        for (final var type : universalCHA.vertexSet()) {

            final var children = new HashSet<>(Collections.singletonList(type));
            children.addAll(getAllChildren(universalCHA, type));
            universalChildren.put(type, children);

            final var parents = new HashSet<>(Collections.singletonList(type));
            parents.addAll(getAllParents(universalCHA, type));
            universalParents.put(type, parents);
        }

        return ImmutablePair.of(universalParents, universalChildren);
    }

    /**
     * Get callables from dependencies.
     *
     * @param dependenciesIds dependencies IDs
     * @param rocksDao        rocks DAO
     * @return list of callables
     */
    private List<Long> getCallables(final Set<Long> dependenciesIds, final RocksDao rocksDao) {
        var callables = new ArrayList<Long>();
        for (var id : dependenciesIds) {
            try {
                var cg = rocksDao.getGraphData(id);
                var nodes = cg.nodes();
                nodes.removeAll(cg.externalNodes());
                callables.addAll(nodes);
            } catch (RocksDBException | NullPointerException e) {
                logger.error("Couldn't retrieve a call graph with ID: {}", id);
            }
        }
        return callables;
    }

    private void setUniversalChildren() {
        this.universalChildren = new Object2ObjectOpenHashMap<>();
        for (final var curr : this.universalParents.entrySet()) {
            for (final var parent : curr.getValue()) {
                var children = this.universalChildren.get(parent);
                if (children == null) {
                    children = new ObjectOpenHashSet<>();
                }
                children.add(curr.getKey());
                this.universalChildren.put(parent, children);
            }
        }
    }

    private void setUniversalParents(
        final List<PartialJavaCallGraph> dependencySet) {
        final var type2Parents = createType2Parents(dependencySet);
        this.universalParents = new Object2ObjectOpenHashMap<>();
        for (final var type : type2Parents.keySet()) {
            final List<String> allParents = new ObjectArrayList<>();
            final Set<String> allInterfaces = new ObjectOpenHashSet<>();
            addAllSupers(type, type2Parents, allParents, allInterfaces);
            allParents.add(OBJECT_TYPE);
            allParents.addAll(allInterfaces);
            this.universalParents.put(type, allParents);
        }
    }

    private Map<String, Pair<String, Set<String>>> createType2Parents(
        final List<PartialJavaCallGraph> dependencies) {
        Map<String, Pair<String, Set<String>>> result = new Object2ObjectOpenHashMap<>();
        for (final var aPackage : dependencies) {
            for (final var type : aPackage.getClassHierarchy().get(JavaScope.internalTypes)
                .entrySet()) {
                final var superClasses = type.getValue().getSuperClasses();
                String superClass = OBJECT_TYPE;
                if (!superClasses.isEmpty()) {
                    superClass = superClasses.get(0).toString();
                }
                result.put(type.getKey(),
                    Pair.of(superClass,
                        type.getValue().getSuperInterfaces().stream().map(FastenURI::toString)
                            .collect(Collectors.toSet())));
            }
        }
        return result;
    }

    private void addAllSupers(final String curType,
                              final Map<String, Pair<String, Set<String>>> types2Parents,
                              List<String> AllParents,
                              Set<String> allSuperInterfaces) {
        final var parentClass = types2Parents.get(curType).getKey();
        if (!parentClass.equals(OBJECT_TYPE)) {
            AllParents.add(parentClass);
            if (types2Parents.containsKey(parentClass)) {
                addAllSupers(parentClass, types2Parents, AllParents, allSuperInterfaces);
            }
        }

        final var superInterfaces = types2Parents.get(curType).getValue();
        allSuperInterfaces.addAll(superInterfaces);
        for (final var superInterface : superInterfaces) {
            if (types2Parents.containsKey(superInterface)) {
                addAllSupers(superInterface, types2Parents, AllParents, allSuperInterfaces);
            }
        }
    }

    /**
     * Add super classes and interfaces to the universal CHA.
     *
     * @param result  universal CHA graph
     * @param child   source type
     * @param parents list of target target types
     */
    private void addSuperTypes(final DefaultDirectedGraph<String, DefaultEdge> result,
                               final String child,
                               final List<String> parents) {
        for (final var parent : parents) {
            if (!result.containsVertex(parent)) {
                result.addVertex(parent);
            }
            if (!result.containsEdge(child, parent)) {
                result.addEdge(child, parent);
            }
        }
    }

    /**
     * Get all parents of a given type.
     *
     * @param graph universal CHA
     * @param type  type uri
     * @return list of types parents
     */
    private List<String> getAllParents(final DefaultDirectedGraph<String, DefaultEdge> graph,
                                       final String type) {
        final var directParents = Graphs.successorListOf(graph, type);
        final List<String> result = new ArrayList<>();
        for (final var parent : directParents) {
            if (parent.equals(OBJECT_TYPE) || result.contains(parent)) {
                continue;
            }
            result.add(parent);
            final var ancestors = getAllParents(graph, parent);
            for (final var ancestor : ancestors) {
                if (result.contains(ancestor)) {
                    continue;
                }
                result.add(ancestor);
            }
        }
        return result;
    }

    /**
     * Get all children of a given type.
     *
     * @param graph universal CHA
     * @param type  type uri
     * @return list of types children
     */
    private Set<String> getAllChildren(final DefaultDirectedGraph<String, DefaultEdge> graph,
                                       final String type) {
        final var directChildren = Graphs.predecessorListOf(graph, type);
        final Set<String> result = new HashSet<>();
        for (final var child : directChildren) {
            if (result.contains(child)) {
                continue;
            }
            result.add(child);
            final var grandChildren = getAllChildren(graph, child);
            for (final var grandChild : grandChildren) {
                if (result.contains(grandChild)) {
                    continue;
                }
                result.add(grandChild);
            }
        }
        return result;
    }

    /**
     * Create a mapping from types and method signatures to callable IDs.
     *
     * @return a type dictionary
     */
    private Map<String, Map<String, Long>> createTypeDictionary(final Set<Long> dependencySet) {
        final long startTime = System.currentTimeMillis();
        var result = new HashMap<String, Map<String, Long>>();
        int noCGCounter = 0, noMetadaCounter = 0;
        for (Long dependencyId : dependencySet) {
            var cg = getGraphData(dependencyId);
            if (cg == null) {
                noCGCounter++;
                continue;
            }
            var metadata = rocksDao.getGraphMetadata(dependencyId, cg);
            if (metadata == null) {
                noMetadaCounter++;
                continue;
            }

            final var nodesData = metadata.sourceId2SourceInf;
            for (final var nodeId : nodesData.keySet()) {
                final var nodeData = nodesData.get(nodeId.longValue());
                final var typeUri = nodeData.sourceType();
                final var signaturesMap = result.getOrDefault(typeUri, new HashMap<>());
                final var signature = nodeData.sourceSignature();
                signaturesMap.putIfAbsent(signature, nodeId);
                result.put(typeUri, signaturesMap);
            }
        }
        logger.info("For {} dependencies failed to retrieve {} graph data and {} metadata " +
            "from rocks db.", dependencySet.size(), noCGCounter, noMetadaCounter);

        logger.info("Created the type dictionary with {} types in {} seconds", result.size(),
            new DecimalFormat("#0.000")
                .format((System.currentTimeMillis() - startTime) / 1000d));

        return result;
    }

    private DirectedGraph getGraphData(Long dependencyId) {
        DirectedGraph cg;
        try {
            cg = rocksDao.getGraphData(dependencyId);
        } catch (RocksDBException e) {
            throw new RuntimeException("An exception occurred retrieving CGs from rocks DB", e);
        }
        return cg;
    }

    private void propagateInheritedMethodsToDefinedMethods() {
        for (final var cur : this.definedMethods.entrySet()) {
            final var curUri = cur.getKey();
            for (final var curMethodSig2Id : this.definedMethods.get(curUri).entrySet()) {
                final var curMethodSig = curMethodSig2Id.getKey();
                final var curChildren = this.universalChildren.get(curUri);
                if (curChildren == null) {
                    continue;
                }
                for (final var childUri : curChildren) {
                    final var childMethodSig2Id =
                        this.definedMethods.getOrDefault(childUri, new HashMap<>());
                    final var idInChild = childMethodSig2Id.get(curMethodSig);
                    if (idInChild == null) {
                        final var childParents = universalParents.get(childUri);
                        if (childParents == null) {
                            continue;
                        }
                        for (final var childsParent : childParents) {
                            final var childsParentMethods =
                                this.definedMethods.getOrDefault(childsParent, new HashMap<>());
                            final var id = childsParentMethods.get(curMethodSig);
                            if (id != null) {
                                childMethodSig2Id.put(curMethodSig, id);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void setDefinedAndAbstractMethods(final List<PartialJavaCallGraph> dependencySet,
                                              final BiMap<Long, String> globalUris) {
        this.definedMethods = new Object2ObjectOpenHashMap<>();
        this.abstractMethods = new Object2ObjectOpenHashMap<>();
        for (final var rcg : dependencySet) {
            final var localUris = rcg.mapOfFullURIStrings();
            for (final var entry : rcg.getClassHierarchy().get(JavaScope.internalTypes)
                .entrySet()) {
                final var typeUri = entry.getKey();
                final var javaType = entry.getValue();
                final var definedMethods = javaType.getDefinedMethods().values();
                final var abstractMethods =
                    CollectionUtils.subtract(javaType.getMethods().values(), definedMethods);

                for (final var method1 : definedMethods) {
                    final var localId = javaType.getMethodKey(method1);
                    final var id = globalUris.inverse().get(localUris.get(localId));
                    putMethods(this.definedMethods, id, typeUri, method1);
                }
                for (final var method : abstractMethods) {
                    final var localId = javaType.getMethodKey(method);
                    final var id = globalUris.inverse().get(localUris.get(localId));
                    putMethods(this.abstractMethods, id, typeUri, method);
                }
            }
        }
    }

    private void putMethods(Map<String, Map<String, Long>> result,
                            final long id, final String typeUri, final JavaNode node) {
        final var oldType = result.getOrDefault(typeUri, new Object2ObjectOpenHashMap<>());
        final var oldNode = oldType.get(node.getSignature());
        if (oldNode == null) {
            oldType.put(node.getSignature(), id);
            result.put(typeUri, oldType);
        }
    }

    public Map<String, Map<String, Long>> getAbstractMethods() {
        return abstractMethods;
    }
}
