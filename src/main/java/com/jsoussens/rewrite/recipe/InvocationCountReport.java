package com.jsoussens.rewrite.recipe;

import org.openrewrite.Column;
import org.openrewrite.DataTable;
import org.openrewrite.Recipe;

public class InvocationCountReport extends DataTable<InvocationCountReport.Row> {

    public InvocationCountReport(Recipe recipe) {
        super(recipe, "Public method invocation count", "Records public method invocations count");
    }

    static class Row {
        @Column(displayName = "Method",
                description = "The name of the method")
        String methodName;

        @Column(displayName = "Count",
                description = "The number of times a method is invoked.")
        Integer count;

        public Row(String methodName) {
            this.methodName = methodName;
            count = 0;
        }

        public String methodName() {
            return methodName;
        }

        public Integer count() {
            return count;
        }

        public Row increment() {
            count++;
            return this;
        }

        public Row increment(Integer value) {
            count += value;
            return this;
        }
    }
}