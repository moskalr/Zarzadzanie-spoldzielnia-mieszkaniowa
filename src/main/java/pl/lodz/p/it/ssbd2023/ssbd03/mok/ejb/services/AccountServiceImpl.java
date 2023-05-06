package pl.lodz.p.it.ssbd2023.ssbd03.mok.ejb.services;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionSynchronization;
import jakarta.ejb.Stateful;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptors;
import jakarta.persistence.NoResultException;
import jakarta.security.enterprise.SecurityContext;
import jakarta.security.enterprise.identitystore.IdentityStoreHandler;
import jakarta.security.enterprise.credential.Password;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.ws.rs.ForbiddenException;
import pl.lodz.p.it.ssbd2023.ssbd03.auth.ConfirmationTokenGenerator;
import pl.lodz.p.it.ssbd2023.ssbd03.auth.JwtGenerator;
import pl.lodz.p.it.ssbd2023.ssbd03.common.AbstractService;
import pl.lodz.p.it.ssbd2023.ssbd03.config.Roles;
import pl.lodz.p.it.ssbd2023.ssbd03.dto.request.ChangePhoneNumberDTO;
import pl.lodz.p.it.ssbd2023.ssbd03.entities.*;
import pl.lodz.p.it.ssbd2023.ssbd03.entities.Account;
import pl.lodz.p.it.ssbd2023.ssbd03.entities.Owner;
import pl.lodz.p.it.ssbd2023.ssbd03.entities.PersonalData;
import pl.lodz.p.it.ssbd2023.ssbd03.exceptions.AppException;
import pl.lodz.p.it.ssbd2023.ssbd03.exceptions.account.AccountPasswordException;
import pl.lodz.p.it.ssbd2023.ssbd03.mok.ejb.facade.*;
import pl.lodz.p.it.ssbd2023.ssbd03.interceptors.TrackerInterceptor;
import pl.lodz.p.it.ssbd2023.ssbd03.mok.ejb.facade.AccountConfirmationTokenFacade;
import pl.lodz.p.it.ssbd2023.ssbd03.mok.ejb.facade.AccountFacade;
import pl.lodz.p.it.ssbd2023.ssbd03.mok.ejb.facade.OwnerFacade;
import pl.lodz.p.it.ssbd2023.ssbd03.mok.ejb.facade.PersonalDataFacade;
import pl.lodz.p.it.ssbd2023.ssbd03.mok.mail.MailSender;
import pl.lodz.p.it.ssbd2023.ssbd03.util.BcryptHashGenerator;

import java.time.LocalDateTime;
import java.util.Set;

@Stateful
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
@Interceptors({TrackerInterceptor.class})
public class AccountServiceImpl extends AbstractService implements AccountService, SessionSynchronization {

    @Inject
    private AccountFacade accountFacade;

    @Inject
    private PersonalDataFacade personalDataFacade;

    @Inject
    private OwnerFacade ownerFacade;

    @Inject
    private ManagerFacade managerFacade;

    @Inject
    private AdminFacade adminFacade;

    @Inject
    private AccountConfirmationTokenFacade accountConfirmationTokenFacade;

    @Inject
    private MailSender mailSender;

    @Inject
    private ConfirmationTokenGenerator confirmationTokenGenerator;

    @Inject
    private JwtGenerator jwtGenerator;

    @Inject
    private IdentityStoreHandler identityStoreHandler;

    @Inject
    private SecurityContext securityContext;

    @Inject
    private BcryptHashGenerator bcryptHashGenerator;

    @Override
    @RolesAllowed(Roles.GUEST)
    public void createOwner(Account account) {
        account.setPassword(bcryptHashGenerator.generate(account.getPassword().toCharArray()));
        account.setRegisterDate(LocalDateTime.now());

        accountFacade.create(account);

        AccountConfirmationToken accountConfirmationToken = new AccountConfirmationToken(
                confirmationTokenGenerator.createAccountConfirmationToken(), account);
        accountConfirmationTokenFacade.create(accountConfirmationToken);

        mailSender.sendLinkToActivateAccountToEmail(account.getEmail(), "Activate account", accountConfirmationToken.getTokenValue());
    }

    @Override
    @RolesAllowed(Roles.GUEST)
    public void confirmAccountFromActivationLink(String confirmationToken) {
        AccountConfirmationToken accountConfirmationToken = accountConfirmationTokenFacade.getActivationTokenByTokenValue(confirmationToken);

        Account accountToActivate = accountConfirmationToken.getAccount();
        accountToActivate.setIsActive(true);
        accountFacade.edit(accountToActivate);

        accountConfirmationTokenFacade.remove(accountConfirmationToken);
    }

    @Override
    @RolesAllowed(Roles.GUEST)
    public String authenticate(String username, String password) {
        final UsernamePasswordCredential usernamePasswordCredential = new UsernamePasswordCredential(username, new Password(password));
        final CredentialValidationResult credentialValidationResult = identityStoreHandler.validate(usernamePasswordCredential);
        if (credentialValidationResult.getStatus().equals(CredentialValidationResult.Status.VALID)) {
            final Set<String> roles = credentialValidationResult.getCallerGroups();
            return jwtGenerator.generateJWT(username, roles);
        }
        throw AppException.invalidCredentialsException();
    }

    @Override
    @RolesAllowed(Roles.OWNER)
    public void changePhoneNumber(ChangePhoneNumberDTO changePhoneNumberDTO) {
        final String username = securityContext.getCallerPrincipal().getName();
        final Account account = accountFacade.findByUsername(username);
        Owner owner = (Owner) account.getAccessLevels().stream()
                .filter(accessLevel -> accessLevel instanceof Owner)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Account is not an Owner."));
        final String newPhoneNumber = changePhoneNumberDTO.getPhoneNumber();
        if (!newPhoneNumber.equals(owner.getPhoneNumber())) {
            if (accountFacade.findByPhoneNumber(newPhoneNumber) == null) {
                owner.setPhoneNumber(newPhoneNumber);
                ownerFacade.edit(owner);
            } else {
                throw new IllegalArgumentException("Phone number is already taken.");
            }
        } else {
            throw new IllegalArgumentException("The given number is taken by your account.");
        }
    }

    @Override
    public PersonalData getPersonalData(Owner owner) {
        return personalDataFacade.find(owner.getId())
                .orElseThrow(() -> new IllegalArgumentException("Personal data not found"));
    }

    @Override
    public PersonalData getPersonalData(Manager manager) {
        return personalDataFacade.find(manager.getId())
                .orElseThrow(() -> new IllegalArgumentException("Personal data not found"));
    }

    @Override
    public PersonalData getPersonalData(Admin admin) {
        return personalDataFacade.find(admin.getId())
                .orElseThrow(() -> new IllegalArgumentException("Personal data not found"));
    }

    @Override
    public Owner getOwner(Long id) {
        return ownerFacade.find(id)
                .orElseThrow(() -> new IllegalArgumentException("Owner data not found."));
    }

    @Override
    public Manager getManager(Long id) {
        return managerFacade.find(id)
                .orElseThrow(() -> new IllegalArgumentException("Manager data not found."));
    }

    @Override
    public Admin getAdmin(Long id) {
        return adminFacade.find(id)
                .orElseThrow(() -> new IllegalArgumentException("Admin data not found."));
    }

    @Override
    public Owner getOwner() {
        final String username = securityContext.getCallerPrincipal().getName();
        final Account account = accountFacade.findByUsername(username);
        final Owner owner = (Owner) account.getAccessLevels().stream()
                .filter(accessLevel -> accessLevel instanceof Owner)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Account is not an Owner."));
        return owner;
    }

    @Override
    public Manager getManager() {
        final String username = securityContext.getCallerPrincipal().getName();
        final Account account = accountFacade.findByUsername(username);
        final Manager manager = (Manager) account.getAccessLevels().stream()
                .filter(accessLevel -> accessLevel instanceof Manager)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Account is not an Owner."));
        return manager;
    }

    @Override
    public Admin getAdmin() {
        final String username = securityContext.getCallerPrincipal().getName();
        final Account account = accountFacade.findByUsername(username);
        final Admin admin = (Admin) account.getAccessLevels().stream()
                .filter(accessLevel -> accessLevel instanceof Admin)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Account is not an Owner."));
        return admin;
    }

    @RolesAllowed({Roles.ADMIN, Roles.MANAGER, Roles.OWNER})
    @Override
    public void changePassword(String oldPassword, String newPassword, String newRepeatedPassword) throws AccountPasswordException {
        if (oldPassword.equals(newPassword)) {
            throw new AccountPasswordException("Old password and new password are the same.");
        }
        if (!newPassword.equals(newRepeatedPassword)) {
            throw new AccountPasswordException("New password and new repeated password are not the same.");
        }
        final String username = securityContext.getCallerPrincipal().getName();
        final Account account = accountFacade.findByUsername(username);
        if (!bcryptHashGenerator.generate(oldPassword.toCharArray()).equals(account.getPassword())) {
            throw new AccountPasswordException("Old password is incorrect");
        }
        final String newPasswordHash = bcryptHashGenerator.generate(newPassword.toCharArray());
        account.setPassword(newPasswordHash);
        accountFacade.edit(account);
    }
    @Override
    public void editSelfPersonalData(String firstName, String surname) throws NoResultException {
        try {
            final String username = securityContext.getCallerPrincipal().getName();
            editPersonalData(username, firstName, surname);
        } catch (NoResultException e) {
            throw new NoResultException(e.getMessage());
        }
    }

    @Override
    public void editUserPersonalData(String username, String firstName, String surname) throws ForbiddenException {
        final String editor = securityContext.getCallerPrincipal().getName();
        final Account editorAccount = accountFacade.findByUsername(editor);
        final Account editableAccount = accountFacade.findByUsername(username);

        if (editorAccount.getAccessLevels().stream().anyMatch(accessLevelMapping -> accessLevelMapping.getAccessLevel().equals("ADMIN"))) {
            editPersonalData(username, firstName, surname);
        } else if (editableAccount.getAccessLevels().stream().noneMatch(accessLevelMapping -> accessLevelMapping.getAccessLevel().equals("ADMIN"))) {
            editPersonalData(username, firstName, surname);
        } else {
            throw new ForbiddenException("Cannot edit other user personal data due to not supported role");
        }
    }

    private void editPersonalData(String username, String firstName, String surname) {
        try {
            PersonalData personalData = personalDataFacade.findByLogin(username);

            personalData.setFirstName(firstName);
            personalData.setSurname(surname);

            personalDataFacade.edit(personalData);
        } catch (NoResultException e) {
            throw new NoResultException(e.getMessage());
        }
    }

    @Override
    public void disableUserAccount(String username) throws NoResultException {
        editUserEnableFlag(username, false);
    }

    @Override
    public void enableUserAccount(String username) throws NoResultException {
        editUserEnableFlag(username, true);
    }

    private void editUserEnableFlag(String username, boolean flag) throws NoResultException {
        try {
            final String editor = securityContext.getCallerPrincipal().getName();
            final Account editorAccount = accountFacade.findByUsername(editor);
            final Account editableAccount = accountFacade.findByUsername(username);

            if (editorAccount.equals(editableAccount)) {
                throw new ForbiddenException("Cannot edit yours enable flag.");
            }

            if (editorAccount.getAccessLevels().stream().anyMatch(accessLevelMapping -> accessLevelMapping.getAccessLevel().equals("ADMIN"))) {
                setUserEnableFlag(username, flag);
            } else if (editableAccount.getAccessLevels().stream().noneMatch(accessLevelMapping -> accessLevelMapping.getAccessLevel().equals("ADMIN"))) {
                setUserEnableFlag(username, flag);
            } else {
                throw new ForbiddenException("Cannot edit other user enable flag due to not supported role.");
            }
        } catch (NoResultException e) {
            throw new NoResultException(e.getMessage());
        }
    }

    private void setUserEnableFlag(String username, boolean flag) throws NoResultException {
        try {
            final Account editableAccount = accountFacade.findByUsername(username);
            editableAccount.setIsEnable(flag);
            accountFacade.edit(editableAccount);
        } catch (NoResultException e) {
            throw new NoResultException(e.getMessage());
        }
    }
}
