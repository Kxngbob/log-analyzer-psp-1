import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AvaluadorPracticaTest {

    private static double notaBase = 0.0;

    @AfterAll
    static void mostrarResumPuntuacio() {
        System.out.println("\n==================================================");
        System.out.printf("PUNTUACIÓ AUTOMÀTICA DE L'ALUMNE: %.2f / 6.00 pts%n", notaBase);
        System.out.println("Punts pendents d'avaluar pel professor (Revisió manual):");
        System.out.println("  + 1.50 pts: Qualitat del codi i bones pràctiques");
        System.out.println("  + 2.50 pts: Vídeo demostratiu i defensa tècnica (3 min)");
        System.out.println("==================================================\n");
    }

    @Test
    @Order(1)
    @DisplayName("Nivell 1: Execució individual del procés fill (FiltreLog) [2.0 pts]")
    void testNivell1_FiltreLogIsolated() throws Exception {
        String logExemple = "2026-09-30 INFO Inici\n2026-09-30 ERROR Fallada 1\n2026-09-30 ERROR Fallada 2\n";
        String javaClasspath = System.getProperty("java.class.path");
        
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", javaClasspath, "FiltreLog", "ERROR");
        Process p = pb.start();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()))) {
            writer.write(logExemple);
            writer.flush();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line = reader.readLine();
            assertNotNull(line, "El procés fill no ha retornat cap resultat.");
            assertEquals("2", line.trim(), "El comptatge no és correcte.");
        }
        assertEquals(0, p.waitFor(), "El procés fill hauria de finalitzar amb exitCode=0.");
        
        notaBase += 2.0;
    }

    @Test
    @Order(2)
    @DisplayName("Nivell 2: Gestió de text buit i fitxer d'errors [2.0 pts]")
    void testNivell2_GestioErrors() throws Exception {
        File logErrors = new File("errors_filtre.log");
        if (logErrors.exists()) logErrors.delete();

        String javaClasspath = System.getProperty("java.class.path");
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", javaClasspath, "FiltreLog", "ERROR");
        pb.redirectError(logErrors);
        Process p = pb.start();

        try (OutputStream os = p.getOutputStream()) {
            os.flush();
        }

        int exitCode = p.waitFor();
        assertEquals(1, exitCode, "Hauria de finalitzar amb exitCode=1 quan rep un text buit.");
        assertTrue(logErrors.exists(), "No s'ha generat el fitxer errors_filtre.log.");

        notaBase += 2.0;
    }

    @Test
    @Order(3)
    @DisplayName("Nivell 3: Execució completa de l'AnalitzadorPrincipal i Format [2.0 pts]")
    void testNivell3_AnalitzadorPrincipalComplete() throws Exception {
        String javaClasspath = System.getProperty("java.class.path");
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", javaClasspath, "AnalitzadorPrincipal");
        Process p = pb.start();

        String ultimaLinia = "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("RESULTAT:")) {
                    ultimaLinia = line;
                }
            }
        }
        
        p.waitFor();
        assertFalse(ultimaLinia.isEmpty(), "No s'ha trobat la línia que comença per 'RESULTAT:'.");
        assertTrue(ultimaLinia.contains("ERRORS=3"), "El comptatge d'ERRORS no és correcte.");
        assertTrue(ultimaLinia.contains("WARNINGS=3"), "El comptatge de WARNINGS no és correcte.");
        assertTrue(ultimaLinia.contains("EXIT_CODE=0"), "El codi de sortida final no és EXIT_CODE=0.");

        notaBase += 2.0;
    }
}
