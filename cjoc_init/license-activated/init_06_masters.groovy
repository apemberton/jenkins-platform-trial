import com.cloudbees.opscenter.server.model.ClientMaster;
import com.cloudbees.opscenter.server.properties.ConnectedMasterLicenseServerProperty;

import groovy.io.LineColumnReader
import groovy.json.JsonSlurper
import jenkins.model.Jenkins
import hudson.model.Item

import java.util.regex.Pattern
import java.util.logging.Logger

Logger logger = Logger.getLogger("init.init_06_masters.groovy")

Pattern masterPattern = Pattern.compile("jenkins-[0-9]+ \\(built-in\\)")

Jenkins jenkins = Jenkins.instance
def env = System.getenv()

List<String> masters = ["cje1"] as String[]
int nb = masters.size()

List<ClientMaster> mastersBefore = jenkins.getAllItems(ClientMaster.class)

logger.info("$nb masters configured.");
List<ClientMaster> builtInMasters = new ArrayList<ClientMaster>();
for (int i = 0; i < nb; i++) {
    String grantId = "jenkins-$i"
    String name = "jenkins-$i (built-in)"
    Item item = jenkins.getItem(name)
    if (item != null && !(item instanceof ClientMaster)) {
        logger.info("Deleting non-compliant item $item (type:${item.class}");
        item.delete();
        item = null;
    }
    if (item == null) {
        logger.info("Creating $name");
        ClientMaster cm = jenkins.createProject(ClientMaster.class, name);
        cm.setId(i);
        cm.setGrantId(grantId);
        def licenseStrategy = new ConnectedMasterLicenseServerProperty.FloatingExecutorsStrategy();
        cm.getProperties().replace(new ConnectedMasterLicenseServerProperty(licenseStrategy));
        cm.save();
        builtInMasters.add(cm);
    } else {
        logger.info("Skiping existing client master $name")
        builtInMasters.add((ClientMaster) item);
    }
}

for (Item item : jenkins.getAllItems(ClientMaster.class)) {
    ClientMaster master = (ClientMaster) item
    if (builtInMasters.contains(master)) {
        continue;
    }
    String name = master.getName()
    if (masterPattern.matcher(name).matches()) {
        logger.info("Removing $name");
        master.delete();
    } else {
        logger.info("Keeping custom master $name");
    }
}

List<ClientMaster> mastersAfter = jenkins.getAllItems(ClientMaster.class)
logger.info("MASTERS - BEFORE: " + mastersBefore + ", AFTER: " + mastersAfter)

logger.info("Restart cje1")
def response = "curl POST env['JENKINS_URL']/cje1/restart".execute().text
logger.info("Restart cje1 repsonse: " + response)