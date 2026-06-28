package com.multiagent.review.config;

import com.multiagent.review.agent.AnalyserAgent;
import com.multiagent.review.agent.CriticAgent;
import com.multiagent.review.agent.FixerAgent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChainConfig {

    @Bean
    public AnalyserAgent analyserAgent(ChatLanguageModel chatModel) {
        return AiServices.builder(AnalyserAgent.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    public CriticAgent criticAgent(ChatLanguageModel chatModel) {
        return AiServices.builder(CriticAgent.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    public FixerAgent fixerAgent(ChatLanguageModel chatModel) {
        return AiServices.builder(FixerAgent.class)
                .chatLanguageModel(chatModel)
                .build();
    }
}
