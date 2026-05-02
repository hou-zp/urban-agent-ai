package com.example.urbanagent.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "urban-agent.bootstrap.demo-data")
public class DemoBootstrapProperties {

    private boolean enabled = false;
    private boolean resetOnStartup = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isResetOnStartup() {
        return resetOnStartup;
    }

    public void setResetOnStartup(boolean resetOnStartup) {
        this.resetOnStartup = resetOnStartup;
    }
}
