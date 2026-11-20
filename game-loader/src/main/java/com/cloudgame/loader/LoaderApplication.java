package com.cloudgame.loader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada. Un proceso por host: administra los contenedores de
 * estación ({@code cloudgame/game-station-<gameId>}) a través del socket de Docker.
 */
@SpringBootApplication
public class LoaderApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoaderApplication.class, args);
    }
}
