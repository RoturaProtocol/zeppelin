/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.sparql;

import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.server.DataAccessPointRegistry;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Properties;

import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResult.Code;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SparqlJenaEngineTest {
  private static int port;

  private static FusekiServer server;

  private static final String ENGINE = "jena";
  private static final String DATASET = "/dataset";
  private static final String HOST = "http://localhost";

  private static Properties properties;

  private static final String DATA_FILE = "data.ttl";

  @BeforeAll
  public static void setUp() {
    port = Fuseki.choosePort();

    Model model = ModelFactory.createDefaultModel();
    model.read(DATA_FILE);
    Dataset ds = DatasetFactory.create(model);

    server = FusekiServer
      .create()
      .port(port)
      .add(DATASET, ds)
      .build();
    DataAccessPointRegistry registry = server.getDataAccessPointRegistry();
    assertTrue(registry.isRegistered(DATASET));
    assertEquals(1, registry.size());
    server.start();
  }

  @AfterAll
  public static void tearDown() {
    if (server != null) {
      server.stop();
    }
  }

  @BeforeEach
  public void setUpProperties() {
    properties = new Properties();
    properties.put(SparqlInterpreter.SPARQL_ENGINE_TYPE, ENGINE);
    properties.put(SparqlInterpreter.SPARQL_SERVICE_ENDPOINT, HOST + ":" + port + DATASET);
  }

  @Test
  void testWrongQuery() {
    SparqlInterpreter interpreter = new SparqlInterpreter(properties);
    interpreter.open();

    final InterpreterResult result = interpreter.interpret("SELECT * WHER {", null);
    assertEquals(Code.ERROR, result.code());
  }

  @Test
  void testSuccessfulRawQuery() {
    properties.put(SparqlInterpreter.SPARQL_REPLACE_URIS, "false");
    properties.put(SparqlInterpreter.SPARQL_REMOVE_DATATYPES, "false");
    SparqlInterpreter interpreter = new SparqlInterpreter(properties);
    interpreter.open();

    final InterpreterResult result = interpreter.interpret(
        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>" +
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>" +
        "PREFIX foaf: <http://xmlns.com/foaf/0.1/>" +
        "PREFIX rel: <http://www.perceive.net/schemas/relationship/>" +
        "SELECT ?subject ?predicate ?object WHERE { ?subject ?predicate ?object }", null);
    assertEquals(Code.SUCCESS, result.code());

    final String expected = "?subject\t?predicate\t?object\n" +
        "<http://example.org/#spiderman>\t<http://xmlns.com/foaf/0.1/name>" +
        "\t\"Человек-паук\"@ru\n<http://example.org/#spiderman>\t" +
        "<http://xmlns.com/foaf/0.1/name>\t\"Spiderman\"\n" +
        "<http://example.org/#spiderman>\t<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>" +
        "\t<http://xmlns.com/foaf/0.1/Person>\n<http://example.org/#spiderman>\t" +
        "<http://www.perceive.net/schemas/relationship/enemyOf>\t" +
        "<http://example.org/#green-goblin>\n<http://example.org/#spiderman>\t" +
        "<http://example.org/stats#born>\t\"1962-10-15T14:00.00\"^^" +
        "<http://www.w3.org/2001/XMLSchema#dateTime>\n<http://example.org/#green-goblin>\t" +
        "<http://xmlns.com/foaf/0.1/name>\t\"Green Goblin\"\n" +
        "<http://example.org/#green-goblin>\t" +
        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>" +
        "\t<http://xmlns.com/foaf/0.1/Person>\n<http://example.org/#green-goblin>\t" +
        "<http://www.perceive.net/schemas/relationship/enemyOf>\t<http://example.org/#spiderman>\n";
    assertEquals(expected, result.message().get(0).getData());
  }

  @Test
  void testSuccessfulReplaceRemoveQuery() {
    properties.put(SparqlInterpreter.SPARQL_REPLACE_URIS, "true");
    properties.put(SparqlInterpreter.SPARQL_REMOVE_DATATYPES, "true");
    SparqlInterpreter interpreter = new SparqlInterpreter(properties);
    interpreter.open();

    final InterpreterResult result = interpreter.interpret(
        "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>" +
        "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>" +
        "PREFIX foaf: <http://xmlns.com/foaf/0.1/>" +
        "PREFIX rel: <http://www.perceive.net/schemas/relationship/>" +
        "SELECT ?subject ?predicate ?object WHERE { ?subject ?predicate ?object }", null);
    assertEquals(Code.SUCCESS, result.code());

    final String expected = "?subject\t?predicate\t?object\n" +
        "<http://example.org/#spiderman>\t<foaf:name>" +
        "\t\"Человек-паук\"@ru\n<http://example.org/#spiderman>\t" +
        "<foaf:name>\t\"Spiderman\"\n" +
        "<http://example.org/#spiderman>\t<rdf:type>" +
        "\t<foaf:Person>\n<http://example.org/#spiderman>\t" +
        "<rel:enemyOf>\t" +
        "<http://example.org/#green-goblin>\n<http://example.org/#spiderman>\t" +
        "<http://example.org/stats#born>\t1962-10-15T14:00.00\n" +
        "<http://example.org/#green-goblin>\t<foaf:name>\t\"Green Goblin\"\n" +
        "<http://example.org/#green-goblin>\t" +
        "<rdf:type>" +
        "\t<foaf:Person>\n<http://example.org/#green-goblin>\t" +
        "<rel:enemyOf>\t<http://example.org/#spiderman>\n";

    assertEquals(expected, result.message().get(0).getData());
  }

  @Test
  void testRemoteEndpoint() {
    properties.put(SparqlInterpreter.SPARQL_SERVICE_ENDPOINT, "http://dbpedia.org/sparql");
    SparqlInterpreter interpreter = new SparqlInterpreter(properties);
    interpreter.open();

    String query = "PREFIX rdfs:<http://www.w3.org/2000/01/rdf-schema#>" +
            "\nSELECT * WHERE {?athlete rdfs:label " +
            "\"Cristiano Ronaldo\"@en}  order by ?athlete limit 1";
    final InterpreterResult result = interpreter.interpret(query, null);
    assertEquals(Code.SUCCESS, result.code());

    final String expected =
            "?athlete\n<http://dbpedia.org/resource/Category:Cristiano_Ronaldo>\n";
    assertEquals(expected, result.message().get(0).getData());
  }

  @Test
  void testEndpointMalformed() {
    properties.put(SparqlInterpreter.SPARQL_SERVICE_ENDPOINT, "tsohlacol");
    SparqlInterpreter interpreter = new SparqlInterpreter(properties);
    interpreter.open();

    final InterpreterResult result = interpreter.interpret(
        "SELECT ?subject ?predicate ?object WHERE { ?subject ?predicate ?object }", null);
    assertEquals(Code.ERROR, result.code());
  }

  @Test
  void testEndpointNotFound() {
    properties.put(SparqlInterpreter.SPARQL_SERVICE_ENDPOINT, "http://tsohlacol/");
    SparqlInterpreter interpreter = new SparqlInterpreter(properties);
    interpreter.open();

    final InterpreterResult result = interpreter.interpret(
        "SELECT ?subject ?predicate ?object WHERE { ?subject ?predicate ?object }", null);
    assertEquals(Code.ERROR, result.code());
  }
}
