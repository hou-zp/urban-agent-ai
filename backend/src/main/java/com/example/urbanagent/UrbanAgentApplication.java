package com.example.urbanagent;

import com.example.urbanagent.agent.application.AgentConstants;
import com.example.urbanagent.agent.application.ChartProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AgentConstants.class, ChartProperties.class})
public class UrbanAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrbanAgentApplication.class, args);
    }
}
