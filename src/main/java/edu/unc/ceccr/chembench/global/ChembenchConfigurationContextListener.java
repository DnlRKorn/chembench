package edu.unc.ceccr.chembench.global;

import edu.unc.ceccr.chembench.utilities.ParseConfigurationXML;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.nio.file.Paths;

public class ChembenchConfigurationContextListener implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        // read $CHEMBENCH_HOME, then append config directory / filename
        String ENV_CHEMBENCH_HOME = null;
        try {
            ENV_CHEMBENCH_HOME = System.getenv("CHEMBENCH_HOME");
        } catch (SecurityException e) {
            throw new RuntimeException("Couldn't read $CHEMBENCH_HOME environment variable", e);
        }
        if (ENV_CHEMBENCH_HOME == null) {
            throw new RuntimeException("Environment variable $CHEMBENCH_HOME doesn't exist");
        }

        String configFilePath = Paths.get(ENV_CHEMBENCH_HOME, "config", "systemConfig.xml").toString();
        ParseConfigurationXML.initializeConstants(configFilePath);
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
    }
}
