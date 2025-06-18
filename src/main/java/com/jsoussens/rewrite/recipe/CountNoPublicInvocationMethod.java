package com.jsoussens.rewrite.recipe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.NonNull;
import org.openrewrite.ExecutionContext;
import org.openrewrite.NlsRewrite;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class CountNoPublicInvocationMethod extends Recipe {

    @Option(displayName = "Fully Qualified base package name",
            description = "A fully qualified package name indicating the search scope",
            example = "com.yourorg")
    @NonNull
    String fullyQualifiedClassName;
    @NonNull
    private final Boolean keepAll;

    // All recipes must be serializable. This is verified by RewriteTest.rewriteRun() in your tests.
    @JsonCreator
    public CountNoPublicInvocationMethod(@NonNull @JsonProperty("fullyQualifiedClassName") String fullyQualifiedClassName,
                                         @NonNull @JsonProperty(value = "keepAll", defaultValue="false") Boolean keepAll) {
        this.fullyQualifiedClassName = fullyQualifiedClassName;
        this.keepAll = keepAll;
    }


    transient InvocationCountReport report = new InvocationCountReport(this);

    @Override
    public @NlsRewrite.DisplayName String getDisplayName() {
        return "Count public methods with no invocations";
    }

    @Override
    public @NlsRewrite.Description String getDescription() {
        return "Count public methods with no invocations.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new NoInvocationVisitor();
    }

    @Override
    public void onComplete(ExecutionContext ctx) {
        List<InvocationCountReport.Row> rows = (List<InvocationCountReport.Row>) ctx.getMessage(ExecutionContext.DATA_TABLES, new ConcurrentHashMap<>()).get(report);
        if (rows == null) {
            return;
        }

        rows.sort(Comparator.comparing(InvocationCountReport.Row::methodName));
        Map<String, InvocationCountReport.Row> reduce = new HashMap<>();
        for (InvocationCountReport.Row row : rows) {
            reduce.merge(row.methodName(), row, (row1, row2) -> row1.increment(row2.count()));
        }
        ctx.computeMessage(ExecutionContext.DATA_TABLES, null, ConcurrentHashMap::new, (extract, allDataTables) -> {
            if (!keepAll) {
                reduce.values().removeIf(row -> row.count() != 0);
            }
            ArrayList<InvocationCountReport.Row> values = new ArrayList<>(reduce.values());
            values.sort(Comparator.comparing(InvocationCountReport.Row::count));
            allDataTables.put(report, values);
            return allDataTables;
        });
    }

    private class NoInvocationVisitor extends JavaIsoVisitor<ExecutionContext> {

        private Predicate<JavaType.Method> isTarget = method -> method != null
                && method.hasFlags(Flag.Public)
                && method.getDeclaringType().getFullyQualifiedName().startsWith(fullyQualifiedClassName);

        private Predicate<J.ClassDeclaration> isTargetClass = classDeclaration -> classDeclaration != null
                && !J.ClassDeclaration.Kind.Type.Interface.equals(classDeclaration.getKind())
                && !classDeclaration.getSimpleName().endsWith("Test");

        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext executionContext) {
            JavaType.Method method = md.getMethodType();
            J.ClassDeclaration classDeclaration = getCursor().firstEnclosing(J.ClassDeclaration.class);
            if (isTargetClass.test(classDeclaration)) {
                countIfNeeded(executionContext, method, 0);
            }
            return super.visitMethodDeclaration(md, executionContext);
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext executionContext) {
            JavaType.Method method = mi.getMethodType();
            countIfNeeded(executionContext, method, 1);
            return super.visitMethodInvocation(mi, executionContext);
        }

        private void countIfNeeded(ExecutionContext executionContext, JavaType.Method method, Integer count) {
            if (isTarget.test(method)) {
                report.insertRow(executionContext, new InvocationCountReport.Row(getFQDN(method)).increment(count));
            }
        }

        private String getFQDN(JavaType.Method methodType) {
            return methodType.getDeclaringType().getFullyQualifiedName() + "." + methodType.getName();
        }
    }
}