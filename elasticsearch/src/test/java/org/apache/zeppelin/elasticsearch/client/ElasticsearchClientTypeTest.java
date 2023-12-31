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

package org.apache.zeppelin.elasticsearch.client;


import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class ElasticsearchClientTypeTest {

  @Test
  void it_should_return_http_when_reducing_on_http_types() {
    //GIVEN
    List<ElasticsearchClientType> httpTypes =
        new ArrayList<>(Arrays.asList(ElasticsearchClientType.HTTP, ElasticsearchClientType.HTTPS));
    //WHEN
    Boolean httpTypesReduced = httpTypes.stream()
        .map(ElasticsearchClientType::isHttp)
        .reduce(true, (ident, elasticsearchClientType) -> ident && elasticsearchClientType);
    //THEN
    assertTrue(httpTypesReduced);
  }
}
