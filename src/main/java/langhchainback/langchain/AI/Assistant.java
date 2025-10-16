package langhchainback.langchain.AI;


import dev.langchain4j.service.spring.AiService;


public interface Assistant {

    String chat(String userMessage);
}