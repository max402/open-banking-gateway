package de.adorsys.opba.core.protocol.service.xs2a.consent.authorize.embedded;

import de.adorsys.opba.core.protocol.service.xs2a.context.Xs2aContext;
import de.adorsys.xs2a.adapter.service.AccountInformationService;
import de.adorsys.xs2a.adapter.service.model.PsuData;
import de.adorsys.xs2a.adapter.service.model.UpdatePsuAuthentication;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static de.adorsys.opba.core.protocol.constant.GlobalConst.CONTEXT;

@Service("xs2aDoScaChallenge")
@RequiredArgsConstructor
public class DoScaChallenge implements JavaDelegate {

    private final AccountInformationService ais;

    @Override
    @Transactional
    public void execute(DelegateExecution delegateExecution) {
        Xs2aContext context = delegateExecution.getVariable(CONTEXT, Xs2aContext.class);

        ais.updateConsentsPsuData(
                context.getConsentId(),
                context.getAuthorizationId(),
                context.toHeaders(),
                authentication()
        );
    }

    private UpdatePsuAuthentication authentication() {
        UpdatePsuAuthentication psuAuthentication = new UpdatePsuAuthentication();
        PsuData data = new PsuData();
        data.setPassword("12345");
        psuAuthentication.setPsuData(data);
        return psuAuthentication;
    }
}
