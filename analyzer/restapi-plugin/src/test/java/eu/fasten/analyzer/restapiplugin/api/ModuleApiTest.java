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

package eu.fasten.analyzer.restapiplugin.api;

import eu.fasten.analyzer.restapiplugin.KnowledgeBaseConnector;
import eu.fasten.analyzer.restapiplugin.RestApplication;
import eu.fasten.core.data.Constants;
import eu.fasten.core.data.metadatadb.MetadataDao;
import eu.fasten.core.maven.data.PackageVersionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ModuleApiTest {

    private ModuleApi service;
    private MetadataDao kbDao;
    private final int offset = 0;
    private final int limit = Integer.parseInt(RestApplication.DEFAULT_PAGE_SIZE);

    @BeforeEach
    void setUp() {
        service = new ModuleApi();
        kbDao = Mockito.mock(MetadataDao.class);
        KnowledgeBaseConnector.kbDao = kbDao;
        KnowledgeBaseConnector.forge = Constants.mvnForge;

    }

    @Test
    void getPackageModulesPositiveTest() {
        var packageName = "group:artifact";
        var version = "version";
        var response = "modules";
        Mockito.when(kbDao.getPackageModules(packageName, version, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getPackageModules(packageName, version, offset, limit, null, null);
        assertEquals(expected, result);
        Mockito.verify(kbDao).getPackageModules(packageName, version, offset, limit);
    }

    @Test
    void getPackageModulesIngestionTest() {
        var packageName = "junit:junit";
        var version = "4.12";
        Mockito.when(kbDao.getPackageModules(packageName, version, offset, limit)).thenThrow(new PackageVersionNotFoundException("Error"));
        var result = service.getPackageModules(packageName, version, offset, limit, null, null);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());

        Mockito.verify(kbDao).getPackageModules(packageName, version, offset, limit);
    }

    @Test
    void getModuleMetadataPositiveTest() {
        var packageName = "group:artifact";
        var version = "version";
        var module = "module";
        var response = "module metadata";
        Mockito.when(kbDao.getModuleMetadata(packageName, version, module)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getModuleMetadata(packageName, version, module, null, null);
        assertEquals(expected, result);

        Mockito.verify(kbDao, Mockito.times(1)).getModuleMetadata(packageName, version, module);
    }

    @Test
    void getModuleMetadataNegativeTest() {
        var packageName = "group:artifact";
        var version = "version";
        var module = "module";
        Mockito.when(kbDao.getModuleMetadata(packageName, version, module)).thenReturn(null);
        var result = service.getModuleMetadata(packageName, version, module, null, null);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(1)).getModuleMetadata(packageName, version, module);
    }

    @Test
    void getModuleMetadataIngestionTest() {
        var packageName = "junit:junit";
        var version = "4.12";
        var module = "module";
        Mockito.when(kbDao.getModuleMetadata(packageName, version, module)).thenThrow(new PackageVersionNotFoundException("Error"));
        var result = service.getModuleMetadata(packageName, version, module, null, null);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());

        Mockito.verify(kbDao, Mockito.times(1)).getModuleMetadata(packageName, version, module);
    }
    @Test
    void getModuleFilesPositiveTest() {
        var packageName = "group:artifact";
        var version = "version";
        var module = "module";
        var response = "module files";
        Mockito.when(kbDao.getModuleFiles(packageName, version, module, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getModuleFiles(packageName, version, module, offset, limit, null, null);
        assertEquals(expected, result);
        Mockito.verify(kbDao).getModuleFiles(packageName, version, module, offset, limit);
    }

    @Test
    void getModuleFilesNegativeTest() {
        var packageName = "group:artifact";
        var version = "version";
        var module = "module";
        Mockito.when(kbDao.getModuleFiles(packageName, version, module, offset, limit)).thenReturn(null);
        var result = service.getModuleFiles(packageName, version, module, offset, limit, null, null);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        Mockito.verify(kbDao).getModuleFiles(packageName, version, module, offset, limit);
    }

    @Test
    void getModuleFilesIngestionTest() {
        var packageName = "junit:junit";
        var version = "4.12";
        var module = "module";
        Mockito.when(kbDao.getModuleFiles(packageName, version, module, offset, limit)).thenThrow(new PackageVersionNotFoundException("Error"));
        var result = service.getModuleFiles(packageName, version, module, offset, limit, null, null);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());

        Mockito.verify(kbDao).getModuleFiles(packageName, version, module, offset, limit);
    }

    @Test
    void getModuleCallablesPositiveTest() {
        var packageName = "group:artifact";
        var version = "version";
        var module = "module";
        var response = "module callables";
        Mockito.when(kbDao.getModuleCallables(Constants.mvnForge, packageName, version, module, offset, limit)).thenReturn(response);
        var expected = new ResponseEntity<>(response, HttpStatus.OK);
        var result = service.getModuleCallables(packageName, version, module, offset, limit, null, null);
        assertEquals(expected, result);
        Mockito.verify(kbDao).getModuleCallables(Constants.mvnForge, packageName, version, module, offset, limit);
    }

    @Test
    void getModuleCallablesNegativeTest() {
        var packageName = "group:artifact";
        var version = "version";
        var module = "module";
        Mockito.when(kbDao.getModuleCallables(Constants.mvnForge, packageName, version, module, offset, limit)).thenReturn(null);
        var result = service.getModuleCallables(packageName, version, module, offset, limit, null, null);
        assertEquals(HttpStatus.NOT_FOUND, result.getStatusCode());
        Mockito.verify(kbDao).getModuleCallables(Constants.mvnForge, packageName, version, module, offset, limit);
    }

    @Test
    void getModuleCallablesIngestionTest() {
        var packageName = "junit:junit";
        var version = "4.12";
        var module = "module";
        Mockito.when(kbDao.getModuleCallables(Constants.mvnForge, packageName, version, module, offset, limit)).thenThrow(new PackageVersionNotFoundException("Error"));
        var result = service.getModuleCallables(packageName, version, module, offset, limit, null, null);
        assertEquals(HttpStatus.CREATED, result.getStatusCode());

        Mockito.verify(kbDao).getModuleCallables(Constants.mvnForge, packageName, version, module, offset, limit);
    }

}
