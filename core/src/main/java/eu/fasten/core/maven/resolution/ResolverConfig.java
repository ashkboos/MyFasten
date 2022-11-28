/*
 * Copyright 2022 Delft University of Technology
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
package eu.fasten.core.maven.resolution;

import static eu.fasten.core.maven.data.Scope.RUNTIME;
import static eu.fasten.core.maven.resolution.ResolverDepth.TRANSITIVE;
import static org.apache.commons.lang3.builder.ToStringStyle.MULTI_LINE_STYLE;

import java.util.Date;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import eu.fasten.core.maven.data.Scope;

public class ResolverConfig {

    public long resolveAt = new Date().getTime();
    public ResolverDepth depth = TRANSITIVE;
    public Scope scope = RUNTIME;

    /**
     * if false, include only direct dependencies
     */
    public boolean alwaysIncludeProvided;

    /**
     * if false, include only direct dependencies
     */
    public boolean alwaysIncludeOptional;

    public ResolverConfig at(long timestamp) {
        this.resolveAt = timestamp;
        return this;
    }

    public ResolverConfig depth(ResolverDepth depth) {
        this.depth = depth;
        return this;
    }

    public ResolverConfig scope(Scope scope) {
        this.scope = scope;
        return this;
    }

    /**
     * if false, include only direct dependencies
     */
    public ResolverConfig alwaysIncludeProvided(boolean alwaysIncludeProvided) {
        this.alwaysIncludeProvided = alwaysIncludeProvided;
        return this;
    }

    /**
     * if false, include only direct dependencies
     */
    public ResolverConfig alwaysIncludeOptional(boolean alwaysIncludeOptional) {
        this.alwaysIncludeOptional = alwaysIncludeOptional;
        return this;
    }

    public static ResolverConfig resolve() {
        return new ResolverConfig();
    }

    @Override
    public boolean equals(Object obj) {
        return EqualsBuilder.reflectionEquals(this, obj);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, MULTI_LINE_STYLE);
    }
}