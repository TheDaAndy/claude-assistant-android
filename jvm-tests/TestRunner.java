import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Minimaler abhaengigkeitsfreier Test-Runner fuer die reinen Java-Klassen der Ankai-Anbindung. */
public class TestRunner {

    public static void main(String[] args) throws Exception {
        List<String> failures = new ArrayList<>();
        int total = 0;
        for (String className : args) {
            Class<?> cls = Class.forName(className);
            Object instance = cls.getDeclaredConstructor().newInstance();
            for (Method m : cls.getDeclaredMethods()) {
                if (!m.getName().startsWith("test") || m.getParameterCount() != 0) continue;
                total++;
                try {
                    m.invoke(instance);
                    System.out.println("  ok   " + cls.getSimpleName() + "." + m.getName());
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    System.out.println("  FAIL " + cls.getSimpleName() + "." + m.getName() + ": " + cause);
                    failures.add(cls.getSimpleName() + "." + m.getName() + ": " + cause);
                }
            }
        }
        System.out.println("\n" + (total - failures.size()) + "/" + total + " Tests gruen");
        if (!failures.isEmpty()) {
            for (String f : failures) System.out.println("FAILED: " + f);
            System.exit(1);
        }
    }
}
