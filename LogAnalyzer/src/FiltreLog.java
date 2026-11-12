import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Independent child process: stdin contains logs; stdout contains one number. */
public class FiltreLog {
    public static void main(String[] args) {
        String paraula = args.length > 0 ? args[0] : "ERROR";
        long total = 0;
        boolean teText = false;

        try (BufferedReader entrada = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String linia;
            // readLine returns null when the parent closes its sending pipe (EOF).
            while ((linia = entrada.readLine()) != null) {
                if (!linia.isBlank()) teText = true;
                // Count matching lines, not repeated occurrences in the same line.
                if (linia.contains(paraula)) total++;
            }
            if (!teText) {
                System.err.println("Error: Empty text");
                System.exit(1);
            }
            System.out.println(total);
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
