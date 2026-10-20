package com.smarthire.document.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Registers the document-service configuration property records. */
@Configuration
@EnableConfigurationProperties(DocumentUploadProperties.class)
public class DocumentConfiguration {}
