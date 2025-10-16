package langhchainback.langchain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class LangchainApplication {

	public static void main(String[] args) {
		SpringApplication.run(LangchainApplication.class, args);
		
		log.info("안녕하세요");	
	}

}
