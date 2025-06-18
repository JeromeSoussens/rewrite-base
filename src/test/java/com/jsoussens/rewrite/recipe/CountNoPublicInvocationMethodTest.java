package com.jsoussens.rewrite.recipe;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.java.Assertions.java;

class CountNoPublicInvocationMethodTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CountNoPublicInvocationMethod("com.acme", true))
                .parser(JavaParser.fromJavaVersion()
                        .logCompilationWarningsAndErrors(true)
                        .classpath("junit-jupiter-api"));
    }

    @Test
    void reportNonUsedPublicMethods() {
        //language=java
        rewriteRun(

            spec -> spec
                    .beforeRecipe(withToolingApi())
                    .dataTable(InvocationCountReport.Row.class, rows -> {
                        assertThat(rows)
                                .filteredOn(it -> "com.acme.PrintService.count".equals(it.methodName()))
                                .isEmpty();
                        assertThat(rows)
                                .filteredOn(it -> "com.acme.MyOtherServiceTest.testMyService".equals(it.methodName()))
                                .isEmpty();
                        assertThat(rows)
                                .filteredOn(it -> "com.acme.MyOtherService.doTheJob".equals(it.methodName()) && Integer.valueOf(0).equals(it.count()))
                                .hasSize(1);
                        assertThat(rows)
                                .filteredOn(it -> "com.acme.MyService.print".equals(it.methodName()) && Integer.valueOf(0).equals(it.count()))
                                .hasSize(1);
                        assertThat(rows)
                                .filteredOn(it -> "com.acme.MyService.count".equals(it.methodName()) && Integer.valueOf(1).equals(it.count()))
                                .hasSize(1);
                    }),
            java(
                    "package com.acme;\n" +
                    "\n" +
                    "public interface PrintService {\n" +
                    "    void count();\n" +
                    "}\n"),
            java(
                    "package com.acme;\n" +
                    "\n" +
                    "import com.acme.PrintService;\n" +
                    "\n" +
                    "public class MyService implements PrintService {\n" +
                    "    @Override\n" +
                    "    public void count()  { }\n" +
                    "\n" +
                    "    public void print()  {\n" +
                    "        System.out.println(\"Hello World\");\n" +
                    "     }\n" +
                    "}\n"),
            java(
                    "package com.acme;\n" +
                    "\n" +
                    "public class MyOtherService {\n" +
                    "\n" +
                    "    private final MyService serv;\n" +
                    "\n" +
                    "    MyOtherService(MyService serv) {\n" +
                    "        this.serv = serv;\n" +
                    "    }\n" +
                    "\n" +
                    "    public void doTheJob() {\n" +
                    "        serv.count();\n" +
                    "    }\n" +
                    "}\n"
            ),
            java(
                    "package com.acme;\n" +
                    "\n" +
                    "import org.junit.jupiter.api.Test;\n" +
                    "\n" +
                    "public class MyOtherServiceTest {\n" +
                    "\n" +
                    "    @Test\n" +
                    "    public void testMyService() {}\n" +
                    "}\n"
            )
        );
    }

    @Test
    void reportDontCrashOnEmptyModule() {
        rewriteRun(
            spec -> spec
                    .beforeRecipe(withToolingApi())
                    .afterRecipe(run ->
                        assertThat(run.getDataTables()).isEmpty()));

    }

}