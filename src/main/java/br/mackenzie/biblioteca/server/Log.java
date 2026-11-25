package br.mackenzie.biblioteca.server;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

final class Log {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Log() {
    }

    static void info(String formato, Object... args) {
        System.out.println("[" + LocalTime.now().format(HORA) + "] [SERVIDOR] " + String.format(formato, args));
    }
}
