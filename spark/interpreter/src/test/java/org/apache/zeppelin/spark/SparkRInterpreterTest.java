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

package org.apache.zeppelin.spark;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.zeppelin.interpreter.Interpreter;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterOutput;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterEventClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SparkRInterpreterTest {

  private SparkRInterpreter sparkRInterpreter;
  private SparkInterpreter sparkInterpreter;
  private RemoteInterpreterEventClient mockRemoteIntpEventClient = mock(RemoteInterpreterEventClient.class);

  @BeforeEach
  public void setUp() throws InterpreterException {
    Properties properties = new Properties();
    properties.setProperty(SparkStringConstants.MASTER_PROP_NAME, "local");
    properties.setProperty(SparkStringConstants.APP_NAME_PROP_NAME, "test");
    properties.setProperty("zeppelin.spark.maxResult", "100");
    properties.setProperty("zeppelin.R.knitr", "true");
    properties.setProperty("spark.r.backendConnectionTimeout", "10");
    properties.setProperty("zeppelin.spark.deprecatedMsg.show", "false");
    properties.setProperty("spark.sql.execution.arrow.sparkr.enabled", "false");

    InterpreterContext context = getInterpreterContext();
    InterpreterContext.set(context);
    sparkRInterpreter = new SparkRInterpreter(properties);
    sparkInterpreter = new SparkInterpreter(properties);

    InterpreterGroup interpreterGroup = new InterpreterGroup();
    interpreterGroup.addInterpreterToSession(new LazyOpenInterpreter(sparkRInterpreter), "session_1");
    interpreterGroup.addInterpreterToSession(new LazyOpenInterpreter(sparkInterpreter), "session_1");
    sparkRInterpreter.setInterpreterGroup(interpreterGroup);
    sparkInterpreter.setInterpreterGroup(interpreterGroup);

    sparkRInterpreter.open();
  }

  @AfterEach
  public void tearDown() throws InterpreterException {
    sparkInterpreter.close();
  }

  @Test
  void testSparkRInterpreter() throws InterpreterException, InterruptedException {
    InterpreterResult result = sparkRInterpreter.interpret("1+1", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(result.message().get(0).getData().contains("2"));

    result = sparkRInterpreter.interpret("sparkR.version()", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    result = sparkRInterpreter.interpret("df <- as.DataFrame(faithful)\nhead(df)", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertTrue(result.message().get(0).getData().contains("eruptions waiting"), result.toString());
    // spark job url is sent
    verify(mockRemoteIntpEventClient, atLeastOnce()).onParaInfosReceived(any(Map.class));

    // cancel
    InterpreterContext context = getInterpreterContext();
    InterpreterContext finalContext = context;
    Thread thread = new Thread() {
      @Override
      public void run() {
        try {
          InterpreterResult result = sparkRInterpreter.interpret("ldf <- dapplyCollect(\n" +
                  "         df,\n" +
                  "         function(x) {\n" +
                  "           Sys.sleep(3)\n" +
                  "           x <- cbind(x, \"waiting_secs\" = x$waiting * 60)\n" +
                  "         })\n" +
                  "head(ldf, 3)", finalContext);
          assertTrue(result.message().get(0).getData().contains("cancelled"));
        } catch (InterpreterException e) {
          fail("Should not throw InterpreterException");
        }
      }
    };
    thread.setName("Cancel-Thread");
    thread.start();
    Thread.sleep(1000);
    sparkRInterpreter.cancel(context);

    // plotting
    context = getInterpreterContext();
    context.getLocalProperties().put("imageWidth", "100");
    result = sparkRInterpreter.interpret("hist(mtcars$mpg)", context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(1, result.message().size());
    assertEquals(InterpreterResult.Type.HTML, result.message().get(0).getType());
    assertTrue(result.message().get(0).getData().contains("<img src="));
    assertTrue(result.message().get(0).getData().contains("width=\"100\""));

    result = sparkRInterpreter.interpret("library(ggplot2)\n" +
        "ggplot(diamonds, aes(x=carat, y=price, color=cut)) + geom_point()", getInterpreterContext());
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());
    assertEquals(1, result.message().size());
    assertEquals(InterpreterResult.Type.HTML, result.message().get(0).getType());
    assertTrue(result.message().get(0).getData().contains("<img src="));

    // sparkr backend would be timeout after 10 seconds
    Thread.sleep(15 * 1000);
    result = sparkRInterpreter.interpret("1+1", getInterpreterContext());
    assertEquals(InterpreterResult.Code.ERROR, result.code());
    assertTrue(result.message().get(0).getData().contains("sparkR backend is dead"));
  }

  @Test
  void testInvalidR() throws InterpreterException {
    tearDown();

    Properties properties = new Properties();
    properties.setProperty("zeppelin.R.cmd", "invalid_r");
    properties.setProperty(SparkStringConstants.MASTER_PROP_NAME, "local");
    properties.setProperty(SparkStringConstants.APP_NAME_PROP_NAME, "test");

    InterpreterGroup interpreterGroup = new InterpreterGroup();
    Interpreter sparkRInterpreter = new LazyOpenInterpreter(new SparkRInterpreter(properties));
    Interpreter sparkInterpreter = new LazyOpenInterpreter(new SparkInterpreter(properties));
    interpreterGroup.addInterpreterToSession(sparkRInterpreter, "session_1");
    interpreterGroup.addInterpreterToSession(sparkInterpreter, "session_1");
    sparkRInterpreter.setInterpreterGroup(interpreterGroup);
    sparkInterpreter.setInterpreterGroup(interpreterGroup);

    InterpreterContext context = getInterpreterContext();
    InterpreterContext.set(context);

    try {
      sparkRInterpreter.interpret("1+1", getInterpreterContext());
      fail("Should fail to open SparkRInterpreter");
    } catch (InterpreterException e) {
      String stacktrace = ExceptionUtils.getStackTrace(e);
      assertTrue(stacktrace.contains("No such file or directory"), stacktrace);
    }
  }

  private InterpreterContext getInterpreterContext() {
    InterpreterContext context = InterpreterContext.builder()
        .setNoteId("note_1")
        .setParagraphId("paragraph_1")
        .setIntpEventClient(mockRemoteIntpEventClient)
        .setInterpreterOut(new InterpreterOutput())
        .setLocalProperties(new HashMap<>())
        .build();
    return context;
  }
}

