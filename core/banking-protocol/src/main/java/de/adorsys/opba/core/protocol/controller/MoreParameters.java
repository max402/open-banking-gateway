package de.adorsys.opba.core.protocol.controller;

import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Iterables;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import de.adorsys.opba.core.protocol.domain.dto.forms.PsuPassword;
import de.adorsys.opba.core.protocol.domain.dto.forms.ScaChallengeResult;
import de.adorsys.opba.core.protocol.domain.dto.forms.ScaSelectedMethod;
import de.adorsys.opba.core.protocol.domain.entity.ProtocolAction;
import de.adorsys.opba.core.protocol.service.eventbus.ProcessEventHandlerRegistrar;
import de.adorsys.opba.core.protocol.service.xs2a.Xs2aResultExtractor;
import de.adorsys.opba.core.protocol.service.xs2a.context.BaseContext;
import de.adorsys.opba.core.protocol.service.xs2a.context.Xs2aContext;
import de.adorsys.xs2a.adapter.service.model.AccountDetails;
import de.adorsys.xs2a.adapter.service.model.TransactionsReport;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.flowable.engine.RuntimeService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.jayway.jsonpath.Option.DEFAULT_PATH_LEAF_TO_NULL;
import static com.jayway.jsonpath.Option.SUPPRESS_EXCEPTIONS;
import static de.adorsys.opba.core.protocol.constant.GlobalConst.CONTEXT;
import static de.adorsys.opba.core.protocol.controller.constants.ApiPaths.MORE_PARAMETERS;
import static de.adorsys.opba.core.protocol.controller.constants.ApiPaths.MORE_PARAMETERS_PSU_PASSWORD;
import static de.adorsys.opba.core.protocol.controller.constants.ApiPaths.MORE_PARAMETERS_REPORT_SCA_RESULT;
import static de.adorsys.opba.core.protocol.controller.constants.ApiPaths.MORE_PARAMETERS_SELECT_SCA_METHOD;
import static de.adorsys.opba.core.protocol.controller.constants.ApiVersion.API_1;

@RestController
@RequestMapping(API_1)
@RequiredArgsConstructor
public class MoreParameters {

    private final ObjectMapper mapper;
    private final RuntimeService runtimeService;
    private final Xs2aResultExtractor extractor;
    private final ProcessEventHandlerRegistrar registrar;

    @PostMapping(value = MORE_PARAMETERS + "/{executionId}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Transactional
    public CompletableFuture<? extends ResponseEntity<?>> confirmedRedirectConsentAccounts(
            @PathVariable String executionId,
            @RequestBody @Valid @NotNull LinkedMultiValueMap<@NotBlank String, String> pathAndValueUpdates) {

        BaseContext ctx = (BaseContext) runtimeService.getVariable(executionId, CONTEXT);
        // TODO It works only for String
        ctx = (BaseContext) updateCtxUsingJsonPath(ctx, pathAndValueUpdates);
        runtimeService.setVariable(executionId, CONTEXT, ctx);
        runtimeService.trigger(executionId);

        if (ProtocolAction.LIST_ACCOUNTS == ctx.getAction()) {
            return accounts(ctx.getSagaId());
        } else if (ProtocolAction.LIST_TRANSACTIONS == ctx.getAction()) {
            return transactions(ctx.getSagaId());
        }

        return CompletableFuture.completedFuture(ResponseEntity.notFound().build());
    }

    // TODO duplicated code
    @PostMapping(value = MORE_PARAMETERS_PSU_PASSWORD + "/{executionId}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Transactional
    public CompletableFuture<? extends ResponseEntity<?>> recievePsuPassword(
            @PathVariable String executionId,
            @Valid PsuPassword password) {

        Xs2aContext ctx = (Xs2aContext) runtimeService.getVariable(executionId, CONTEXT);
        ctx.setPsuPassword(password.getPsuPassword());
        runtimeService.setVariable(executionId, CONTEXT, ctx);
        runtimeService.trigger(executionId);

        if (ProtocolAction.LIST_ACCOUNTS == ctx.getAction()) {
            return accounts(ctx.getSagaId());
        } else if (ProtocolAction.LIST_TRANSACTIONS == ctx.getAction()) {
            return transactions(ctx.getSagaId());
        }

        return CompletableFuture.completedFuture(ResponseEntity.notFound().build());
    }

    // TODO duplicated code
    @PostMapping(value = MORE_PARAMETERS_SELECT_SCA_METHOD + "/{executionId}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Transactional
    public CompletableFuture<? extends ResponseEntity<?>> receiveScaMethodSelected(
            @PathVariable String executionId,
            @Valid ScaSelectedMethod methodSelected) {

        Xs2aContext ctx = (Xs2aContext) runtimeService.getVariable(executionId, CONTEXT);
        ctx.setUserSelectScaId(methodSelected.getScaMethodId());
        runtimeService.setVariable(executionId, CONTEXT, ctx);
        runtimeService.trigger(executionId);

        if (ProtocolAction.LIST_ACCOUNTS == ctx.getAction()) {
            return accounts(ctx.getSagaId());
        } else if (ProtocolAction.LIST_TRANSACTIONS == ctx.getAction()) {
            return transactions(ctx.getSagaId());
        }

        return CompletableFuture.completedFuture(ResponseEntity.notFound().build());
    }

    // TODO duplicated code
    @PostMapping(value = MORE_PARAMETERS_REPORT_SCA_RESULT + "/{executionId}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Transactional
    public CompletableFuture<? extends ResponseEntity<?>> receiveScaChallengeResult(
            @PathVariable String executionId,
            @Valid ScaChallengeResult scaChallengeResult) {

        Xs2aContext ctx = (Xs2aContext) runtimeService.getVariable(executionId, CONTEXT);
        ctx.setLastScaChallenge(scaChallengeResult.getScaChallengeResult());
        runtimeService.setVariable(executionId, CONTEXT, ctx);
        runtimeService.trigger(executionId);

        if (ProtocolAction.LIST_ACCOUNTS == ctx.getAction()) {
            return accounts(ctx.getSagaId());
        } else if (ProtocolAction.LIST_TRANSACTIONS == ctx.getAction()) {
            return transactions(ctx.getSagaId());
        }

        return CompletableFuture.completedFuture(ResponseEntity.notFound().build());
    }

    // TODO: Duplicated code
    private CompletableFuture<ResponseEntity<List<AccountDetails>>> accounts(String sagaId) {

        CompletableFuture<ResponseEntity<List<AccountDetails>>> result = new CompletableFuture<>();
        registrar.addHandler(
                sagaId,
                response -> result.complete(ResponseEntity.ok(extractor.extractAccountList(response))),
                result
        );
        return result;
    }

    private CompletableFuture<ResponseEntity<TransactionsReport>> transactions(String sagaId) {

        CompletableFuture<ResponseEntity<TransactionsReport>> result = new CompletableFuture<>();
        registrar.addHandler(
                sagaId,
                response -> result.complete(ResponseEntity.ok(extractor.extractTransactionsReport(response))),
                result
        );
        return result;
    }

    @SneakyThrows
    private Object updateCtxUsingJsonPath(Object original, MultiValueMap<String, String> pathAndValueUpdates) {
        Configuration jsonConfig = Configuration.builder()
            .jsonProvider(new JacksonJsonNodeJsonProvider())
            .options(DEFAULT_PATH_LEAF_TO_NULL, SUPPRESS_EXCEPTIONS)
            .build();

        TreeNode tree = mapper.valueToTree(original);
        DocumentContext docCtx = JsonPath.parse(tree, jsonConfig);
        pathAndValueUpdates.forEach((key, values) -> docCtx.set("$." + key, Iterables.getFirst(values, null)));

        return mapper.treeToValue(tree, original.getClass());
    }
}