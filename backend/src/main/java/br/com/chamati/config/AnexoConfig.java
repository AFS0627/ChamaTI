package br.com.chamati.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AnexoProperties.class)
public class AnexoConfig {
}
