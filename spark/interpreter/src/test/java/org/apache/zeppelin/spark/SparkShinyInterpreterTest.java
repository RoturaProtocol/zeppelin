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

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.commons.io.IOUtils;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterGroup;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.InterpreterResultMessage;
import org.apache.zeppelin.interpreter.LazyOpenInterpreter;
import org.apache.zeppelin.r.ShinyInterpreterTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SparkShinyInterpreterTest extends ShinyInterpreterTest {

  private SparkInterpreter sparkInterpreter;

  @Override
  @BeforeEach
  public void setUp() throws InterpreterException {
    Properties properties = new Properties();
    properties.setProperty(SparkStringConstants.MASTER_PROP_NAME, "local[*]");
    properties.setProperty(SparkStringConstants.APP_NAME_PROP_NAME, "test");

    InterpreterContext context = getInterpreterContext();
    InterpreterContext.set(context);
    interpreter = new SparkShinyInterpreter(properties);

    InterpreterGroup interpreterGroup = new InterpreterGroup();
    interpreterGroup.addInterpreterToSession(new LazyOpenInterpreter(interpreter), "session_1");
    interpreter.setInterpreterGroup(interpreterGroup);

    sparkInterpreter = new SparkInterpreter(properties);
    interpreterGroup.addInterpreterToSession(new LazyOpenInterpreter(sparkInterpreter), "session_1");
    sparkInterpreter.setInterpreterGroup(interpreterGroup);

    interpreter.open();
  }

  @Override
  @AfterEach
  public void tearDown() throws InterpreterException {
    if (interpreter != null) {
      interpreter.close();
    }
  }

  @Test
  void testSparkShinyApp()
    throws IOException, InterpreterException, InterruptedException, UnirestException {
    /****************** Launch Shiny app with default app name *****************************/
    InterpreterContext context = getInterpreterContext();
    context.getLocalProperties().put("type", "ui");
    InterpreterResult result =
      interpreter.interpret(
        IOUtils.toString(getClass().getResource("/spark_ui.R"), StandardCharsets.UTF_8), context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    context = getInterpreterContext();
    context.getLocalProperties().put("type", "server");
    result = interpreter.interpret(
      IOUtils.toString(getClass().getResource("/spark_server.R"), StandardCharsets.UTF_8), context);
    assertEquals(InterpreterResult.Code.SUCCESS, result.code());

    final InterpreterContext context2 = getInterpreterContext();
    context2.getLocalProperties().put("type", "run");
    Thread thread = new Thread(() -> {
      try {
        interpreter.interpret("", context2);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
    thread.start();
    // wait for the shiny app start
    Thread.sleep(5 * 1000);
    // extract shiny url
    List<InterpreterResultMessage> resultMessages = context2.out.toInterpreterResultMessage();
    assertEquals(1, resultMessages.size(), resultMessages.toString());
    assertEquals(InterpreterResult.Type.HTML, resultMessages.get(0).getType());
    String resultMessageData = resultMessages.get(0).getData();
    assertTrue(resultMessageData.contains("<iframe"), resultMessageData);
    Pattern urlPattern = Pattern.compile(".*src=\"(http\\S*)\".*", Pattern.DOTALL);
    Matcher matcher = urlPattern.matcher(resultMessageData);
    if (!matcher.matches()) {
      fail("Unable to extract url: " + resultMessageData);
    }
    String shinyURL = matcher.group(1);

    // verify shiny app via calling its rest api
    HttpResponse<String> response = Unirest.get(shinyURL).asString();
    assertEquals(200, response.getStatus());
    assertTrue(response.getBody().contains("Spark Version"), response.getBody());
  }
}
