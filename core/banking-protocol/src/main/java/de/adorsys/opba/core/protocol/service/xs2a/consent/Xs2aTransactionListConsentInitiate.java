package de.adorsys.opba.core.protocol.service.xs2a.consent;

import com.google.common.collect.ImmutableList;
import de.adorsys.opba.core.protocol.service.xs2a.context.TransactionListXs2aContext;
import de.adorsys.xs2a.adapter.service.AccountInformationService;
import de.adorsys.xs2a.adapter.service.Response;
import de.adorsys.xs2a.adapter.service.model.AccountAccess;
import de.adorsys.xs2a.adapter.service.model.AccountReference;
import de.adorsys.xs2a.adapter.service.model.ConsentCreationResponse;
import de.adorsys.xs2a.adapter.service.model.Consents;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static de.adorsys.opba.core.protocol.constant.GlobalConst.CONTEXT;

@Service("xs2aTransactionListConsentInitiate")
@RequiredArgsConstructor
public class Xs2aTransactionListConsentInitiate implements JavaDelegate {

    private final AccountInformationService ais;

    @Override
    @Transactional
    public void execute(DelegateExecution delegateExecution) {
        TransactionListXs2aContext context = delegateExecution.getVariable(CONTEXT, TransactionListXs2aContext.class);

        Response<ConsentCreationResponse> consentInit = ais.createConsent(
                context.toHeaders(),
                consents(context)
        );

        context.setRedirectUriOk("http://localhost:8080/v1/consents/confirm/transactions/" + delegateExecution.getProcessInstanceId() + "/");
        context.setConsentId(consentInit.getBody().getConsentId());
        delegateExecution.setVariable(CONTEXT, context);
    }

    @SuppressWarnings("checkstyle:MagicNumber") // Hardcoded as it is POC, these should be read from context
    private Consents consents(TransactionListXs2aContext ctx) {
        Consents consents = new Consents();
        AccountAccess access = new AccountAccess();
        access.setAccounts(ImmutableList.of(reference(ctx)));
        access.setBalances(ImmutableList.of(reference(ctx)));
        access.setTransactions(ImmutableList.of(reference(ctx)));
        consents.setAccess(access);
        consents.setCombinedServiceIndicator(false);
        consents.setRecurringIndicator(true);
        consents.setFrequencyPerDay(10);
        consents.setValidUntil(LocalDate.of(2021, 10, 10));

        return consents;
    }

    private AccountReference reference(TransactionListXs2aContext ctx) {
        AccountReference account = new AccountReference();
        account.setIban(ctx.getIban());
        account.setCurrency(ctx.getCurrency());
        return account;
    }
}
