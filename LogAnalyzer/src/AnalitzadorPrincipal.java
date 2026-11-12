import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Parent process. Run with no arguments for the required demonstration. */
public class AnalitzadorPrincipal {
    private static final String TEXT_PROVA = """
            INFO Application started
            ERROR Database unavailable
            WARNING Slow response
            ERROR File not found
            WARNING Disk nearly full
            ERROR Operation failed
            WARNING Queue growing
            INFO Application finished
            """;

    public static void main(String[] args) {
        // Optio --stdin allows testing arbitrary or very large input streams.
        boolean llegirEntrada = args.length == 1 && args[0].equals("--stdin");
        long errors = 0;
        long warnings = 0;
        int codiFinal = 1;
        Process fillA = null;
        Process fillB = null;
        ExecutorService tasques = Executors.newFixedThreadPool(4);

        try {
            // Start BOTH operating-system processes before sending input.
            fillA = iniciarFill("ERROR", "errors_filtre.log");
            fillB = iniciarFill("WARNING", "errors_filtre_warning.log");
            final Process a = fillA;
            final Process b = fillB;

            // Drain stdout while writing input, so a full output pipe cannot block us.
            Future<Long> resultatA = tasques.submit(() -> llegirResultat(a));
            Future<Long> resultatB = tasques.submit(() -> llegirResultat(b));

            // One writer per child sends the SAME lines concurrently. The child
            // outputs only after EOF; the stdout readers above are already active.
            // A bounded queue for each writer also keeps large inputs out of RAM.
            java.util.concurrent.BlockingQueue<String> cuaA =
                    new java.util.concurrent.ArrayBlockingQueue<>(128);
            java.util.concurrent.BlockingQueue<String> cuaB =
                    new java.util.concurrent.ArrayBlockingQueue<>(128);
            String fi = new String("END"); // identity marker, never compared by value
            Future<Void> enviamentA = tasques.submit(() -> enviar(a, cuaA, fi));
            Future<Void> enviamentB = tasques.submit(() -> enviar(b, cuaB, fi));

            try (BufferedReader entrada = new BufferedReader(llegirEntrada
                    ? new InputStreamReader(System.in, StandardCharsets.UTF_8)
                    : new java.io.StringReader(TEXT_PROVA))) {
                String linia;
                while ((linia = entrada.readLine()) != null) {
                    posar(cuaA, linia, enviamentA);
                    posar(cuaB, linia, enviamentB);
                }
            }
            posar(cuaA, fi, enviamentA);
            posar(cuaB, fi, enviamentB);
            enviamentA.get();
            enviamentB.get();

            a.waitFor();
            b.waitFor();
            // exitValue is safe after waitFor: the process has finished.
            int codiA = a.exitValue();
            int codiB = b.exitValue();
            errors = resultatA.get();
            warnings = resultatB.get();
            codiFinal = codiA != 0 ? codiA : codiB;
            System.out.println("Estat ERROR: " + codiA);
            System.out.println("Estat WARNING: " + codiB);
            System.out.println(codiFinal == 0 ? "Execucio correcta" : "Execucio amb errors");
        } catch (Exception e) {
            System.err.println("Error del pare: " + e.getMessage());
        } finally {
            if (fillA != null && fillA.isAlive()) fillA.destroyForcibly();
            if (fillB != null && fillB.isAlive()) fillB.destroyForcibly();
            tasques.shutdownNow();
        }
        // No console output is printed after this required final line.
        System.out.println("RESULTAT: ERRORS=" + errors
                + " | WARNINGS=" + warnings + " | EXIT_CODE=" + codiFinal);
        System.exit(codiFinal);
    }

    private static Process iniciarFill(String paraula, String fitxerErrors) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "-cp",
                System.getProperty("java.class.path"), "FiltreLog", paraula);
        // Keep stderr separate: stdout must remain a single numeric result.
        pb.redirectError(new File(fitxerErrors));
        return pb.start();
    }

    private static Void enviar(Process fill,
            java.util.concurrent.BlockingQueue<String> cua, String fi) throws Exception {
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                fill.getOutputStream(), StandardCharsets.UTF_8))) {
            String linia;
            while ((linia = cua.take()) != fi) {
                out.write(linia);
                out.newLine();
            }
            out.flush(); // Deliver any characters still held in Java's buffer.
            // try-with-resources then calls close(): child receives EOF.
        }
        return null;
    }

    private static void posar(java.util.concurrent.BlockingQueue<String> cua,
            String linia, Future<Void> escriptor) throws Exception {
        // Detect a failed writer instead of waiting forever on its full queue.
        while (!cua.offer(linia, 100, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            if (escriptor.isDone()) {
                escriptor.get();
                throw new java.io.IOException("El fill ha tancat l'entrada");
            }
        }
    }

    private static long llegirResultat(Process fill) throws Exception {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                fill.getInputStream(), StandardCharsets.UTF_8))) {
            String resultat = in.readLine();
            // An invalid/empty input deliberately produces stderr and no number.
            return resultat == null ? 0 : Long.parseLong(resultat);
        }
    }
}
