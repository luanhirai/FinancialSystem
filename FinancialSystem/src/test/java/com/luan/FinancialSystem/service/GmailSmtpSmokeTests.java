package com.luan.FinancialSystem.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.env.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "FINSYS_TEST_SMTP", matches = "true")
class GmailSmtpSmokeTests {
    @Test void authenticatesAndSendsTestToSenderOnly() throws Exception {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addLast(new PropertiesPropertySource("app",
                PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"))));
        var local = new org.springframework.core.io.FileSystemResource("src/main/resources/application-mail-local.properties");
        if (local.exists()) environment.getPropertySources().addFirst(new PropertiesPropertySource("local",
                PropertiesLoaderUtils.loadProperties(local)));
        var sender = new JavaMailSenderImpl();
        sender.setHost(environment.getProperty("spring.mail.host"));
        sender.setPort(environment.getProperty("spring.mail.port", Integer.class, 587));
        sender.setUsername(environment.getProperty("spring.mail.username"));
        sender.setPassword(environment.getProperty("spring.mail.password"));
        assertThat(sender.getPassword()).as("Gmail app password configured").isNotBlank();
        var properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.smtp.connectiontimeout", "10000");
        properties.setProperty("mail.smtp.timeout", "10000");
        properties.setProperty("mail.smtp.writetimeout", "10000");
        sender.testConnection();
        var message = new SimpleMailMessage();
        message.setFrom(environment.getProperty("app.mail.from"));
        message.setTo(sender.getUsername());
        message.setSubject("FINSYS - Teste de envio da recuperação de senha");
        message.setText("Teste técnico do FINSYS: autenticação SMTP e envio de e-mail concluídos. "
                + "Este e-mail não altera nenhuma senha e não exige nenhuma ação.");
        sender.send(message);
    }
}
