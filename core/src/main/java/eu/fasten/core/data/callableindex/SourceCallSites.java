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

package eu.fasten.core.data.callableindex;

import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;
import eu.fasten.core.merge.CallGraphUtils;
import eu.fasten.core.utils.FastenUriUtils;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import org.apache.commons.lang.StringUtils;

/**
 * This class contains the metadata associated with the nodes of a call graph.
 * Such metadata is stored by the {@link RocksDao} class in a suitable column family of the RocksDB
 * database, and can be recovered after reading the graph using
 * {@link RocksDao#getGraphMetadata(long, eu.fasten.core.data.DirectedGraph)}, if needed.
 */

public class SourceCallSites {
    /**
     * This class represent the metadata associated with a node. The FASTEN Java URI is split into the
     * type part, and the signature part.
     *
     * <p>
     * Since this class is intended for internal use only, and all fields are public, final and
     * immutable, no getters/setters are provided.
     */
    public static final class SourceMethodInf {

        public final String sourceUri;
        /**
         * The list of receivers.
         */
        public final List<CallSite> callSites;

        public SourceMethodInf(String sourceUri,
                               List<CallSite> callSites) {
            this.sourceUri = sourceUri;
            this.callSites = callSites;
        }

        public SourceMethodInf(final String type, final String sourceSignature,
                               final List<CallSite> callSites) {
            this.sourceUri = type + "." + sourceSignature;
            this.callSites = callSites;
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final SourceMethodInf other = (SourceMethodInf) obj;
            if (!sourceUri.equals(other.sourceUri)) {
                return false;
            }
            return callSites.equals(other.callSites);
        }

        @Override
        public int hashCode() {
            return sourceUri.hashCode() ^ callSites.hashCode();
        }

        @Override
        public String toString() {
            return "[" + sourceUri + ", " + callSites + "]";
        }

        public String sourceType() {
            final var fastenURI = FastenURI.create(sourceUri);
            return fastenURI.getNamespace() + "/" +
                CallGraphUtils.decode(StringUtils.substringBefore(fastenURI.getEntity(),
                    "."));
        }

        public String sourceSignature() {
            return CallGraphUtils.decode(
                StringUtils.substringAfter(FastenURI.create(sourceUri).getEntity(), "."));
        }
    }

    /**
     * This class represent compactly a receiver record. The {@link InvocationInstruction} enum matches closely the
     * jOOQ-generated one in {@link eu.fasten.core.data.metadatadb.codegen.tables.records.CallSitesRecord}.
     *
     * <p>
     * Since this class is intended for internal use only, and all fields are public, final and
     * immutable, no getters/setters are provided.
     */
    public static final class CallSite {
        public enum InvocationInstruction {
            STATIC,
            DYNAMIC,
            VIRTUAL,
            INTERFACE,
            SPECIAL
        }

        /**
         * The line of this call.
         */
        public final int line;
        /**
         * The type of invocation for this call.
         */
        public final InvocationInstruction invocationInstruction;
        /**
         * The signature of this call.
         */
        public final String targetSignature;
        /**
         * Possible target types for this call.
         */
        public final List<String> receiverTypes;

        public CallSite(final int line, final InvocationInstruction invocationInstruction,
                        final String targetSignature, final List<String> receiverTypes) {
            this.line = line;
            this.invocationInstruction = invocationInstruction;
            this.targetSignature = formatIfNeeded(targetSignature);
            this.receiverTypes = receiverTypes;
        }

        private String formatIfNeeded(final String targetSignature) {
            String sig = targetSignature;
            if (targetSignature.startsWith("/")) {
                sig = CallGraphUtils.decode(StringUtils.substringAfter(
                    FastenJavaURI.create(targetSignature).decanonicalize()
                        .getEntity(), "."));
            }
            return sig;
        }

        @Override
        public String toString() {
            return "{line: " + line + ", type: " + invocationInstruction + ", receiverUris: " +
                receiverTypes + "}";
        }

        @Override
        public int hashCode() {
            return HashCommon.mix(
                line + invocationInstruction.ordinal() + receiverTypes.hashCode());
        }

        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final CallSite other = (CallSite) obj;
            if (line != other.line) {
                return false;
            }
            if (invocationInstruction != other.invocationInstruction) {
                return false;
            }
            return receiverTypes.equals(other.receiverTypes);
        }

    }

    /**
     * For each node, the associated metadata.
     */
    public Long2ObjectOpenHashMap<SourceMethodInf> sourceId2SourceInf;

    public SourceCallSites(final Long2ObjectOpenHashMap<SourceMethodInf> sourceId2SourceInf) {
        this.sourceId2SourceInf = sourceId2SourceInf;
    }

}
