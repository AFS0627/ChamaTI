package br.com.chamati;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration excluída: evita o usuário/senha padrão do Spring.
// A autenticação real (JWT + usuarios) será implementada na fase de segurança.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ChamaTiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChamaTiApplication.class, args);
    }
}
