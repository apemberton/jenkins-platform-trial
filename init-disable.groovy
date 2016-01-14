import jenkins.model.Jenkins

import java.util.logging.Logger

Logger logger = Logger.getLogger("init-disable.groovy")
    
logger.info("creating file to enable disabling of specified init scripts")
new File(Jenkins.getInstance().getRootDir(), ".disable-init-script").createNewFile()