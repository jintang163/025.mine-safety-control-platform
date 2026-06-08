package com.mine.safety.config;

import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.*;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Configuration
public class DroolsConfig {

    private static final String RULES_PATH = "rules/";

    private final ConcurrentMap<String, KieSession> kieSessionCache = new ConcurrentHashMap<>();

    @Bean
    public KieServices kieServices() {
        return KieServices.Factory.get();
    }

    @Bean
    public KieFileSystem kieFileSystem(KieServices kieServices) throws IOException {
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
        ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resourcePatternResolver.getResources("classpath*:" + RULES_PATH + "**/*.*");

        for (Resource resource : resources) {
            String path = RULES_PATH + resource.getFilename();
            kieFileSystem.write(ResourceFactory.newClassPathResource(path, "UTF-8"));
            log.info("加载Drools规则文件: {}", path);
        }

        return kieFileSystem;
    }

    @Bean
    public KieContainer kieContainer(KieServices kieServices, KieFileSystem kieFileSystem) {
        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();

        Results results = kieBuilder.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            for (Message message : results.getMessages()) {
                log.error("Drools规则编译错误: {} - {}", message.getLevel(), message.getText());
            }
            throw new RuntimeException("Drools规则编译失败，请检查规则文件");
        }

        KieRepository kieRepository = kieServices.getRepository();
        kieRepository.addKieModule(kieRepository::getDefaultReleaseId);

        return kieServices.newKieContainer(kieRepository.getDefaultReleaseId());
    }

    @Bean
    public KieBase kieBase(KieContainer kieContainer) {
        return kieContainer.getKieBase();
    }

    @Bean
    public KieSession kieSession(KieContainer kieContainer) {
        KieSession kieSession = kieContainer.newKieSession();
        kieSession.addEventListener(new org.kie.api.event.rule.DebugAgendaEventListener());
        kieSession.addEventListener(new org.kie.api.event.rule.DebugRuleRuntimeEventListener());
        log.info("Drools KieSession初始化完成");
        return kieSession;
    }

    public KieSession getKieSession(String sessionName) {
        return kieSessionCache.computeIfAbsent(sessionName, k -> kieServices().newKieContainer(
                kieServices().getRepository().getDefaultReleaseId()
        ).newKieSession());
    }

    public void reloadAllRules(KieContainer kieContainer, KieServices kieServices) throws IOException {
        log.info("重新加载所有Drools规则...");

        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
        ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resourcePatternResolver.getResources("classpath*:" + RULES_PATH + "**/*.*");

        for (Resource resource : resources) {
            String path = RULES_PATH + resource.getFilename();
            kieFileSystem.write(ResourceFactory.newClassPathResource(path, "UTF-8"));
        }

        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();

        Results results = kieBuilder.getResults();
        if (results.hasMessages(Message.Level.ERROR)) {
            for (Message message : results.getMessages()) {
                log.error("Drools规则重新编译错误: {}", message.getText());
            }
            throw new RuntimeException("Drools规则重新编译失败");
        }

        kieContainer.updateToVersion(kieServices.getRepository().getDefaultReleaseId());

        kieSessionCache.clear();
        log.info("所有Drools规则重新加载完成");
    }
}
