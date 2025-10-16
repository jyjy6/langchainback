package langhchainback.langchain.AI;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AssistantController {
    private final Assistant assistant;

    @GetMapping("/ai/chat")
    public String chat(@RequestParam String message) {
        return assistant.chat(message);
    }

}
