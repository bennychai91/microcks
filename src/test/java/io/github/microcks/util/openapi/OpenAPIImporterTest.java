/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.util.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.microcks.domain.*;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This is a test case for class OpenAPIImporter.
 * @author laurent
 */
public class OpenAPIImporterTest {

   @Test
   public void testSimpleOpenAPIImportYAML() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi.yaml");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      importAndAssertOnSimpleOpenAPI(importer);
   }

   @Test
   public void testSimpleOpenAPIImportJSON() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi.json");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      importAndAssertOnSimpleOpenAPI(importer);
   }

   @Test
   public void testOpenAPIImportYAMLWithHeaders() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi-headers.yaml");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("OpenAPI Car API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.OPEN_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operation and input/output have been found.
      assertEquals(1, service.getOperations().size());

      Operation operation = service.getOperations().get(0);
      assertEquals("GET /owner/{owner}/car", operation.getName());
      assertEquals("GET", operation.getMethod());
      assertEquals(DispatchStyles.URI_ELEMENTS, operation.getDispatcher());
      assertEquals("owner ?? page && limit && x-user-id", operation.getDispatcherRules());

      // Check that messages have been correctly found.
      Map<Request, Response> messages = null;
      try {
         messages = importer.getMessageDefinitions(service, operation);
      } catch (Exception e) {
         fail("No exception should be thrown when importing message definitions.");
      }
      assertEquals(1, messages.size());
      assertEquals(1, operation.getResourcePaths().size());
      assertEquals("/owner/laurent/car", operation.getResourcePaths().get(0));

      for (Map.Entry<Request, Response> entry : messages.entrySet()) {
         Request request = entry.getKey();
         Response response = entry.getValue();
         assertNotNull(request);
         assertNotNull(response);
         assertEquals("laurent_cars", request.getName());
         assertEquals("laurent_cars", response.getName());
         assertEquals("/owner=laurent?limit=20?page=0", response.getDispatchCriteria());
         assertEquals("200", response.getStatus());
         assertEquals("application/json", response.getMediaType());
         assertNotNull(response.getContent());

         // Check headers now.
         assertEquals(2, request.getHeaders().size());
         Iterator<Header> headers = request.getHeaders().iterator();
         while (headers.hasNext()) {
            Header header = headers.next();
            if ("x-user-id".equals(header.getName())) {
               assertEquals(1, header.getValues().size());
               assertEquals("poiuytrezamlkjhgfdsq", header.getValues().iterator().next());
            } else if ("Accept".equals(header.getName())) {
               assertEquals(1, header.getValues().size());
               assertEquals("application/json", header.getValues().iterator().next());
            } else {
               fail("Unexpected header name in request");
            }
         }

         assertEquals(1, response.getHeaders().size());
         Header header = response.getHeaders().iterator().next();
         assertEquals("x-result-count", header.getName());
         assertEquals(1, header.getValues().size());
         assertEquals("2", header.getValues().iterator().next());
      }
   }

   @Test
   public void testOpenAPIJsonPointer() {
      try {
         ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
         byte[] bytes = Files.readAllBytes(Paths.get("target/test-classes/io/github/microcks/util/openapi/cars-openapi.yaml"));
         JsonNode openapiSpec = mapper.readTree(bytes);

         String verb = "get";
         String path = "/owner/{owner}/car";

         String pointer = "/paths/" + path.replace("/", "~1") + "/" + verb
               + "/responses/200/content/" + "application/json".replace("/", "~1");

         JsonNode responseNode = openapiSpec.at(pointer);
         assertNotNull(responseNode);
         assertFalse(responseNode.isMissingNode());
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
   }

   @Test
   public void testCompleteOpenAPIImportYAML() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi-complete.yaml");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("OpenAPI Car API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.1.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.OPEN_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(4, service.getOperations().size());
      for (Operation operation : service.getOperations()) {

         if ("GET /owner/{owner}/car".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_ELEMENTS, operation.getDispatcher());
            assertEquals("owner ?? page && limit", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            Map<Request, Response> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, messages.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertEquals("/owner/laurent/car", operation.getResourcePaths().get(0));

            for (Map.Entry<Request, Response> entry : messages.entrySet()) {
               Request request = entry.getKey();
               Response response = entry.getValue();
               assertNotNull(request);
               assertNotNull(response);
               assertEquals("laurent_cars", request.getName());
               assertEquals("laurent_cars", response.getName());
               assertEquals("/owner=laurent?limit=20?page=0", response.getDispatchCriteria());
               assertEquals("200", response.getStatus());
               assertEquals("application/json", response.getMediaType());
               assertNotNull(response.getContent());
            }
         }
         else if ("POST /owner/{owner}/car".equals(operation.getName())) {
            assertEquals("POST", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            Map<Request, Response> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, messages.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertEquals("/owner/laurent/car", operation.getResourcePaths().get(0));

            for (Map.Entry<Request, Response> entry : messages.entrySet()) {
               Request request = entry.getKey();
               Response response = entry.getValue();
               assertNotNull(request);
               assertNotNull(response);
               assertEquals("laurent_307", request.getName());
               assertEquals("laurent_307", response.getName());
               assertEquals("/owner=laurent", response.getDispatchCriteria());
               assertEquals("201", response.getStatus());
               assertEquals("application/json", response.getMediaType());
               assertNotNull(response.getContent());
            }
         }
         else if ("GET /owner/{owner}/car/{car}/passenger".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner && car", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            Map<Request, Response> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, messages.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertEquals("/owner/laurent/car/307/passenger", operation.getResourcePaths().get(0));

            for (Map.Entry<Request, Response> entry : messages.entrySet()) {
               Request request = entry.getKey();
               Response response = entry.getValue();
               assertNotNull(request);
               assertNotNull(response);
               assertEquals("laurent_307_passengers", request.getName());
               assertEquals("laurent_307_passengers", response.getName());
               assertEquals("/car=307/owner=laurent", response.getDispatchCriteria());
               assertEquals("200", response.getStatus());
               assertEquals("application/json", response.getMediaType());
               assertNotNull(response.getContent());
            }
         }
         else if ("POST /owner/{owner}/car/{car}/passenger".equals(operation.getName())) {
            assertEquals("POST", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner && car", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            Map<Request, Response> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(0, messages.size());
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }


   private void importAndAssertOnSimpleOpenAPI(OpenAPIImporter importer) {
      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("OpenAPI Car API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.OPEN_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(3, service.getOperations().size());
      for (Operation operation : service.getOperations()) {

         if ("GET /owner/{owner}/car".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_ELEMENTS, operation.getDispatcher());
            assertEquals("owner ?? page && limit", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            Map<Request, Response> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, messages.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertEquals("/owner/laurent/car", operation.getResourcePaths().get(0));

            for (Map.Entry<Request, Response> entry : messages.entrySet()) {
               Request request = entry.getKey();
               Response response = entry.getValue();
               assertNotNull(request);
               assertNotNull(response);
               assertEquals("laurent_cars", request.getName());
               assertEquals("laurent_cars", response.getName());
               assertEquals("/owner=laurent?limit=20?page=0", response.getDispatchCriteria());
               assertEquals("200", response.getStatus());
               assertEquals("application/json", response.getMediaType());
               assertNotNull(response.getContent());
            }
         }
         else if ("POST /owner/{owner}/car".equals(operation.getName())) {
            assertEquals("POST", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            Map<Request, Response> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, messages.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertEquals("/owner/laurent/car", operation.getResourcePaths().get(0));

            for (Map.Entry<Request, Response> entry : messages.entrySet()) {
               Request request = entry.getKey();
               Response response = entry.getValue();
               assertNotNull(request);
               assertNotNull(response);
               assertEquals("laurent_307", request.getName());
               assertEquals("laurent_307", response.getName());
               assertEquals("/owner=laurent", response.getDispatchCriteria());
               assertEquals("201", response.getStatus());
               assertEquals("application/json", response.getMediaType());
               assertNotNull(response.getContent());
            }
         }
         else if ("POST /owner/{owner}/car/{car}/passenger".equals(operation.getName())) {
            assertEquals("POST", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner && car", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            Map<Request, Response> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(0, messages.size());
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }
}
