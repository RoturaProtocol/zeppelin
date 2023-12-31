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

package org.apache.zeppelin.flink;


import com.google.common.io.Files;
import org.apache.zeppelin.display.AngularObjectRegistry;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.apache.zeppelin.python.PythonInterpreterTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.TimeoutException;

import static org.mockito.Mockito.mock;


class PyFlinkInterpreterTest extends PythonInterpreterTest {

  private FlinkInterpreter flinkInnerInterpreter;
  private LazyOpenInterpreter flinkScalaInterpreter;
  private LazyOpenInterpreter streamSqlInterpreter;
  private LazyOpenInterpreter batchSqlInterpreter;


  @Override
  @BeforeEach
  public void setUp() throws InterpreterException {
    Properties properties = new Properties();
    properties.setProperty("zeppelin.pyflink.python", "python");
    properties.setProperty("zeppelin.flink.maxResult", "3");
    properties.setProperty("zeppelin.dep.localrepo", Files.createTempDir().getAbsolutePath());
    properties.setProperty("zeppelin.pyflink.useIPython", "false");
    properties.setProperty("zeppelin.flink.test", "true");
    properties.setProperty("zeppelin.python.gatewayserver_address", "127.0.0.1");
    properties.setProperty("local.number-taskmanager", "4");

    // create interpreter group
    intpGroup = new InterpreterGroup();
    intpGroup.put("session_1", new LinkedList<>());

    IPyFlinkInterpreterTest.angularObjectRegistry = new AngularObjectRegistry("flink", null);
    InterpreterContext context = getInterpreterContext();
    InterpreterContext.set(context);
    this.flinkInnerInterpreter = new FlinkInterpreter(properties);
    flinkScalaInterpreter = new LazyOpenInterpreter(flinkInnerInterpreter);
    intpGroup.get("session_1").add(flinkScalaInterpreter);
    flinkScalaInterpreter.setInterpreterGroup(intpGroup);

    LazyOpenInterpreter iPyFlinkInterpreter =
        new LazyOpenInterpreter(new IPyFlinkInterpreter(properties));
    intpGroup.get("session_1").add(iPyFlinkInterpreter);
    iPyFlinkInterpreter.setInterpreterGroup(intpGroup);

    interpreter = new LazyOpenInterpreter(new PyFlinkInterpreter(properties));
    intpGroup.get("session_1").add(interpreter);
    interpreter.setInterpreterGroup(intpGroup);

    streamSqlInterpreter = new LazyOpenInterpreter(new FlinkStreamSqlInterpreter(properties));
    batchSqlInterpreter = new LazyOpenInterpreter(new FlinkBatchSqlInterpreter(properties));
    intpGroup.get("session_1").add(streamSqlInterpreter);
    intpGroup.get("session_1").add(batchSqlInterpreter);
    streamSqlInterpreter.setInterpreterGroup(intpGroup);
    batchSqlInterpreter.setInterpreterGroup(intpGroup);

    interpreter.open();
  }

  @Override
  @AfterEach
  public void tearDown() {
    intpGroup.close();
    intpGroup = null;
    interpreter = null;
  }

  @Test
  void testBatchPyFlink() throws InterpreterException, IOException {
    if (!flinkInnerInterpreter.getFlinkVersion().isAfterFlink114()){
      IPyFlinkInterpreterTest.testBatchPyFlink(interpreter, flinkScalaInterpreter);
    }
  }

  @Test
  void testStreamIPyFlink() throws InterpreterException, IOException {
    if (!flinkInnerInterpreter.getFlinkVersion().isAfterFlink114()) {
      IPyFlinkInterpreterTest.testStreamPyFlink(interpreter, flinkScalaInterpreter);
    }
  }

  @Test
  void testSingleStreamTableApi() throws InterpreterException, IOException {
    IPyFlinkInterpreterTest.testSingleStreamTableApi(interpreter, flinkScalaInterpreter);
  }

  @Test
  void testUpdateStreamTableApi() throws InterpreterException, IOException {
    IPyFlinkInterpreterTest.testUpdateStreamTableApi(interpreter, flinkScalaInterpreter);
  }

  @Test
  void testAppendStreamTableApi() throws InterpreterException, IOException {
    IPyFlinkInterpreterTest.testAppendStreamTableApi(interpreter, flinkScalaInterpreter);
  }

  @Test
  void testCancelStreamSql()
      throws InterpreterException, IOException, TimeoutException, InterruptedException {
    IPyFlinkInterpreterTest.testCancelStreamSql(interpreter, flinkScalaInterpreter);
  }

  @Test
  @Disabled("(zjffdu) flaky test")
  void testResumeStreamSqlFromSavePoint()
      throws InterpreterException, IOException, TimeoutException, InterruptedException {
    IPyFlinkInterpreterTest.testResumeStreamSqlFromSavePoint(interpreter, flinkScalaInterpreter);
  }

  @Override
  protected InterpreterContext getInterpreterContext() {
    InterpreterContext context = InterpreterContext.builder()
            .setNoteId("noteId")
            .setParagraphId("paragraphId")
            .setInterpreterOut(new InterpreterOutput())
            .setAngularObjectRegistry(IPyFlinkInterpreterTest.angularObjectRegistry)
            .setIntpEventClient(mock(RemoteInterpreterEventClient.class))
            .build();
    InterpreterContext.set(context);
    return context;
  }
}
