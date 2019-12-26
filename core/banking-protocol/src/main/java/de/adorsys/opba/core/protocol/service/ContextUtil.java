package de.adorsys.opba.core.protocol.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import org.flowable.engine.delegate.DelegateExecution;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.function.Consumer;

import static de.adorsys.opba.core.protocol.constant.GlobalConst.CONTEXT;

@UtilityClass
@SuppressWarnings("checkstyle:HideUtilityClassConstructor") // Lombok generates private ctor.
public class ContextUtil {

    public static <T> T getContext(DelegateExecution execution, Class<T> ctxType) {
        return execution.getVariable(CONTEXT, ctxType);
    }

    public static <T> void updateContext(DelegateExecution execution, T ctx) {
        execution.setVariable(CONTEXT, ctx);
    }

    public static <T> void getAndUpdateContext(DelegateExecution execution, Consumer<T> contextUpdater) {
        @SuppressWarnings("unchecked")
        T ctx = (T) execution.getVariable(CONTEXT);
        contextUpdater.accept(ctx);
        execution.setVariable(CONTEXT, ctx);
    }

    @SuppressWarnings("unchecked")
    public static <R, T> R evaluateSpelForCtx(String expression, DelegateExecution execution, T context) {
        return (R) evaluateSpelForCtx(expression, execution, context, Object.class);
    }

    public static <R, T> R evaluateSpelForCtx(
            String expression, DelegateExecution execution, T context, Class<R> resultClass) {
        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext parseContext = new StandardEvaluationContext(new SpelCtx<>(execution, context));
        return parser.parseExpression(expression, new TemplateParserContext()).getValue(parseContext, resultClass);
    }

    @Getter
    @RequiredArgsConstructor
    private static class SpelCtx<T> {

        private final DelegateExecution execution;
        private final T context;
    }
}