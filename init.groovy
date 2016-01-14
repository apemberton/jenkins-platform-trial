import jenkins.model.Jenkins
import org.jenkinsci.main.modules.sshd.SSHD
import jenkins.model.JenkinsLocationConfiguration

import java.util.logging.Logger

Logger logger = Logger.getLogger("fixed-ports_url.groovy")

File disableScript = new File(Jenkins.getInstance().getRootDir(), ".disable-init-script")
if (disableScript.exists()) {
    logger.info("DISABLE fixed-ports_url script")
    return
}

Thread.start {
      sleep 10000
      
      println "--> setting agent port for ssh"
      def sshd = SSHD.get()
      sshd.port = env['JENKINS_SSH_PORT'].toInteger()
      println "--> setting agent port for ssh... done"

      println "--> setting location config url"
      JenkinsLocationConfiguration locationConfiguration = JenkinsLocationConfiguration.get()
      locationConfiguration.setUrl(env['JENKINS_URL'])
      println "--> setting location config url... done"
}