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
package org.apache.zeppelin.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit test for ResourceSet
 */
class ResourceSetTest {

  @Test
  void testFilterByName() {
    ResourceSet set = new ResourceSet();

    set.add(new Resource(null, new ResourceId("poo1", "resource1"), "value1"));
    set.add(new Resource(null, new ResourceId("poo1", "resource2"), new Integer(2)));
    assertEquals(2, set.filterByNameRegex(".*").size());
    assertEquals(1, set.filterByNameRegex("resource1").size());
    assertEquals(1, set.filterByNameRegex("resource2").size());
    assertEquals(0, set.filterByNameRegex("res").size());
    assertEquals(2, set.filterByNameRegex("res.*").size());
  }

  @Test
  void testFilterByClassName() {
    ResourceSet set = new ResourceSet();

    set.add(new Resource(null, new ResourceId("poo1", "resource1"), "value1"));
    set.add(new Resource(null, new ResourceId("poo1", "resource2"), new Integer(2)));

    assertEquals(1, set.filterByClassnameRegex(".*String").size());
    assertEquals(1, set.filterByClassnameRegex(String.class.getName()).size());
    assertEquals(1, set.filterByClassnameRegex(".*Integer").size());
    assertEquals(1, set.filterByClassnameRegex(Integer.class.getName()).size());
  }
}
