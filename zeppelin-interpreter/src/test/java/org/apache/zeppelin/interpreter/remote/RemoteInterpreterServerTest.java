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

package org.apache.zeppelin.interpreter.remote;

import org.apache.thrift.TException;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterContext;
import org.apache.zeppelin.interpreter.thrift.RemoteInterpreterResult;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RemoteInterpreterServerTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteInterpreterServerTest.class);

  @Test
  void testStartStop() throws Exception {
    RemoteInterpreterServer server = new RemoteInterpreterServer("localhost",
        RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces(), ":", "groupId", true);

    startRemoteInterpreterServer(server, 10 * 1000);
    stopRemoteInterpreterServer(server, 10 * 10000);
  }

  @Test
  void testStartStopWithQueuedEvents() throws Exception {
    RemoteInterpreterServer server = new RemoteInterpreterServer("localhost",
        RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces(), ":", "groupId", true);
    server.intpEventClient = mock(RemoteInterpreterEventClient.class);
    startRemoteInterpreterServer(server, 10 * 1000);

    server.intpEventClient.onAppStatusUpdate("", "", "", "");
    stopRemoteInterpreterServer(server, 10 * 10000);
  }

  private void startRemoteInterpreterServer(RemoteInterpreterServer server, int timeout)
          throws InterruptedException, TException {
    assertEquals(false, server.isRunning());
    server.start();
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < timeout) {
      if (server.isRunning()) {
        break;
      }
      Thread.sleep(200);
    }
    assertEquals(true, server.isRunning());
    assertEquals(true, RemoteInterpreterUtils.checkIfRemoteEndpointAccessible("localhost",
        server.getPort()));

    server.init(new HashMap<>());
    assertNotNull(server.getConf());
    assertNotNull(server.getLifecycleManager());
  }

  private void stopRemoteInterpreterServer(RemoteInterpreterServer server, int timeout)
      throws TException, InterruptedException {
    assertEquals(true, server.isRunning());
    server.shutdown();
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < timeout) {
      if (!server.isRunning()) {
        break;
      }
      Thread.sleep(200);
    }
    assertEquals(false, server.isRunning());
    assertEquals(false, RemoteInterpreterUtils.checkIfRemoteEndpointAccessible("localhost",
        server.getPort()));
  }

  @Test
  void testInterpreter() throws Exception {
    final RemoteInterpreterServer server = new RemoteInterpreterServer("localhost",
        RemoteInterpreterUtils.findRandomAvailablePortOnAllLocalInterfaces(), ":", "groupId", true);
    server.init(new HashMap<>());
    server.intpEventClient = mock(RemoteInterpreterEventClient.class);

    Map<String, String> intpProperties = new HashMap<>();
    intpProperties.put("property_1", "value_1");
    intpProperties.put("zeppelin.interpreter.localRepo", "/tmp");

    // create Test1Interpreter in session_1
    server.createInterpreter("group_1", "session_1", Test1Interpreter.class.getName(),
        intpProperties, "user_1");
    Test1Interpreter interpreter1 = (Test1Interpreter)
        ((LazyOpenInterpreter) server.getInterpreterGroup().get("session_1").get(0))
            .getInnerInterpreter();
    assertEquals(1, server.getInterpreterGroup().getSessionNum());
    assertEquals(1, server.getInterpreterGroup().get("session_1").size());
    assertEquals(2, interpreter1.getProperties().size());
    assertEquals("value_1", interpreter1.getProperty("property_1"));

    // create duplicated Test1Interpreter in session_1
    server.createInterpreter("group_1", "session_1", Test1Interpreter.class.getName(),
            intpProperties, "user_1");
    assertEquals(1, server.getInterpreterGroup().get("session_1").size());

    // create Test2Interpreter in session_1
    server.createInterpreter("group_1", "session_1", Test2Interpreter.class.getName(),
        intpProperties, "user_1");
    assertEquals(2, server.getInterpreterGroup().get("session_1").size());

    // create Test1Interpreter in session_2
    server.createInterpreter("group_1", "session_2", Test1Interpreter.class.getName(),
        intpProperties, "user_1");
    assertEquals(2, server.getInterpreterGroup().getSessionNum());
    assertEquals(2, server.getInterpreterGroup().get("session_1").size());
    assertEquals(1, server.getInterpreterGroup().get("session_2").size());

    final RemoteInterpreterContext intpContext = new RemoteInterpreterContext();
    intpContext.setNoteId("note_1");
    intpContext.setParagraphId("paragraph_1");
    intpContext.setGui("{}");
    intpContext.setNoteGui("{}");
    intpContext.setLocalProperties(new HashMap<>());

    // single output of SUCCESS
    RemoteInterpreterResult result = server.interpret("session_1", Test1Interpreter.class.getName(),
        "SINGLE_OUTPUT_SUCCESS", intpContext);
    assertEquals("SUCCESS", result.code);
    assertEquals(1, result.getMsg().size());
    assertEquals("SINGLE_OUTPUT_SUCCESS", result.getMsg().get(0).getData());

    // combo output of SUCCESS
    result = server.interpret("session_1", Test1Interpreter.class.getName(), "COMBO_OUTPUT_SUCCESS",
        intpContext);
    assertEquals("SUCCESS", result.code);
    assertEquals(2, result.getMsg().size());
    assertEquals("INTERPRETER_OUT", result.getMsg().get(0).getData());
    assertEquals("SINGLE_OUTPUT_SUCCESS", result.getMsg().get(1).getData());

    // single output of ERROR
    result = server.interpret("session_1", Test1Interpreter.class.getName(), "SINGLE_OUTPUT_ERROR",
        intpContext);
    assertEquals("ERROR", result.code);
    assertEquals(1, result.getMsg().size());
    assertEquals("SINGLE_OUTPUT_ERROR", result.getMsg().get(0).getData());

    // getFormType
    String formType = server.getFormType("session_1", Test1Interpreter.class.getName());
    assertEquals("NATIVE", formType);

    // cancel
    Thread sleepThread = new Thread() {
      @Override
      public void run() {
        try {
          server.interpret("session_1", Test1Interpreter.class.getName(), "SLEEP", intpContext);
        } catch (TException e) {
          e.printStackTrace();
        }
      }
    };
    sleepThread.start();

    Thread.sleep(1000);
    assertFalse(interpreter1.cancelled.get());
    server.cancel("session_1", Test1Interpreter.class.getName(), intpContext);
    // Sleep 1 second, because cancel is async.
    Thread.sleep(1000);
    assertTrue(interpreter1.cancelled.get());

    // getProgress
    assertEquals(10, server.getProgress("session_1", Test1Interpreter.class.getName(),
        intpContext));

    // before close -> thread of Test1Interpreter is running
    assertEquals(true, isThreadRunning(interpreter1.getScheduler().getName()));

    // close opened Test1Interpreter -> remove from interpreterGroup
    server.close("session_1", Test1Interpreter.class.getName());
    assertTrue(interpreter1.closed.get());
    assertEquals(1, server.getInterpreterGroup().get("session_1").size());

    // close unopened Test2Interpreter -> keep in interpreterGroup
    server.close("session_1", Test2Interpreter.class.getName());
    assertEquals(1, server.getInterpreterGroup().get("session_1").size());

    // // Close is async process
    Thread.sleep(100);
    // after close -> thread of Test1Interpreter is not running
    assertEquals(false, isThreadRunning(interpreter1.getScheduler().getName()));
  }

  private boolean isThreadRunning(String schedulerName) {
    boolean res = false;
    Set<Thread> threads = Thread.getAllStackTraces().keySet();
    for (Thread t : threads) {
      if (!t.getName().contains(schedulerName)) continue;
      res = true;
      break;
    }
    return res;
  }

  public static class Test1Interpreter extends Interpreter {

    AtomicBoolean cancelled = new AtomicBoolean();
    AtomicBoolean closed = new AtomicBoolean();

    public Test1Interpreter(Properties properties) {
      super(properties);
    }

    @Override
    public void open() {

    }

    @Override
    public InterpreterResult interpret(String st, InterpreterContext context) {
      if (st.equals("SINGLE_OUTPUT_SUCCESS")) {
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, "SINGLE_OUTPUT_SUCCESS");
      } else if (st.equals("SINGLE_OUTPUT_ERROR")) {
        return new InterpreterResult(InterpreterResult.Code.ERROR, "SINGLE_OUTPUT_ERROR");
      } else if (st.equals("COMBO_OUTPUT_SUCCESS")) {
        try {
          context.out.write("INTERPRETER_OUT");
        } catch (IOException e) {
          LOGGER.error("IO Error", e);
        }
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, "SINGLE_OUTPUT_SUCCESS");
      } else if (st.equals("SLEEP")) {
        int count = 0;
        while (!cancelled.get() || count > 30) {
          try {
            Thread.sleep(100);
          } catch (InterruptedException e) {
            return new InterpreterResult(InterpreterResult.Code.ERROR, "SLEEP_SUCCESS");
          }
          ++count;
        }
        return new InterpreterResult(InterpreterResult.Code.SUCCESS, "SLEEP_SUCCESS");
      }
      return null;
    }

    @Override
    public void cancel(InterpreterContext context) throws InterpreterException {
      cancelled.set(true);
    }

    @Override
    public FormType getFormType() throws InterpreterException {
      return FormType.NATIVE;
    }

    @Override
    public int getProgress(InterpreterContext context) throws InterpreterException {
      return 10;
    }

    @Override
    public void close() {
      closed.set(true);
    }

  }

  public static class Test2Interpreter extends Interpreter {


    public Test2Interpreter(Properties properties) {
      super(properties);
    }

    @Override
    public void open() {

    }

    @Override
    public InterpreterResult interpret(String st, InterpreterContext context) {
      return null;
    }

    @Override
    public void cancel(InterpreterContext context) throws InterpreterException {

    }

    @Override
    public FormType getFormType() throws InterpreterException {
      return FormType.NATIVE;
    }

    @Override
    public int getProgress(InterpreterContext context) throws InterpreterException {
      return 0;
    }

    @Override
    public void close() {

    }

  }
}
