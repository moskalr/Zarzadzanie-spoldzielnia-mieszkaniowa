package pl.lodz.p.it.ssbd2023.ssbd03.mok.ejb.facade;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.interceptor.Interceptors;
import jakarta.persistence.*;
import pl.lodz.p.it.ssbd2023.ssbd03.common.AbstractFacade;
import pl.lodz.p.it.ssbd2023.ssbd03.config.Roles;
import pl.lodz.p.it.ssbd2023.ssbd03.entities.ResetPasswordToken;
import pl.lodz.p.it.ssbd2023.ssbd03.exceptions.AppException;
import pl.lodz.p.it.ssbd2023.ssbd03.interceptors.TrackerInterceptor;

import java.time.LocalDateTime;
import java.util.List;

import static pl.lodz.p.it.ssbd2023.ssbd03.config.ApplicationConfig.TIME_ZONE;

@Stateless
@TransactionAttribute(TransactionAttributeType.MANDATORY)
@Interceptors({TrackerInterceptor.class})
public class ResetPasswordTokenFacade extends AbstractFacade<ResetPasswordToken> {
    @PersistenceContext(unitName = "ssbd03mokPU")
    private EntityManager em;

    @Override
    protected EntityManager getEntityManager() {
        return this.em;
    }

    public ResetPasswordTokenFacade() {
        super(ResetPasswordToken.class);
    }

    @RolesAllowed({Roles.GUEST, Roles.ADMIN})
    public ResetPasswordToken getResetPasswordByTokenValue(String tokenValue) {
        TypedQuery<ResetPasswordToken> query = em.createNamedQuery("ResetPasswordToken.getResetPasswordTokenByTokenValue", ResetPasswordToken.class);
        query.setParameter("tokenValue", tokenValue);
        try {
            return query.getSingleResult();
        } catch (PersistenceException pe) {
            if (pe instanceof NoResultException) {
                throw AppException.createNoResultException(pe.getCause());
            }
            throw AppException.createDatabaseException();
        }
    }

    @RolesAllowed({Roles.GUEST, Roles.ADMIN})
    public boolean checkIfResetPasswordTokenExistsByTokenValue(String tokenValue) {
        TypedQuery<ResetPasswordToken> tq = em.createNamedQuery("ResetPasswordToken.getResetPasswordTokenByTokenValue", ResetPasswordToken.class);
        tq.setParameter("tokenValue", tokenValue);
        try {
            tq.getSingleResult();
            return true;
        } catch (NoResultException e) {
            return false;
        }
    }

    @PermitAll
    public List<ResetPasswordToken> getExpiredResetPasswordTokensList() {
        TypedQuery<ResetPasswordToken> query = em.createNamedQuery("ResetPasswordToken.getOlderResetPasswordToken", ResetPasswordToken.class);
        query.setParameter("currentTime", LocalDateTime.now(TIME_ZONE));
        return query.getResultList();
    }

    @Override
    @PermitAll
    public void remove(ResetPasswordToken entity) {
        super.remove(entity);
    }

    @Override
    @RolesAllowed({Roles.ADMIN, Roles.GUEST})
    public void create(ResetPasswordToken entity) {
        super.create(entity);
    }
}
