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
package org.apache.zeppelin.conf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.format.DateTimeParseException;

import org.junit.jupiter.api.Test;

class ZeppelinConfigurationTest {

  @Test
  void testTimeUnitToMill() {
    assertEquals(10L, ZeppelinConfiguration.timeUnitToMill("10ms"));
    assertEquals(2000L, ZeppelinConfiguration.timeUnitToMill("2s"));
    assertEquals(60000L, ZeppelinConfiguration.timeUnitToMill("1m"));
    assertEquals(3600000L, ZeppelinConfiguration.timeUnitToMill("1h"));
  }

  @Test
  void testTimeUnitToMill_WithoutUnit_1() {
    assertThrows(DateTimeParseException.class, () -> {
      ZeppelinConfiguration.timeUnitToMill("60000");
    });
  }

  @Test
  void testTimeUnitToMill_WithoutUnit_2() {
    assertThrows(DateTimeParseException.class, () -> {
      ZeppelinConfiguration.timeUnitToMill("0");
    });
  }
}
