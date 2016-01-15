import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl
import com.cloudbees.jenkins.plugins.sshslaves.SSHConnectionDetails
import com.cloudbees.jenkins.plugins.sshslaves.SSHLauncher
import com.cloudbees.opscenter.server.model.SharedSlave
import com.cloudbees.opscenter.server.properties.EnvironmentVariablesNodePropertyCustomizer
import com.cloudbees.opscenter.server.properties.NodePropertyCustomizer
import com.cloudbees.opscenter.server.properties.SharedSlaveNodePropertyCustomizer

import com.cloudbees.plugins.credentials.Credentials
import com.cloudbees.plugins.credentials.CredentialsScope
import com.cloudbees.plugins.credentials.SystemCredentialsProvider
import com.cloudbees.plugins.credentials.common.StandardCredentials
import com.google.common.base.Function
import com.google.common.base.Predicates
import com.google.common.collect.Collections2
import com.google.common.collect.Iterables
import groovy.io.LineColumnReader
import groovy.json.JsonSlurper
import hudson.model.Node
import hudson.slaves.ComputerLauncher
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.slaves.RetentionStrategy
import jenkins.model.Jenkins
import hudson.model.Item

import javax.annotation.Nullable
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

Logger logger = Logger.getLogger("init.init_06_shared_slaves.groovy")

// CREDENTIALS

def jenkins = Jenkins.getInstance()
SystemCredentialsProvider credentialsProvider = Iterables.getOnlyElement(jenkins.getExtensionList(SystemCredentialsProvider.class))

String identifierPrefix= "cjp-trial"
String builtInCredentialsDescription = "built-in slave credentials"+" ("+identifierPrefix+")"
String builtInCredentialsId = identifierPrefix+"-"+"built-in-slave-credentials"
String username = "jenkins"
String password = "jenkins"

Function<Credentials, String> credentialsToDescription = new Function<Credentials, String>() {
    @Override
    String apply(@Nullable Credentials credentials) {
        if (credentials == null)
            return null
        if (!credentials instanceof StandardCredentials)
            return null

        return ((StandardCredentials) credentials).getDescription()
    }
}

Function<Credentials, String> credentialsToString = new Function<Credentials, String>() {
    @Override
    String apply(@Nullable Credentials credentials) {
        if (credentials == null)
            return null

        String msg = credentials.getClass().getName()

        if (!credentials instanceof StandardCredentials)
            return null

        return msg + " - " + ((StandardCredentials) credentials).getDescription()
    }
}

String credentialsBefore = Collections2.transform(credentialsProvider.credentials, credentialsToString).toString()
logger.fine("current credentials: " + Collections2.transform(credentialsProvider.credentials, credentialsToString))

Collection<Credentials> credentialssToCheck = Collections2.filter(credentialsProvider.credentials, Predicates.compose(Predicates.equalTo(builtInCredentialsDescription), credentialsToDescription))
logger.fine("credentials to delete: " + Collections2.transform(credentialssToCheck, credentialsToString))

credentialsProvider.credentials.removeAll(credentialssToCheck)

Credentials slavesSshCredentials = new UsernamePasswordCredentialsImpl(
        CredentialsScope.GLOBAL,
        builtInCredentialsId,
        builtInCredentialsDescription,
        username,
        password)

credentialsProvider.credentials.add(slavesSshCredentials)

credentialsProvider.save()
logger.fine("new credentials: " + Collections2.transform(credentialsProvider.credentials, credentialsToString))

String credentialsAfter = Collections2.transform(credentialsProvider.credentials, credentialsToString).toString()
logger.info("CREDENTIALS - BEFORE: " + credentialsBefore + ", AFTER: " + credentialsAfter)

// SSH SLAVES

List<String> hosts = ["slave1"] as String[]
String builtInSlaveDescription = "Built-in shared slave"
String remoteFs = "/home/jenkins"

Function<Node, String> nodeToName = new Function<Node, String>() {
    @Override
    String apply(@Nullable Node node) {
        return node == null ? null : node.getNodeName()
    }
}

def applyEnvironmentVariables = { slave ->
  Map<String, String> desiredEnvVars = new HashMap<String, String>()
  desiredEnvVars.put("PATH+BUILDPACKS", null)
  desiredEnvVars.put("PATH+BUIDPACKS_NODEJS", null)
  desiredEnvVars.put("PATH+CF_CLI", null)
  desiredEnvVars.put("PATH+DOCKER", null)
  desiredEnvVars.put("PATH+GIT", null)
  desiredEnvVars.put("PATH+RUBY", null)
  desiredEnvVars.put("PATH+MAVEN", null)
  desiredEnvVars.put("PATH+OPENJDK8", null)

  desiredEnvVars.put("PATH", null)

  desiredEnvVars.put("MVN_HOME", null)
  desiredEnvVars.put("MAVEN_HOME", null)
  desiredEnvVars.put("JAVA_HOME", null)

  desiredEnvVars.put("DOCKER_HOST", null)
  
  def sharedSlaveNodePropertyCustomizer = slave.getProperties().get(SharedSlaveNodePropertyCustomizer.class);
  def environmentVariablesNodeProperty = null;
  if (sharedSlaveNodePropertyCustomizer == null) {
    environmentVariablesNodeProperty = new EnvironmentVariablesNodeProperty();
    List<NodePropertyCustomizer> listOfNodePropertyCustomizer = new ArrayList<NodePropertyCustomizer>()
    sharedSlaveNodePropertyCustomizer = new SharedSlaveNodePropertyCustomizer(listOfNodePropertyCustomizer)
    slave.getProperties().add(sharedSlaveNodePropertyCustomizer)
  } else {
    def listOfNodePropertyCustomizer = sharedSlaveNodePropertyCustomizer.getCustomizers();
    for (NodePropertyCustomizer customizer: listOfNodePropertyCustomizer) {
      if (customizer instanceof EnvironmentVariablesNodePropertyCustomizer) {
        environmentVariablesNodeProperty = customizer.getValue();
      }
    }
    if (environmentVariablesNodeProperty == null) {
      environmentVariablesNodeProperty = new EnvironmentVariablesNodeProperty();
      listOfNodePropertyCustomizer.add(new EnvironmentVariablesNodePropertyCustomizer(environmentVariablesNodeProperty));
    }
  }
  environmentVariablesNodeProperty.envVars.overrideAll(desiredEnvVars)
  // If we don't have anymore environmnt variables, just remove the customizer
  if (environmentVariablesNodeProperty.envVars.isEmpty()) {
    slave.getProperties().remove(sharedSlaveNodePropertyCustomizer);
  }
}

def createComputerLauncher = { host ->
    def connectionDetails = new SSHConnectionDetails(builtInCredentialsId, 22, "", "", "", "", false)
    return new SSHLauncher(host, connectionDetails)
}

List<String> hostsToCreate = new ArrayList<>(hosts)
for (Item item : jenkins.getAllItems(SharedSlave.class)) {
  SharedSlave slave = (SharedSlave) item
  if (slave.getLauncher() != null) {
      if (slave.getLauncher() instanceof SSHLauncher) {
          SSHLauncher launcher = (SSHLauncher) slave.getLauncher()
          def host = launcher.getHost()
          if (hosts.contains(host)) {
              hostsToCreate.remove(host)
              slave.setDisabled(true);
              slave.setLauncher(createComputerLauncher(host)) // always update launcher
              applyEnvironmentVariables(slave)
              if (builtInCredentialsId.equals(launcher.getConnectionDetails().getCredentialsId())) {
                  logger.info("existing built-in shared slave " + slave.getName() + " with host " + launcher.getHost() + " is properly configured (valid credentials)")
              } else {
                  logger.warning("existing slave " + slave.getName() + " with host " + launcher.getHost() + " is WRONGLY configured, PLEASE CHECK AND FIX THIS SLAVE CONFIGURATION (unexpected credential " + launcher.getConnectionDetails().getCredentialsId() + ")")
              }
              slave.save()
              slave.doEnable()
          } else if (builtInSlaveDescription.equals(slave.getDescription())) {
              logger.info("delete no longer valid built-in shared slave \"" + slave.getName() + "\" with host " + launcher.getHost())
              jenkins.removeNode(slave)
          }
      } else {
          logger.info("skip non CloudBees SSH shared slave " + slave.getName())
      }
  } else {
      logger.info("launcher is null for shared slave: " + slave.getName())
  }
}

List<SharedSlave> sharedSlavesToCheck = new ArrayList<>()

for (String host : hostsToCreate) {
    Node.Mode normal = Node.Mode.NORMAL
    RetentionStrategy always = RetentionStrategy.INSTANCE
    SharedSlave slave = new SharedSlave(jenkins, host + " (built-in)")
    slave.setLabelString("shared-built-in");
    slave.setRetentionStrategy(always)
    slave.setMode(normal)
    slave.setNumExecutors(2);
    slave.setRemoteFS(remoteFs)
    slave.setDescription(builtInSlaveDescription)
    slave.setLauncher(createComputerLauncher(host))
    applyEnvironmentVariables(slave)
    slave.save()
    slave.doEnable()
    sharedSlavesToCheck.add(slave)
    logger.info("create shared slave \"" + slave.getName() + "\"")
    jenkins.putItem(slave)
}


Runnable runnable = new Runnable() {
    @Override
    void run() {
        for(int i = 0;i<10; i++) {
            int pendingSlaves = 0
            for (SharedSlave sharedSlave : sharedSlavesToCheck) {
                logger.fine("checking if all the slaves are enabled")
                if (sharedSlave.isEnabled()) {
                    logger.fine("SharedSlave " + sharedSlave.name + " is enabled")
                } else {
                    logger.fine("SharedSlave " + sharedSlave.name + " is not enabled, trying to enable..")
                    sharedSlave.doEnable()
                    pendingSlaves++
                }
            }
            if (pendingSlaves == 0) {
                logger.fine("all the Shared Slaves are enabled")
                return
            }
            Thread.sleep(TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS))
        }
    }
}

Thread thread = new Thread(runnable)
thread.setDaemon(true)
thread.setName("shared-slave-initializer")

thread.start()