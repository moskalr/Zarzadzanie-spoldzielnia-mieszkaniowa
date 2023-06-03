package pl.lodz.p.it.ssbd2023.ssbd03.mow.facade;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import pl.lodz.p.it.ssbd2023.ssbd03.common.AbstractFacade;
import pl.lodz.p.it.ssbd2023.ssbd03.config.Roles;
import pl.lodz.p.it.ssbd2023.ssbd03.entities.HeatDistributionCentre;
import pl.lodz.p.it.ssbd2023.ssbd03.entities.HeatDistributionCentrePayoff;

@Stateless
@TransactionAttribute(TransactionAttributeType.MANDATORY)
public class HeatDistributionCentrePayoffFacade extends AbstractFacade<HeatDistributionCentrePayoff> {
    @PersistenceContext(unitName = "ssbd03mowPU")
    private EntityManager em;

    public HeatDistributionCentrePayoffFacade() {
        super(HeatDistributionCentrePayoff.class);
    }

    @Override
    protected EntityManager getEntityManager() {
        return em;
    }

    @Override
    @RolesAllowed({Roles.MANAGER})
    public void edit(HeatDistributionCentrePayoff entity) {
        super.edit(entity);
    }

    @Override
    @RolesAllowed({Roles.MANAGER})
    public void create(HeatDistributionCentrePayoff entity) {
        super.create(entity);
    }

    @Override
    @RolesAllowed(Roles.MANAGER)
    public HeatDistributionCentrePayoff find(Object id) {
        return super.find(id);
    }

    @RolesAllowed({Roles.MANAGER})
    public HeatDistributionCentrePayoff getLast() {
        TypedQuery<HeatDistributionCentrePayoff> tq = em.createNamedQuery("HeatDistributionCentrePayoff.getLastHeatDistributionCentrePayoff", HeatDistributionCentrePayoff.class);
        return tq.getSingleResult();
    }
}
