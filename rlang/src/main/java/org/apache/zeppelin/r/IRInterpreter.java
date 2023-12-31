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

package org.apache.zeppelin.r;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.zeppelin.interpreter.ZeppelinContext;
import org.apache.zeppelin.interpreter.InterpreterContext;
import org.apache.zeppelin.interpreter.InterpreterException;
import org.apache.zeppelin.interpreter.InterpreterResult;
import org.apache.zeppelin.interpreter.jupyter.proto.ExecuteRequest;
import org.apache.zeppelin.interpreter.jupyter.proto.ExecuteResponse;
import org.apache.zeppelin.interpreter.jupyter.proto.ExecuteStatus;
import org.apache.zeppelin.interpreter.remote.RemoteInterpreterUtils;
import org.apache.zeppelin.jupyter.JupyterKernelInterpreter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Properties;

/**
 * R Interpreter which use the IRKernel (https://github.com/IRkernel/IRkernel),
 * Besides that it use Spark to setup communication channel between JVM and R process, so that user
 * can use ZeppelinContext.
 */
public class IRInterpreter extends JupyterKernelInterpreter {

  private static final Logger LOGGER = LoggerFactory.getLogger(IRInterpreter.class);
  private static RZeppelinContext z;

  // It is used to store shiny related code (ui.R & server.R)
  // only one shiny app can be hosted in one R session.
  private File shinyAppFolder;
  private SparkRBackend sparkRBackend;
  private String shinyPortRange;

  public IRInterpreter(Properties properties) {
    super("ir", properties);
  }

  /**
   * RInterpreter just use spark-core for the communication between R process and jvm process.
   * SparkContext is not created in this RInterpreter.
   * Sub class can override this, e.g. SparkRInterpreter
   * @return
   */
  protected boolean isSparkSupported() {
    return false;
  }

  /**
   * The spark version specified in pom.xml
   * Sub class can override this, e.g. SparkRInterpreter
   * @return
   */
  protected int sparkVersion() {
    return 20404;
  }

  @Override
  public void open() throws InterpreterException {
    super.open();

    this.sparkRBackend = SparkRBackend.get();
    // Share the same SparkRBackend across sessions
    synchronized (sparkRBackend) {
      if (!sparkRBackend.isStarted()) {
        try {
          sparkRBackend.init();
        } catch (Exception e) {
          throw new InterpreterException("Fail to init SparkRBackend", e);
        }
        sparkRBackend.start();
      }
    }

    synchronized (IRInterpreter.class) {
      if (this.z == null) {
        z = new RZeppelinContext(getInterpreterGroup().getInterpreterHookRegistry(),
                Integer.parseInt(getProperty("zeppelin.R.maxResult", "1000")));
      }
    }

    try {
      initIRKernel();
    } catch (IOException e) {
      throw new InterpreterException("Fail to init IR Kernel:\n" +
              ExceptionUtils.getStackTrace(e), e);
    }

    try {
      this.shinyAppFolder = Files.createTempDirectory("zeppelin-shiny").toFile();
      this.shinyAppFolder.deleteOnExit();
      this.shinyPortRange = properties.getProperty("zeppelin.R.shiny.portRange", ":");
    } catch (IOException e) {
      throw new InterpreterException(e);
    }
  }

  /**
   * Init IRKernel by execute R script zeppelin-isparkr.R
   * @throws IOException
   * @throws InterpreterException
   */
  protected void initIRKernel() throws IOException, InterpreterException {
    String timeout = getProperty("spark.r.backendConnectionTimeout", "6000");
    InputStream input =
            getClass().getClassLoader().getResourceAsStream("R/zeppelin_isparkr.R");
    String code = IOUtils.toString(input, StandardCharsets.UTF_8)
            .replace("${Port}", sparkRBackend.port() + "")
            .replace("${version}", sparkVersion() + "")
            .replace("${libPath}", "\"" + SparkRUtils.getSparkRLib(isSparkSupported()) + "\"")
            .replace("${timeout}", timeout)
            .replace("${isSparkSupported}", "\"" + isSparkSupported() + "\"")
            .replace("${authSecret}", "\"" + sparkRBackend.socketSecret() + "\"");
    LOGGER.debug("Init IRKernel via script:\n{}", code);
    ExecuteResponse response = jupyterKernelClient.block_execute(ExecuteRequest.newBuilder()
            .setCode(code).build());
    if (response.getStatus() != ExecuteStatus.SUCCESS) {
      throw new IOException("Fail to setup JVMGateway\n" + response.getOutput());
    }
  }

  @Override
  protected Map<String, String> setupKernelEnv() throws IOException {
    Map<String, String> envs = super.setupKernelEnv();
    String pathEnv = envs.getOrDefault("PATH", "");
    if (condaEnv != null) {
      // add ${PWD}/${condaEnv}/bin to PATH, otherwise JupyterKernelInterpreter will fail to
      // find R to launch IRKernel
      pathEnv = new File(".").getAbsolutePath() + File.separator + condaEnv +
              File.separator + "bin" + File.pathSeparator + pathEnv;
      envs.put("PATH", pathEnv);
    }
    return envs;
  }

  @Override
  public String getKernelName() {
    return "ir";
  }

  @Override
  public ZeppelinContext buildZeppelinContext() {
    return new RZeppelinContext(getInterpreterGroup().getInterpreterHookRegistry(),
            Integer.parseInt(getProperty("zeppelin.r.maxResult", "1000")));
  }

  public InterpreterResult shinyUI(String st,
                                   InterpreterContext context) throws InterpreterException {
    File uiFile = new File(shinyAppFolder, "ui.R");
    try (FileWriter writer = new FileWriter(uiFile)){
      IOUtils.copy(new StringReader(st), writer);
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, "Write ui.R to "
              + shinyAppFolder.getAbsolutePath() + " successfully.");
    } catch (IOException e) {
      throw new InterpreterException("Fail to write shiny file ui.R", e);
    }
  }

  public InterpreterResult shinyServer(String st,
                                       InterpreterContext context) throws InterpreterException {
    File serverFile = new File(shinyAppFolder, "server.R");
    try (FileWriter writer = new FileWriter(serverFile);){
      IOUtils.copy(new StringReader(st), writer);
      return new InterpreterResult(InterpreterResult.Code.SUCCESS, "Write server.R to "
              + shinyAppFolder.getAbsolutePath() + " successfully.");
    } catch (IOException e) {
      throw new InterpreterException("Fail to write shiny file server.R", e);
    }
  }

  public InterpreterResult runShinyApp(InterpreterContext context)
          throws IOException, InterpreterException {
    // redirect R kernel process to InterpreterOutput of current paragraph
    // because the error message after shiny app launched is printed in R kernel process
    getKernelProcessLauncher().setRedirectedContext(context);
    try {
      StringBuilder builder = new StringBuilder("library(shiny)\n");
      String host = RemoteInterpreterUtils.findAvailableHostAddress();
      int port = RemoteInterpreterUtils.findAvailablePort(shinyPortRange);
      builder.append("runApp(appDir='" + shinyAppFolder.getAbsolutePath() + "', " +
              "port=" + port + ", host='" + host + "', launch.browser=FALSE)");
      // shiny app will launch and block there until user cancel the paragraph.
      LOGGER.info("Run shiny app code: {}", builder.toString());
      return internalInterpret(builder.toString(), context);
    } finally {
      getKernelProcessLauncher().setRedirectedContext(null);
    }
  }

  public static RZeppelinContext getRZeppelinContext() {
    return z;
  }
}
