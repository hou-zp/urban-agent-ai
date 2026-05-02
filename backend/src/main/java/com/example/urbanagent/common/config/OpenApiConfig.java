package com.example.urbanagent.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI urbanAgentOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Urban Agent API")
                        .version("0.1.0")
                        .description("城管政策咨询、知识库、智能问数、风险审核和审计接口"))
                .components(new Components()
                        .addParameters("UserIdHeader", header("X-User-Id", "当前用户 ID，MVP 默认 demo-user"))
                        .addParameters("UserRoleHeader", header("X-User-Role", "当前角色编码"))
                        .addParameters("UserRegionHeader", header("X-User-Region", "当前区域编码")));
    }

    @Bean
    public OpenApiCustomizer userContextHeaderCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(pathItem -> pathItem.readOperations().forEach(operation -> {
                operation.addParametersItem(new Parameter().$ref("#/components/parameters/UserIdHeader"));
                operation.addParametersItem(new Parameter().$ref("#/components/parameters/UserRoleHeader"));
                operation.addParametersItem(new Parameter().$ref("#/components/parameters/UserRegionHeader"));
            }));
        };
    }

    private Parameter header(String name, String description) {
        return new Parameter()
                .in("header")
                .name(name)
                .description(description)
                .required(false)
                .schema(new StringSchema());
    }
}
